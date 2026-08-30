package dev.openpolaris.core.sim

import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.EMPTY_CONTENT
import dev.openpolaris.core.protocol.ResponseParser

/**
 * Pure protocol-level simulator for the Polaris gimbal.
 *
 * The same logic backs two runners:
 *  - the in-process [dev.openpolaris.core.sim.SimulatedMount] (no networking)
 *  - the standalone `tools/stub-server` TCP server (the mobile app can talk to it
 *    over WiFi as a stand-in for the real mount, so the protocol path can be
 *    exercised without a physical gimbal)
 *
 * The simulator is intentionally side-effect free with respect to threading: a
 * single instance should be confined to one coroutine / thread per concurrent
 * caller. The TCP server runs each client on its own thread with its own
 * instance to keep state isolated between sessions.
 *
 * Code coverage is driven by [Codes]. Anything not explicitly handled returns
 * a generic `1&<code>&2&ret:0;#` ack so new commands round-trip cleanly
 * against the stub during development.
 */
class SimulatedProtocol {

    // ---- gimbal state --------------------------------------------------------
    var tracking = false
    var halfSpeed = false
    var ahrs = true
    var yaw = 0f
    var pitch = 0f
    var roll = 0f

    // ---- camera (indices into the firmware-sorted option lists) ---------------
    var isoIndex = 5
    var wbIndex = 2
    var fNumIndex = 3
    var evIndex = 4
    var focusIndex = 0
    var imgSizeIndex = 0
    var imgFmtIndex = 0
    var colorIndex = 0
    var shutterIndex = 0
    var captureModeIndex = 0
    var liveview = false

    // ---- misc knobs -----------------------------------------------------------
    var ditherState = 0
    var ditherEnabled = false
    var autoLevelEnabled = false
    var settlingTime = 5
    var buzzer = true
    var led = true
    var wifiBand = 0
    var omsRunning = false
    var omsTaskCount = 0
    /** OMS scheduler task table (id, state, name). The first record is the
     *  default seeded list — exactly what the simulator returns for code 825. */
    val omsTasks: MutableList<SimTask> = mutableListOf(
        SimTask(0, 0, "Slew"),
        SimTask(1, 1, "Pan"),
    )
    var gimbalRtcMillis: Long = 1_700_000_000_000L
    var fwVersion = "1.0.0"
    var serial = "POL-SIM-0001"
    var passwordRequired = false
    var token = "0"
    var pingCounter = 0

    // ---- file system ----------------------------------------------------------
    var fileCount = 12
    var sdStatus = 0   // 0 = present, 1 = formatting, 2 = error
    var fwUpgradeState = 0
    var fwUpgradeProgress = 0
    val files: MutableList<SimFile> = mutableListOf(
        SimFile(1, "DSC_0001.JPG", 0, 0),
        SimFile(2, "DSC_0002.JPG", 0, 0),
        SimFile(3, "DSC_0003.RAW", 0, 0),
    )

    /**
     * Process a single inbound frame and return zero or more response frames
     * (ASCII bytes already terminated with `#`).
     *
     * Returns an empty list for fire-and-forget commands that the firmware
     * does not acknowledge. Each returned byte array is one complete on-wire
     * response, including the trailing `#`, ready to write to the socket as-is.
     */
    fun handle(code: Int, fields: Map<String, String>): List<ByteArray> {
        val out = ArrayList<ByteArray>(1)
        when (code) {
            // ---- gimbal push / poll ----------------------------------------
            Codes.PUSH_MODE_STATE -> out += response(
                "1&${Codes.PUSH_MODE_STATE}&2&" +
                    "mode:${if (tracking) 2 else 0};battery:76;charge:0;" +
                    "track:${if (tracking) 1 else 0};halfSpeed:${if (halfSpeed) 0 else 1};" +
                    "ahrs:${if (ahrs) 1 else 0};#"
            )
            Codes.GET_GIMBAL_POS -> {
                if (tracking) yaw = ((yaw + 0.05f) % 360f + 360f) % 360f
                out += response(
                    "1&${Codes.GET_GIMBAL_POS}&2&yaw:$yaw;pitch:$pitch;roll:$roll;#"
                )
            }
            Codes.PUSH_ROTATE_VECTOR -> out += response(
                "1&${Codes.PUSH_ROTATE_VECTOR}&2&yaw:$yaw;pitch:$pitch;roll:$roll;#"
            )

            // ---- tracking / jog --------------------------------------------
            Codes.SET_TRACK_AU_STATE -> tracking = fields["state"] == "1"
            Codes.SET_TRACK_HALF_SPEED -> halfSpeed = fields["halfSpeed"] == "0" // inverted quirk
            Codes.SET_AHRS_STATE -> ahrs = fields["state"] == "1"

            // ---- slewing / goto --------------------------------------------
            Codes.SET_GOTO_AU_STATE -> {
                val state = fields["state"]
                if (state == "0") {
                    // cancel — no echo
                } else {
                    val az = fields["az"]?.toFloatOrNull()
                    val alt = fields["alt"]?.toFloatOrNull()
                    if (az != null) yaw = az
                    if (alt != null) pitch = alt
                    out += response("1&${Codes.SET_GOTO_AU_STATE}&2&result:ok;#")
                }
            }
            Codes.POS_RESET -> {
                yaw = 0f; pitch = 0f; roll = 0f
                out += response("1&${Codes.POS_RESET}&2&ret:0;#")
            }
            Codes.EX_AXIS_STA -> out += response(
                "1&${Codes.EX_AXIS_STA}&2&state:0;angle:0.0;#"
            )
            Codes.SET_SETTLING_TIME -> {
                settlingTime = fields["time"]?.toIntOrNull() ?: settlingTime
            }

            // ---- jog --------------------------------------------------------
            Codes.GIMBAL_HADJ_SPEED, Codes.GIMBAL_VADJ_SPEED,
            Codes.GIMBAL_HADJ_ANGLE, Codes.GIMBAL_VADJ_ANGLE,
            Codes.GIMBAL_RADJ_SPEED, Codes.GIMBAL_RADJ_ANGLE -> {
                val t = fields["time"]?.toIntOrNull() ?: 0
                when (code) {
                    Codes.GIMBAL_HADJ_SPEED, Codes.GIMBAL_HADJ_ANGLE ->
                        yaw = ((yaw + t * 0.01f) % 360f + 360f) % 360f
                    Codes.GIMBAL_VADJ_SPEED, Codes.GIMBAL_VADJ_ANGLE ->
                        pitch = (pitch + t * 0.01f)
                    Codes.GIMBAL_RADJ_SPEED, Codes.GIMBAL_RADJ_ANGLE ->
                        roll = ((roll + t * 0.01f) + 360f) % 360f
                }
            }

            // ---- calibration / star alignment -------------------------------
            Codes.CALIBRATE_START -> {
                val step = fields["step"]?.toIntOrNull() ?: 0
                when (step) {
                    1 -> out += response("1&${Codes.CALIBRATE_START}&2&result:ok;#")
                    2 -> out += response(
                        "1&${Codes.CALIBRATE_START}&2&yaw:${fields["yaw"]};" +
                            "pitch:${fields["pitch"]};err:0;#"
                    )
                    else -> out += response("1&${Codes.CALIBRATE_START}&2&ret:0;#")
                }
            }
            Codes.YAW_KEY, Codes.PITCH_KEY, Codes.ROLL_KEY -> {
                out += response("1&$code&2&ret:0;#")
            }
            Codes.SET_GIMBAL_POS -> {
                val az = fields["az"]?.toFloatOrNull() ?: yaw
                val alt = fields["alt"]?.toFloatOrNull() ?: pitch
                yaw = az; pitch = alt
                out += response("1&${Codes.SET_GIMBAL_POS}&2&ret:0;#")
            }
            Codes.GET_TILT_STATE, Codes.SET_TILT_STATE,
            Codes.GET_DITHER_STATE, Codes.SET_DITHER_STATE -> {
                if (code == Codes.GET_DITHER_STATE || code == Codes.SET_DITHER_STATE) {
                    ditherState = fields["state"]?.toIntOrNull() ?: ditherState
                }
                out += response("1&$code&2&state:$ditherState;#")
            }
            Codes.GET_LIMIT_STATE, Codes.SET_LIMIT_STATE -> {
                out += response("1&$code&2&ret:0;#")
            }
            Codes.GET_SETTLING_TIME -> out += response(
                "1&${Codes.GET_SETTLING_TIME}&2&time:$settlingTime;#"
            )
            Codes.GET_AUTO_LEVEL_EN, Codes.SET_AUTO_LEVEL_EN -> {
                autoLevelEnabled = fields["en"] == "1"
                out += response("1&$code&2&en:${if (autoLevelEnabled) 1 else 0};#")
            }
            Codes.SET_AUTO_LEVEL_STATE -> {
                out += response("1&${Codes.SET_AUTO_LEVEL_STATE}&2&ret:0;#")
            }

            // ---- camera parameters (UNVERIFIED) -----------------------------
            Codes.CAM_GET_ISO -> out += response("1&${Codes.CAM_GET_ISO}&2&iso:$isoIndex;ret:0;#")
            Codes.CAM_SET_ISO -> { isoIndex = fields["iso"]?.toIntOrNull() ?: isoIndex }
            Codes.CAM_GET_WB -> out += response("1&${Codes.CAM_GET_WB}&2&wb:$wbIndex;ret:0;#")
            Codes.CAM_SET_WB -> { wbIndex = fields["wb"]?.toIntOrNull() ?: wbIndex }
            Codes.CAM_GET_FNUM -> out += response("1&${Codes.CAM_GET_FNUM}&2&fNum:$fNumIndex;ret:0;#")
            Codes.CAM_SET_FNUM -> { fNumIndex = fields["fNum"]?.toIntOrNull() ?: fNumIndex }
            Codes.CAM_GET_EV -> out += response("1&${Codes.CAM_GET_EV}&2&ev:$evIndex;ret:0;#")
            Codes.CAM_SET_EV -> { evIndex = fields["ev"]?.toIntOrNull() ?: evIndex }
            Codes.CAM_GET_FOCUS -> out += response("1&${Codes.CAM_GET_FOCUS}&2&focus:$focusIndex;ret:0;#")
            Codes.CAM_SET_FOCUS -> { focusIndex = fields["focus"]?.toIntOrNull() ?: focusIndex }
            Codes.CAM_GET_IMG_SIZE -> out += response("1&${Codes.CAM_GET_IMG_SIZE}&2&imgSize:$imgSizeIndex;ret:0;#")
            Codes.CAM_SET_IMG_SIZE -> { imgSizeIndex = fields["imgSize"]?.toIntOrNull() ?: imgSizeIndex }
            Codes.CAM_GET_IMG_FMT -> out += response("1&${Codes.CAM_GET_IMG_FMT}&2&imgFmt:$imgFmtIndex;ret:0;#")
            Codes.CAM_SET_IMG_FMT -> { imgFmtIndex = fields["imgFmt"]?.toIntOrNull() ?: imgFmtIndex }
            Codes.CAM_GET_COLOR -> out += response("1&${Codes.CAM_GET_COLOR}&2&color:$colorIndex;ret:0;#")
            Codes.CAM_SET_COLOR -> { colorIndex = fields["color"]?.toIntOrNull() ?: colorIndex }
            Codes.CAM_GET_SHUTTER -> out += response("1&${Codes.CAM_GET_SHUTTER}&2&shutter:$shutterIndex;ret:0;#")
            Codes.CAM_SET_SHUTTER -> { shutterIndex = fields["shutter"]?.toIntOrNull() ?: shutterIndex }
            Codes.CAM_GET_CAPTURE_MODE -> out += response("1&${Codes.CAM_GET_CAPTURE_MODE}&2&captureMode:$captureModeIndex;ret:0;#")
            Codes.CAM_SET_CAPTURE_MODE -> { captureModeIndex = fields["captureMode"]?.toIntOrNull() ?: captureModeIndex }
            Codes.CAM_GET_STATE -> out += response(
                "1&${Codes.CAM_GET_STATE}&2&state:${if (tracking) 1 else 0};" +
                    "bulb:0;c:0;#"
            )
            Codes.CAM_CAPTURE -> out += response(
                "1&${Codes.CAM_CAPTURE}&2&state:1;bulb:0;c:1;#"
            )
            Codes.CAM_LIVEVIEW_START -> {
                liveview = true
                out += response("1&${Codes.CAM_LIVEVIEW_START}&2&ret:0;#")
            }

            // ---- file / SD management --------------------------------------
            Codes.FILE_LIST -> {
                val type = fields["type"]?.toIntOrNull() ?: 0
                val page = fields["page"]?.toIntOrNull() ?: 0
                val body = files.joinToString("") { f ->
                    "id:${f.id};name:${f.name};prot:${f.prot};"
                }
                out += response(
                    "1&${Codes.FILE_LIST}&2&type:$type;page:$page;count:$fileCount;${body}#"
                )
            }
            Codes.FILE_DELETE -> {
                val id = fields["id"]?.toIntOrNull()
                if (id != null) {
                    files.removeAll { it.id == id }
                    fileCount = files.size
                }
                out += response("1&${Codes.FILE_DELETE}&2&ret:0;#")
            }
            Codes.FILE_DOWNLOAD_START -> out += response(
                "1&${Codes.FILE_DOWNLOAD_START}&2&size:0;ret:0;#"
            )
            Codes.FILE_DOWNLOAD_DATA, Codes.FILE_DOWNLOAD_END -> Unit
            Codes.FILE_SD_STATUS -> out += response(
                "1&${Codes.FILE_SD_STATUS}&2&state:$sdStatus;total:30000;free:24000;#"
            )
            Codes.FILE_SD_FORMAT -> {
                sdStatus = 1
                out += response("1&${Codes.FILE_SD_FORMAT}&2&ret:0;#")
            }
            Codes.FILE_SET_TYPE -> out += response("1&${Codes.FILE_SET_TYPE}&2&ret:0;#")
            Codes.FILE_RENAME -> {
                val id = fields["id"]?.toIntOrNull()
                val name = fields["name"]
                if (id != null && name != null) {
                    files.firstOrNull { it.id == id }?.let { it.name = name }
                }
                out += response("1&${Codes.FILE_RENAME}&2&ret:0;#")
            }
            Codes.FILE_THUMB -> out += response("1&${Codes.FILE_THUMB}&2&ret:0;#")
            Codes.FILE_THUMB_LIST -> out += response("1&${Codes.FILE_THUMB_LIST}&2&ret:0;#")
            Codes.FILE_INFO -> out += response(
                "1&${Codes.FILE_INFO}&2&id:${fields["id"]};name:n;size:0;#"
            )
            Codes.FILE_UPLOAD_FW -> out += response("1&${Codes.FILE_UPLOAD_FW}&2&ret:0;#")
            Codes.FILE_BACKUP, Codes.FILE_RESTORE -> out += response("1&$code&2&ret:0;#")
            Codes.FILE_CAM_LIST -> out += response("1&${Codes.FILE_CAM_LIST}&2&ret:0;#")
            Codes.FILE_LIST_BY_DATE -> out += response("1&${Codes.FILE_LIST_BY_DATE}&2&ret:0;#")
            Codes.FILE_DELETE_ALL -> {
                files.clear(); fileCount = 0
                out += response("1&${Codes.FILE_DELETE_ALL}&2&ret:0;#")
            }
            Codes.FILE_PROTECT, Codes.FILE_UNPROTECT -> {
                val id = fields["id"]?.toIntOrNull()
                val prot = fields["prot"]?.toIntOrNull() ?: 1
                if (id != null) {
                    files.firstOrNull { it.id == id }?.let { it.prot = prot }
                }
                out += response("1&$code&2&ret:0;#")
            }
            Codes.FILE_QUOTA -> out += response(
                "1&${Codes.FILE_QUOTA}&2&total:30000;free:24000;used:6000;#"
            )
            Codes.FILE_PAGINATE -> out += response("1&${Codes.FILE_PAGINATE}&2&ret:0;#")
            Codes.FILE_UPLOAD_CHUNK, Codes.FILE_UPLOAD_END -> Unit
            Codes.FILE_CAM_RAW -> out += response("1&${Codes.FILE_CAM_RAW}&2&ret:0;#")
            Codes.FILE_SCAN_COMPLETE -> out += response("1&${Codes.FILE_SCAN_COMPLETE}&2&ret:0;#")

            // ---- battery ----------------------------------------------------
            Codes.BATTERY_STATUS -> out += response(
                "1&${Codes.BATTERY_STATUS}&2&level:76;charge:0;vol:7400;#"
            )
            Codes.BATTERY_DETAIL -> out += response(
                "1&${Codes.BATTERY_DETAIL}&2&level:76;temp:25;cycle:42;health:98;#"
            )

            // ---- WiFi / system ---------------------------------------------
            Codes.WIFI_BAND, Codes.GET_WIFI_BAND -> out += response(
                "1&${Codes.GET_WIFI_BAND}&2&band:$wifiBand;#"
            )
            Codes.SET_WIFI_BAND -> {
                wifiBand = fields["band"]?.toIntOrNull() ?: wifiBand
                out += response("1&${Codes.SET_WIFI_BAND}&2&ret:0;#")
            }
            Codes.WIFI_SCAN -> out += response("1&${Codes.WIFI_SCAN}&2&ret:0;#")
            Codes.WIFI_LIST -> out += response(
                "1&${Codes.WIFI_LIST}&2&n:1;ssid:polaris_d13e86;rssi:-42;sec:0;#"
            )
            Codes.WIFI_CONNECT -> out += response("1&${Codes.WIFI_CONNECT}&2&ret:0;#")
            Codes.WIFI_DISCONNECT -> out += response("1&${Codes.WIFI_DISCONNECT}&2&ret:0;#")
            Codes.WIFI_STATUS -> out += response(
                "1&${Codes.WIFI_STATUS}&2&state:1;ip:192.168.0.1;#"
            )
            Codes.WIFI_RSSI -> out += response("1&${Codes.WIFI_RSSI}&2&rssi:-42;#")
            Codes.SYS_VERSION -> out += response(
                "1&${Codes.SYS_VERSION}&2&ver:$fwVersion;hw:1;#"
            )
            Codes.SYS_SERIAL -> out += response(
                "1&${Codes.SYS_SERIAL}&2&sn:$serial;#"
            )
            Codes.SYS_FW_UPGRADE -> {
                fwUpgradeState = fields["state"]?.toIntOrNull() ?: fwUpgradeState
                out += response("1&${Codes.SYS_FW_UPGRADE}&2&state:$fwUpgradeState;#")
            }
            Codes.SYS_FW_PROGRESS -> out += response(
                "1&${Codes.SYS_FW_PROGRESS}&2&p:$fwUpgradeProgress;#"
            )
            Codes.SYS_REBOOT, Codes.SYS_SHUTDOWN -> out += response("1&$code&2&ret:0;#")
            Codes.SYS_TIME -> {
                val t = fields["time"]?.toLongOrNull()
                if (t != null) gimbalRtcMillis = t
                out += response("1&${Codes.SYS_TIME}&2&time:$gimbalRtcMillis;#")
            }
            Codes.SYS_TIMEZONE -> out += response("1&${Codes.SYS_TIMEZONE}&2&tz:${fields["tz"]};#")
            Codes.SYS_LANGUAGE -> out += response("1&${Codes.SYS_LANGUAGE}&2&lang:${fields["lang"]};#")
            Codes.SYS_BUZZER -> {
                buzzer = fields["en"] == "1"
                out += response("1&${Codes.SYS_BUZZER}&2&ret:0;#")
            }
            Codes.SYS_LED -> {
                led = fields["en"] == "1"
                out += response("1&${Codes.SYS_LED}&2&ret:0;#")
            }
            Codes.SYS_LOG -> out += response("1&${Codes.SYS_LOG}&2&ret:0;#")

            // ---- OMS ---------------------------------------------------------
            Codes.OMS_RUN_STATE -> out += response(
                "1&${Codes.OMS_RUN_STATE}&2&running:${if (omsRunning) 1 else 0};" +
                    "tasks:$omsTaskCount;err:0;#"
            )
            Codes.OMS_TASK_LIST -> {
                // Format mirrors a real GET response: count first, then one
                // "id:N;state:N;name:X;" record per task. Parser relies on
                // `id:` never appearing inside a name.
                val sb = StringBuilder()
                sb.append("count:").append(omsTasks.size).append(';')
                for (t in omsTasks) {
                    sb.append("id:").append(t.id).append(';')
                    sb.append("state:").append(t.state).append(';')
                    sb.append("name:").append(t.name).append(';')
                }
                out += response("1&${Codes.OMS_TASK_LIST}&2&${sb}#")
            }

            // ---- handshake / token -----------------------------------------
            Codes.APP_PASSWORD_INFO -> out += response(
                "1&${Codes.APP_PASSWORD_INFO}&2&needed:${if (passwordRequired) 1 else 0};#"
            )
            Codes.APP_TOKEN -> {
                token = fields["token"] ?: "0"
                out += response("1&${Codes.APP_TOKEN}&2&token:$token;ret:0;#")
            }
            Codes.APP_PING -> {
                pingCounter++
                out += response("1&${Codes.APP_PING}&2&pong:$pingCounter;#")
            }
            Codes.APP_HELLO -> out += response(
                "1&${Codes.APP_HELLO}&2&app:openpolaris;ver:$fwVersion;#"
            )

            // ---- diagnostic 8-step handshake -------------------------------
            Codes.SP_TEST -> {
                val step = fields["step"]?.toIntOrNull() ?: 0
                out += response("1&${Codes.SP_TEST}&2&step:$step;ret:0;#")
            }

            // ---- default: generic ack so callers see success -------------- 
            else -> out += response("1&$code&2&ret:0;#")
        }
        return out
    }

    /** Convenience: parse a complete frame and feed it to [handle]. */
    fun handleFrame(frame: ResponseParser.Frame): List<ByteArray> = handle(frame.code, frame.fields)

    private fun response(ascii: String): ByteArray = ascii.toByteArray(Charsets.US_ASCII)
}

/** Lightweight file record used by the simulator's FILE_LIST reply. */
data class SimFile(
    val id: Int,
    var name: String,
    var prot: Int,
    var type: Int,
)

/** OMS scheduler task row (code 825, GET returns one per scheduled task). */
data class SimTask(
    val id: Int,
    var state: Int,
    var name: String,
)

/** Sentinel for empty payload (imported for clarity in callers). */
@Suppress("unused")
private val Empty: String = EMPTY_CONTENT
