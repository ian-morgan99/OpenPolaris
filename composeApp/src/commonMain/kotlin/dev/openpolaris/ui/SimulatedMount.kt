package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.protocol.Codes
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
    var shutterIndex = 1

    private var demoCaptureNumber = 0
    private val demoFiles = mutableListOf<String>()

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
            val subtype = text.split('&').getOrNull(2)?.toIntOrNull() ?: 2
            handle(f.code, f.fields, subtype)
        }

        private fun handle(code: Int, fields: Map<String, String>, subtype: Int) {
            when (code) {
                Codes.APP_PASSWORD_INFO -> queue("1&" + Codes.APP_PASSWORD_INFO + "&2&needed:0;#")
                Codes.APP_HELLO -> queue("1&" + Codes.APP_HELLO + "&2&app:openpolaris;ver:demo;ret:0;#")
                Codes.CAM_INFO -> queue(
                    "1&" + Codes.CAM_INFO + "&2&manufacturer:sony;model:A7;state:0;storage:128;photoFormat:1;#"
                )
                Codes.BenroCamera.GET_ISO_INFO -> queue(
                    "1&" + Codes.BenroCamera.GET_ISO_INFO + "&2&V:" + isoIndex + ";R:100,200,400,800,1600;RD:1;#"
                )
                Codes.BenroCamera.GET_WB_INFO -> queue(
                    "1&" + Codes.BenroCamera.GET_WB_INFO + "&2&V:" + wbIndex + ";R:Auto,Daylight,Cloudy,Shade;RD:1;#"
                )
                Codes.BenroCamera.GET_EV_INFO -> queue(
                    "1&" + Codes.BenroCamera.GET_EV_INFO + "&2&V:" + evIndex + ";R:-2,-1,0,1,2;RD:1;#"
                )
                Codes.BenroCamera.GET_SHUTTER_INFO -> queue(
                    "1&" + Codes.BenroCamera.GET_SHUTTER_INFO + "&2&V:" + shutterIndex + ";R:M,B;RD:1;#"
                )
                Codes.BenroCamera.GET_FNUM_INFO -> queue(
                    "1&" + Codes.BenroCamera.GET_FNUM_INFO + "&2&V:" + fNumIndex + ";R:2.8,4,5.6,8,11;RD:1;#"
                )
                Codes.FILE_LIST -> {
                    val requestedType = fields["type"]?.toIntOrNull() ?: 0
                    val requestedPage = fields["page"]?.toIntOrNull() ?: 0
                    val payload = buildString {
                        append("type:")
                        append(requestedType)
                        append(";page:")
                        append(requestedPage)
                        append(";count:")
                        append(demoFiles.size)
                        append(";")
                        demoFiles.forEachIndexed { index, name ->
                            append("id:")
                            append(index + 1)
                            append(";name:")
                            append(name)
                            append(";prot:0;")
                        }
                    }
                    queue("1&" + Codes.FILE_LIST + "&2&" + payload + "#")
                }
                Codes.FILE_SD_STATUS -> queue(
                    "1&" + Codes.FILE_SD_STATUS + "&2&state:0;total:128;free:96;#"
                )
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
                Codes.BenroCamera.SET_ISO -> {
                    isoIndex = fields["iso"]?.toIntOrNull() ?: isoIndex
                    queue("1&" + Codes.BenroCamera.SET_ISO + "&2&ret:0;#")
                }
                Codes.BenroCamera.SET_WB -> {
                    wbIndex = fields["wb"]?.toIntOrNull() ?: wbIndex
                    queue("1&" + Codes.BenroCamera.SET_WB + "&2&ret:0;#")
                }
                Codes.BenroCamera.SET_EV -> {
                    evIndex = fields["ev"]?.toIntOrNull() ?: evIndex
                    queue("1&" + Codes.BenroCamera.SET_EV + "&2&ret:0;#")
                }
                Codes.BenroCamera.SET_SHUTTER -> {
                    shutterIndex = fields["shutter"]?.toIntOrNull()?.coerceIn(0, 1) ?: shutterIndex
                    queue("1&" + Codes.BenroCamera.SET_SHUTTER + "&2&ret:0;#")
                }
                Codes.BenroCamera.SET_FNUM -> {
                    fNumIndex = fields["fNum"]?.toIntOrNull() ?: fNumIndex
                    queue("1&" + Codes.BenroCamera.SET_FNUM + "&2&ret:0;#")
                }
                Codes.BenroCamera.SET_VIDEO_RECORD_STATUS -> queue(
                    "1&" + Codes.BenroCamera.SET_VIDEO_RECORD_STATUS + "&2&state:" +
                        (fields["state"] ?: "0") + ";ret:0;#"
                )
                Codes.BenroCamera.SET_FOCUS -> queue(
                    "1&" + Codes.BenroCamera.SET_FOCUS + "&2&ret:0;#"
                )
                Codes.BenroCamera.SET_FOCUS_ADJ -> queue(
                    "1&" + Codes.BenroCamera.SET_FOCUS_ADJ + "&2&ret:0;#"
                )
                Codes.CAM_GET_EV -> if (fields.containsKey("state") || fields.containsKey("bulb") || fields.containsKey("c")) {
                    val state = fields["state"]?.toIntOrNull() ?: 1
                    val bulb = fields["bulb"]?.toIntOrNull() ?: 0
                    val c = fields["c"]?.toIntOrNull() ?: -1
                    queue("1&" + Codes.CAM_GET_EV + "&2&state:$state;bulb:$bulb;c:$c;#")
                    if (state == 1) {
                        val imageName = "IMG_" + (++demoCaptureNumber).toString().padStart(4, '0') + ".JPG"
                        demoFiles += imageName
                        queue(
                            "1&" + Codes.FILE_DOWNLOAD_DATA +
                                "&2&type:1;path:/demo/DCIM/$imageName;size:12345;#"
                        )
                        queue("1&" + Codes.CAM_GET_EV + "&2&state:0;bulb:0;c:$c;#")
                    }
                } else if (subtype == Codes.CAM_CAPTURE_SUBTYPE) {
                    queue("1&" + Codes.CAM_CAPTURE + "&2&state:1;bulb:0;c:-1;#")
                } else {
                    queue("1&" + Codes.CAM_GET_EV + "&2&ev:$evIndex;ret:0;#")
                }
                265 -> { evIndex = fields["ev"]?.toIntOrNull() ?: evIndex; queue("1&265&2&ret:0;#") }
                266 -> queue("1&266&2&state:${if (tracking) 1 else 0};bulb:0;c:0;#")
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
