package dev.openpolaris.probe

import dev.openpolaris.core.protocol.CommandTable
import dev.openpolaris.stub.runServer
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BurstTest {

    // ---- parseBurstArgs ----

    @Test
    fun `parseBurstArgs defaults host port and codes`() {
        val a = parseBurstArgs(emptyArray())
        assertEquals("192.168.0.1", a.host)
        assertEquals(9090, a.port)
        assertEquals(listOf(524, 544, 802, 824, 775, 778, 779), a.codes)
    }

    @Test
    fun `parseBurstArgs parses explicit host and port`() {
        val a = parseBurstArgs(arrayOf("10.0.0.5", "1234"))
        assertEquals("10.0.0.5", a.host)
        assertEquals(1234, a.port)
        // codes still default
        assertEquals(listOf(524, 544, 802, 824, 775, 778, 779), a.codes)
    }

    @Test
    fun `parseBurstArgs parses a custom code list with whitespace`() {
        val a = parseBurstArgs(arrayOf("h", "1", "524, 802 ,825"))
        assertEquals(listOf(524, 802, 825), a.codes)
    }

    @Test
    fun `parseBurstArgs treats a non-integer port as default 9090`() {
        val a = parseBurstArgs(arrayOf("h", "notanumber"))
        assertEquals(9090, a.port)
    }

    @Test
    fun `parseBurstArgs --full uses the canonical pre-camera burst codes`() {
        val a = parseBurstArgs(arrayOf("h", "1", "--full"))
        assertTrue(a.full, "expected full=true when --full is passed")
        // Source of truth: CommandTable.BURST_PRE_CAMERA_CODES. Keeping the
        // test's expectation here pinned to the constant guards against drift
        // between the burst path and the canonical pre-camera set documented
        // in docs/PROTOCOL-CODE-AUDIT-2026-08-31.md §"Live-captured codes".
        assertEquals(
            CommandTable.BURST_PRE_CAMERA_CODES,
            a.codes,
        )
    }

    @Test
    fun `BURST_PRE_CAMERA_CODES matches the audit doc's live-captured pre-camera set`() {
        // Audit-doc canonical set (verified 2026-08-30 on real gimbal 192.168.0.1):
        // 11 codes — 808, 809, 802, 778, 779, 775, 824, 524, 543, 780, 525.
        // If you reorder or extend BURST_PRE_CAMERA in CommandTable.kt, this
        // assertion forces you to also update the audit doc.
        val expected = listOf(808, 809, 802, 778, 779, 775, 824, 524, 543, 780, 525)
        assertEquals(
            expected,
            CommandTable.BURST_PRE_CAMERA_CODES,
            "CommandTable.BURST_PRE_CAMERA_CODES drifted from the audit doc's " +
                "canonical live-captured pre-camera set — update " +
                "docs/PROTOCOL-CODE-AUDIT-2026-08-31.md §Live-captured codes " +
                "to match (or vice versa).",
        )
    }

    // ---- runBurst: error path ----

    @Test
    fun `runBurst emits an error line when no server is listening`() {
        // Find a definitely-free port by binding then closing.
        val ss = ServerSocket(0); val freePort = ss.localPort; ss.close()
        val lines = runBurst(BurstArgs("127.0.0.1", freePort, codes = listOf(524)), sink = {})
        assertTrue(
            lines.any { it.startsWith("  <error:") },
            "expected an <error:...> line, got:\n${lines.joinToString("\n")}"
        )
        // The header line should still be emitted.
        assertTrue(
            lines.first().startsWith("burst → 127.0.0.1:$freePort codes="),
            "expected header line, got: ${lines.first()}"
        )
    }

    @Test
    fun `drain window ends while peer continuously sends async data`() {
        ServerSocket(0).use { server ->
            val writer = thread(isDaemon = true) {
                runCatching {
                    server.accept().use { peer ->
                        val out = peer.getOutputStream()
                        while (true) {
                            out.write("1&284&2&mode:1;state:0;#".toByteArray())
                            out.flush()
                            Thread.sleep(10)
                        }
                    }
                }
            }
            Socket("127.0.0.1", server.localPort).use { client ->
                val started = System.nanoTime()
                val bytes = drainForWindow(client, client.getInputStream(), 200)
                val elapsedMs = (System.nanoTime() - started) / 1_000_000L
                assertTrue(bytes.isNotEmpty(), "continuous peer should produce data")
                assertTrue(elapsedMs in 150..700, "absolute 200 ms window took ${elapsedMs} ms")
            }
            writer.join(1000)
        }
    }

    @Test
    fun `runBurst times out a silent code and continues on one connection`() {
        ServerSocket(0).use { server ->
            val peer = thread(isDaemon = true) {
                runCatching {
                    server.accept().use { socket ->
                        val input = socket.getInputStream()
                        val buf = ByteArray(256)
                        // Consume both requests but deliberately never reply.
                        while (input.read(buf) >= 0) Unit
                    }
                }
            }
            val started = System.nanoTime()
            val lines = runBurst(
                BurstArgs("127.0.0.1", server.localPort, codes = listOf(524, 802)),
                sink = {},
            )
            val elapsedMs = (System.nanoTime() - started) / 1_000_000L
            assertEquals(2, lines.count { it.contains("<no response>") }, lines.joinToString("\n"))
            assertTrue(elapsedMs in 2_500..4_500, "two bounded silent reads took ${elapsedMs} ms")
            peer.join(1000)
        }
    }

    @Test
    fun `runBurst receives delayed replies over one persistent connection`() {
        ServerSocket(0).use { server ->
            val accepts = java.util.concurrent.atomic.AtomicInteger()
            val peer = thread(isDaemon = true) {
                runCatching {
                    server.accept().use { socket ->
                        accepts.incrementAndGet()
                        val input = socket.getInputStream()
                        val output = socket.getOutputStream()
                        val request = StringBuilder()
                        while (true) {
                            val b = input.read()
                            if (b < 0) break
                            request.append(b.toChar())
                            if (b.toChar() == '#') {
                                val code = request.toString().split('&').getOrNull(1)?.toIntOrNull()
                                Thread.sleep(75)
                                val frame = when (code) {
                                    524 -> "1&524&2&state:1;#"
                                    802 -> "1&802&2&band:5;#"
                                    else -> "1&$code&2&ret:0;#"
                                }
                                output.write(frame.toByteArray())
                                output.flush()
                                request.clear()
                            }
                        }
                    }
                }
            }
            val lines = runBurst(
                BurstArgs("127.0.0.1", server.localPort, codes = listOf(524, 802)),
                sink = {},
            )
            assertEquals(1, accepts.get(), "burst must reuse one persistent socket")
            assertTrue(lines.any { it.startsWith("  code=524") && it.contains("state=1") })
            assertTrue(lines.any { it.startsWith("  code=802") && it.contains("band=5") })
            peer.join(1000)
        }
    }

    // ---- runBurst: integration with stub-server ----

    @Test
    fun `runBurst against the stub receives framed responses for query codes`() {
        val port = startStubServer()
        val lines = runBurst(
            BurstArgs("127.0.0.1", port, codes = listOf(524, 802, 824)),
            sink = {},
        )
        // Each code should produce: sent, drained (with a frame), parsed
        val parsed = lines.filter { it.startsWith("  code=") }
        assertEquals(3, parsed.size, "expected 3 parsed lines, got:\n${lines.joinToString("\n")}")
        assertTrue(parsed.any { it.contains("code=524") && it.contains("state=") }, "missing 524: $parsed")
        assertTrue(parsed.any { it.contains("code=802") && it.contains("band=") }, "missing 802: $parsed")
        assertTrue(parsed.any { it.contains("code=824") && it.contains("running=") }, "missing 824: $parsed")
    }

    @Test
    fun `runBurst against the stub handles the 2026-08-30 live-captured codes 780 and 525`() {
        // 780 (device info) and 525 (temperature) were not in the original burst.
        // They were added after the live capture at gimbal 192.168.0.1 on
        // 2026-08-30 confirmed both endpoints reply on the real device. The
        // simulator was extended to mimic them; this test guards the burst path
        // (frame → parser → drain) for both.
        val port = startStubServer()
        val lines = runBurst(
            BurstArgs("127.0.0.1", port, codes = listOf(780, 525)),
            sink = {},
        )
        val parsed = lines.filter { it.startsWith("  code=") }
        assertEquals(2, parsed.size, "expected 2 parsed lines, got:\n${lines.joinToString("\n")}")

        // 780 has key:value fields (hw, sw, sv, …).
        val p780 = parsed.firstOrNull { it.contains("code=780") }
        assertTrue(p780 != null, "missing code=780 in parsed: $parsed")
        assertTrue(p780!!.contains("hw="), "780 missing hw= field: $p780")
        assertTrue(p780.contains("sw="), "780 missing sw= field: $p780")
        assertTrue(p780.contains("sv="), "780 missing sv= field: $p780")

        // 525 has a single Tempa<hex> token (no `:`), so the parser produces
        // `code=525` with no trailing fields. The raw payload still shows up
        // on the `drained` line — assert on that to keep the test honest.
        val p525 = parsed.firstOrNull { it.contains("code=525") }
        assertTrue(p525 != null, "missing code=525 in parsed: $parsed")
        val drained = lines.filter { it.startsWith("  drained") }
        assertTrue(
            drained.any { it.contains("Tempa") },
            "expected Tempa<hex> in a drained line for 525, got:\n${drained.joinToString("\n")}"
        )
    }

    @Test
    fun `runBurst --full against the stub yields parsed lines for all 11 codes`() {
        // End-to-end check: --full exercises every entry in the canonical
        // BURST_PRE_CAMERA list, and the stub must respond to all 11.
        // Use parseBurstArgs so the full code set is expanded from the flag.
        val port = startStubServer()
        val args = parseBurstArgs(arrayOf("127.0.0.1", port.toString(), "--full"))
        val lines = runBurst(args, sink = {})
        val parsed = lines.filter { it.startsWith("  code=") }
        assertEquals(
            CommandTable.BURST_PRE_CAMERA_CODES.size, parsed.size,
            "expected ${CommandTable.BURST_PRE_CAMERA_CODES.size} parsed lines for " +
                "the full burst, got ${parsed.size}:\n${lines.joinToString("\n")}"
        )
        for (c in CommandTable.BURST_PRE_CAMERA_CODES) {
            assertTrue(
                parsed.any { it.contains("code=$c") },
                "missing code=$c in full burst, got:\n${parsed.joinToString("\n")}"
            )
        }
    }

    // -- helpers --

    /**
     * Start the stub-server on an ephemeral port, returning the bound port.
     * The listener thread is a daemon so it won't block JVM shutdown.
     */
    private fun startStubServer(): Int {
        val ss = ServerSocket(0); val port = ss.localPort; ss.close()
        val t = Thread({ runServer("127.0.0.1", port) }, "cli-probe-test-stub").apply {
            isDaemon = true
        }
        t.start()
        // Wait for the listener to come up — retry connect until success or timeout.
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", port), 100) }
                return port
            } catch (_: Exception) {
                Thread.sleep(20)
            }
        }
        error("stub server did not start on port $port within 3s")
    }
}
