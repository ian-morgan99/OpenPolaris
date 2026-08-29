package dev.openpolaris.probe

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Minimal stand-in for the Polaris control socket. Listens on 127.0.0.1:<port>
 * and replies to a hand-rolled subset of codes 258..549 with the documented
 * frame format: `1&<code>&<type>&<payload>#`. Anything outside the subset gets
 * a generic `1&<code>&2&-100#` ack so the probe's framing logic still gets
 * exercised.
 *
 * Reading frames: the real protocol is byte-framed (`#` terminator, no
 * required line break) so we scan for `#` rather than relying on the client
 * to send a newline. We still use a small `BufferedReader` for line-oriented
 * clients, but tolerate the absence of a `\n` and split on `#` ourselves.
 *
 * Auto-level trigger (code 549) emulation: when the controller starts an
 * auto-level cycle, the real mount starts streaming 538 push frames as the
 * head physically settles. To exercise the controller's settling predicate
 * end-to-end we mirror that here by emitting a `ramp → settled → done`
 * sequence of 538 frames spaced ~100ms apart over [timeToSettleMs]
 * milliseconds. The settled phase keeps the tilt within ±0.01° so the
 * controller's `SETTLE_WINDOW=10` predicate triggers cleanly.
 *
 * This is for offline self-test of the probe only. Real payloads for codes
 * 537..548 are not implemented here — those should run against the physical
 * mount.
 *
 * @param timeToSettleMs How long the simulated auto-level cycle should take
 *   to settle, in milliseconds. Total 538 push frames ≈
 *   `timeToSettleMs / 100`; the first third is the ramp (drifting tilt),
 *   the rest is the settled phase (tilt within ±0.01°).
 */
class FakeMount(
    private val port: Int,
    private val host: String = "127.0.0.1",
    private val timeToSettleMs: Long = 5000,
) {
    private val running = AtomicBoolean(false)
    private var server: ServerSocket? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        server = ServerSocket(port, 1, java.net.InetAddress.getByName(host))
        thread(isDaemon = true, name = "fake-mount-accept") { acceptLoop() }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { server?.close() }
    }

    fun isRunning(): Boolean = running.get()

    /**
     * The port the server is bound to. Returns the explicitly-requested port, or the
     * ephemeral port assigned by the OS when [port] was 0. Throws if [start] was not
     * called or failed to bind.
     */
    val localPort: Int
        get() = server?.localPort
            ?: error("FakeMount not started")

    private fun acceptLoop() {
        while (running.get()) {
            val client = runCatching { server?.accept() }.getOrNull() ?: break
            client.soTimeout = 3000
            thread(isDaemon = true, name = "fake-mount-session") {
                handle(client.getInputStream(), client.getOutputStream().bufferedWriter())
            }
        }
    }

    private fun handle(`in`: java.io.InputStream, out: java.io.BufferedWriter) {
        try {
            val buf = ByteArray(4096)
            val pending = StringBuilder()
            while (running.get()) {
                val n = try { `in`.read(buf) } catch (_: java.net.SocketTimeoutException) { -2 }
                if (n == null || n <= 0) {
                    if (n == -2) continue          // timeout, keep waiting
                    break                          // EOF / error → exit
                }
                pending.append(String(buf, 0, n, Charsets.US_ASCII))
                // Drain all complete frames (delimited by '#').
                while (true) {
                    val hash = pending.indexOf("#")
                    if (hash < 0) break
                    val frame = pending.substring(0, hash)
                    pending.delete(0, hash + 1)
                    val parsed = parseFrame(frame) ?: continue
                    val reply = buildReply(parsed)
                    out.write(reply)
                    out.newLine()
                    out.flush()
                    // Mirror the real mount: an auto-level trigger (549) starts a
                    // sustained push of 538 frames as the gimbal physically
                    // settles. Emit a ramp → settled sequence so the controller's
                    // settling predicate (SETTLE_WINDOW=10 samples within
                    // ±0.01° of the running mean) can converge end-to-end.
                    if (parsed.code == 549) {
                        val total = (timeToSettleMs / 100L).toInt().coerceAtLeast(1)
                        val rampCutoff = total / 3
                        for (i in 0 until total) {
                            Thread.sleep(100)
                            val (pitch, roll) = if (i < rampCutoff) {
                                // Ramp: start with a large offset and let the
                                // controller observe a clearly-drifting signal.
                                val t = i.toDouble() / rampCutoff.coerceAtLeast(1)
                                val pitch = 0.5 - 0.5 * t
                                val roll = -0.3 + 0.3 * t
                                pitch to roll
                            } else {
                                // Settled: jitter within ±0.005° of zero.
                                val jitter = ((i * 37) % 7).toDouble() / 1000.0 - 0.003
                                val pitch = jitter
                                val roll = -jitter
                                pitch to roll
                            }
                            out.write("1&538&2&pitch:${"%.3f".format(pitch)};roll:${"%.3f".format(roll)};#")
                            out.newLine()
                            out.flush()
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Client disconnected or transport error — exit session thread.
        }
    }

    private data class Frame(val code: Int, val type: Int, val payload: String)

    private fun parseFrame(line: String): Frame? {
        // The protocol terminates with '#'. Be tolerant.
        val trimmed = line.trim().trimEnd('#')
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split('&')
        if (parts.size < 4 || parts[0] != "1") return null
        val code = parts[1].toIntOrNull() ?: return null
        val type = parts[2].toIntOrNull() ?: 2
        val payload = parts.drop(3).joinToString("&")
        return Frame(code, type, payload)
    }

    private fun buildReply(f: Frame): String {
        val payload = when (f.code) {
            284 -> "stat:1;ver:0.0.0-fake;"
            517 -> "ra:0.0;dec:0.0;locked:0;ahrs:0;"
            518 -> "q0:1.0;q1:0.0;q2:0.0;q3:0.0;"
            520 -> "state:${f.payload.substringAfter("state:", "0").substringBefore(';').take(1)};"
            531 -> "speed:${f.payload.substringAfter("speed:", "0").substringBefore(';').take(1)};"
            513, 514 -> "dir:${if (f.code == 513) "X" else "Y"};lvl:1;"
            519, 530 -> "ack:1;"
            523 -> "ack:1;"
            535 -> "ack:1;"
            536 -> "state:${f.payload.substringAfter("state:", "0").substringBefore(';').take(1)};"
            537 -> "pitch:0.10;roll:-0.05;"
            538 -> "pitch:0.10;roll:-0.05;"
            539 -> "state:1;"
            540 -> "state:${f.payload.substringAfter("state:", "1").substringBefore(';').take(1)};"
            541 -> "limit:1;"
            542 -> "limit:${f.payload.substringAfter("limit:", "1").substringBefore(';').take(1)};"
            543 -> "time:2;"
            544 -> "time:${f.payload.substringAfter("time:", "2").substringBefore(';').take(3)};"
            547 -> "en:1;"
            548 -> "en:${f.payload.substringAfter("en:", "1").substringBefore(';').take(1)};"
            549 -> "ack:1;"
            258, 268, 269 -> "ack:1;"
            else -> "ack:1;"
        }
        return "1&${f.code}&2&$payload#"
    }
}

fun main(args: Array<String>) {
    val port = args.getOrNull(0)?.toIntOrNull() ?: 9090
    val mount = FakeMount(port)
    mount.start()
    println("FakeMount listening on 127.0.0.1:$port (Ctrl+C to stop)")
    // Block main thread.
    Thread.currentThread().join()
}
