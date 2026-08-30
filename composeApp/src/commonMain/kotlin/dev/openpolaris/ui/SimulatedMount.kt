package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.protocol.ResponseParser
import dev.openpolaris.core.sim.SimulatedProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * In-process simulated mount for demo mode. Implements Connection but never touches
 * the network: writes are parsed as commands and answered with plausible frames
 * queued on the read side, so the real MountSession request/response path is exercised.
 *
 * The protocol logic itself lives in [SimulatedProtocol] so it can be shared with the
 * standalone `tools/stub-server` TCP runner.
 */
class SimulatedMount(@Suppress("UNUSED_PARAMETER") scope: CoroutineScope) {

    val sim = SimulatedProtocol()

    var tracking: Boolean get() = sim.tracking; set(v) { sim.tracking = v }
    var halfSpeed: Boolean get() = sim.halfSpeed; set(v) { sim.halfSpeed = v }
    var ahrs: Boolean get() = sim.ahrs; set(v) { sim.ahrs = v }
    var yaw: Float get() = sim.yaw; set(v) { sim.yaw = v }
    var pitch: Float get() = sim.pitch; set(v) { sim.pitch = v }
    var isoIndex: Int get() = sim.isoIndex; set(v) { sim.isoIndex = v }
    var wbIndex: Int get() = sim.wbIndex; set(v) { sim.wbIndex = v }
    var fNumIndex: Int get() = sim.fNumIndex; set(v) { sim.fNumIndex = v }
    var evIndex: Int get() = sim.evIndex; set(v) { sim.evIndex = v }

    private val conn = SimConnection(sim)
    val session = dev.openpolaris.core.domain.MountSession({ conn })

    private class SimConnection(private val sim: SimulatedProtocol) : Connection {
        val inbox = ArrayDeque<ByteArray>()

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) { /* always ok */ }

        override suspend fun write(data: ByteArray) {
            val text = String(data, Charsets.US_ASCII)
            val f = ResponseParser().parseFrame(text.trimEnd('#')) ?: return
            for (response in sim.handle(f.code, f.fields)) {
                queueBytes(response)
            }
        }

        fun queueBytes(bytes: ByteArray) = synchronized(inbox) { inbox += bytes }

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
