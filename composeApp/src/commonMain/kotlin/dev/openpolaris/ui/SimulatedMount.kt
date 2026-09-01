package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.protocol.ResponseParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * In-process simulated mount for demo mode. Implements Connection but never touches
 * the network: writes are parsed as commands and answered with plausible frames
 * queued on the read side, so the real MountSession request/response path is exercised.
 */
class SimulatedMount {
    /**
     * The simulated mount owns its own private reader scope. The reader
     * loop (long-lived, idle until a frame arrives) would otherwise pin
     * the calling scope — particularly the test scope under
     * `runTest(UnconfinedTestDispatcher())` — open and prevent
     * `advanceUntilIdle()` from returning, hanging the test. Keeping
     * the reader on a dedicated `SupervisorJob + Dispatchers.Unconfined`
     * scope means the test scope only sees the short-lived request
     * coroutines, not the persistent reader.
     */
    private val readerScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    var tracking = false
    var halfSpeed = false
    var ahrs = true
    var yaw = 0f
    var pitch = 0f

    // Simulated camera parameter indices (demo mode).
    var isoIndex = 5
    var wbIndex = 2
    var fNumIndex = 3
    var evIndex = 4

    private val conn = SimConnection()
    val session = dev.openpolaris.core.domain.MountSession(
        connectionFactory = { conn },
        readerScope = readerScope,
    )

    private inner class SimConnection : Connection {
        // 3b.5-BUG follow-up: use a Channel instead of a polling inbox so
        // read() suspends on a virtual-time-friendly primitive (the
        // previous TimeSource.Monotonic + delay(5) loop burned ~200ms of
        // real CPU per read under the test scheduler, which made every
        // connectDemo test take ~30s of wall-clock and could cause the
        // full test class to hang).
        val inbox = Channel<ByteArray>(capacity = Channel.UNLIMITED)

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) { /* always ok */ }

        override suspend fun write(data: ByteArray) {
            val text = String(data, Charsets.US_ASCII)
            val f = ResponseParser().parseFrame(text.trimEnd('#')) ?: return
            handle(f.code, f.fields)
        }

        private fun handle(code: Int, fields: Map<String, String>) {
            when (code) {
                284 -> queue(
                    "1&284&2&mode:${if (tracking) 2 else 0};battery:76;charge:0;" +
                        "track:${if (tracking) 1 else 0};halfSpeed:${if (halfSpeed) 0 else 1};" +
                        "ahrs:${if (ahrs) 1 else 0};#"
                )
                517 -> {
                    if (tracking) yaw = (yaw + 0.05f) % 360f
                    queue("1&517&2&yaw:$yaw;pitch:$pitch;roll:0.0;#")
                }
                531 -> tracking = fields["state"] == "1"
                536 -> halfSpeed = fields["halfSpeed"] == "0" // inverted quirk
                520 -> ahrs = fields["state"] == "1"
                519 -> {
                    yaw = fields["az"]?.toFloatOrNull() ?: yaw
                    pitch = fields["alt"]?.toFloatOrNull() ?: pitch
                    queue("1&519&2&result:ok;#")
                }
                258 -> queue("1&258&2&iso:$isoIndex;ret:0;#")
                259 -> { isoIndex = fields["iso"]?.toIntOrNull() ?: isoIndex; queue("1&259&2&ret:0;#") }
                260 -> queue("1&260&2&wb:$wbIndex;ret:0;#")
                261 -> { wbIndex = fields["wb"]?.toIntOrNull() ?: wbIndex; queue("1&261&2&ret:0;#") }
                262 -> queue("1&262&2&fNum:$fNumIndex;ret:0;#")
                263 -> { fNumIndex = fields["fNum"]?.toIntOrNull() ?: fNumIndex; queue("1&263&2&ret:0;#") }
                264 -> queue("1&264&2&ev:$evIndex;ret:0;#")
                265 -> { evIndex = fields["ev"]?.toIntOrNull() ?: evIndex; queue("1&265&2&ret:0;#") }
                266 -> queue("1&266&2&state:${if (tracking) 1 else 0};bulb:0;c:0;#")
                267 -> queue("1&267&2&state:1;bulb:0;c:1;#")
            }
        }

        fun queue(frameText: String) {
            inbox.trySend(frameText.toByteArray(Charsets.US_ASCII))
        }

        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val bytes = withTimeoutOrNull(timeoutMs.toLong()) { inbox.receive() } ?: return -1
            bytes.copyInto(buffer)
            return bytes.size
        }

        override fun close() {
            inbox.close()
        }
    }

    /**
     * Tear down the private reader scope. Call this when the simulated
     * mount is no longer needed (typically in test teardown) so the
     * long-lived reader coroutine is cancelled. Without it, the
     * SupervisorJob would keep the dispatcher's thread alive past
     * the test JVM's lifetime.
     */
    fun shutdown() {
        readerScope.cancel()
    }
}
