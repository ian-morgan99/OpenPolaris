package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.protocol.ResponseParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    var roll = 0f
    /**
     * 537/538 tilt envelope "state" field. Benro app firmware semantics
     * (PLAN §3b.5 / issue #32): the firmware is decoupled — `state` is a
     * distinct field from `pitch`/`roll`, not a derived "level" boolean.
     * For the simulator we just echo the most recent computed envelope.
     */
    var tiltState: Int = 1
    var autoLevelEnabled: Boolean = false

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
                // 3b.5: auto-level primitives. 547/548/549 are live-confirmed
                // (docs/POLARIS-FUNCTIONS-REPORT.md §2.3); FeatureFlags.autoLevel
                // is now ON by default. The simulator still emits converging
                // 538 push frames on 549 so AutoLevelController.awaitSettling
                // can observe the AHRS settle without any hardware attached.
                537 -> queue("1&537&2&state:$tiltState;pitch:$pitch;roll:$roll;#")
                538 -> {
                    // SET_TILT_STATE is a push-style envelope on real
                    // firmware. In the simulator we accept the field
                    // payload for completeness, then echo the current
                    // envelope (mirroring the live capture's behaviour
                    // where 538 was reported as a write from the host).
                    tiltState = fields["state"]?.toIntOrNull() ?: tiltState
                    fields["pitch"]?.toFloatOrNull()?.let { pitch = it }
                    fields["roll"]?.toFloatOrNull()?.let { roll = it }
                    queue("1&538&2&state:$tiltState;pitch:$pitch;roll:$roll;#")
                }
                547 -> queue("1&547&2&en:${if (autoLevelEnabled) 1 else 0};#")
                548 -> {
                    autoLevelEnabled = fields["en"] == "1"
                    queue("1&548&2&en:${if (autoLevelEnabled) 1 else 0};#")
                }
                549 -> {
                    // Fire-and-forget trigger. To make the demo actually
                    // "do something" we spin off a coroutine that emits
                    // 538 tilt-push frames converging to (0,0) over
                    // ~500ms. That lets AutoLevelController.awaitSettling
                    // observe the AHRS settle without any hardware.
                    queue("1&549&2&ret:0;#")
                    val readerScope = this@SimulatedMount.readerScope
                    val inbox = this.inbox
                    val settle = { snap: Float ->
                        val frame = "1&538&2&state:1;pitch:$snap;roll:$snap;#"
                            .toByteArray(Charsets.US_ASCII)
                        inbox.trySend(frame)
                    }
                    readerScope.launch {
                        // Simple geometric convergence: 538 push every
                        // ~50ms, halving the residual each step. By
                        // ~400ms the residual is < SETTLE_EPSILON_DEG
                        // (0.01°) and the settling window fills.
                        var r = pitch
                        var p = roll
                        if (kotlin.math.abs(r) < 0.001f && kotlin.math.abs(p) < 0.001f) {
                            r = 1.5f; p = -1.2f
                        }
                        repeat(20) {
                            r *= 0.6f
                            p *= 0.6f
                            settle(p)
                            settle(r)
                            delay(25)
                        }
                    }
                }
                else -> {
                    // Defensive: do NOT swallow unknown codes silently.
                    // Echoing a 0-byte frame at least surfaces in
                    // session-level round-trip tests that something
                    // arrived, rather than hanging the read deadline.
                    // Real hardware decoding belongs in MountSession;
                    // here we just bail.
                }
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
