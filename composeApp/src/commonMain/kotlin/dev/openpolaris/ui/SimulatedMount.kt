package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.protocol.ResponseParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * In-process simulated mount for demo mode. Implements Connection but never touches
 * the network: writes are parsed as commands and answered with plausible frames
 * queued on the read side, so the real MountSession request/response path is exercised.
 */
class SimulatedMount(private val scope: CoroutineScope) {

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
    val session = dev.openpolaris.core.domain.MountSession({ conn })

    private inner class SimConnection : Connection {
        val inbox = ArrayDeque<ByteArray>()

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

        fun queue(frameText: String) = synchronized(inbox) { inbox += frameText.toByteArray(Charsets.US_ASCII) }

        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            // Poll briefly for a queued response so request() sees it within its timeout.
            val deadline = kotlin.time.TimeSource.Monotonic.markNow() + timeoutMs.milliseconds
            while (true) {
                synchronized(inbox) {
                    if (inbox.isNotEmpty()) {
                        val r = inbox.removeFirst()
                        r.copyInto(buffer)
                        return r.size
                    }
                }
                if (deadline.hasPassedNow()) return -1
                delay(5)
            }
        }

        override fun close() {}
    }
}
