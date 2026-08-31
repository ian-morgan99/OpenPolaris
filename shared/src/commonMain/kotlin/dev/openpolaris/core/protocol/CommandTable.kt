package dev.openpolaris.core.protocol

import dev.openpolaris.core.domain.BatteryDetail
import dev.openpolaris.core.domain.CameraAttachment
import dev.openpolaris.core.domain.CameraInfo
import dev.openpolaris.core.domain.DeviceInfo
import dev.openpolaris.core.domain.ExAxisState
import dev.openpolaris.core.domain.FileList
import dev.openpolaris.core.domain.GimbalPosition
import dev.openpolaris.core.domain.MountState
import dev.openpolaris.core.domain.OmsState
import dev.openpolaris.core.domain.SdStatus
import dev.openpolaris.core.domain.TaskList
import dev.openpolaris.core.domain.Temperature

/**
 * Table-driven command registry (ARCHITECTURE §3.2). One descriptor per code:
 * how to build the payload and how to interpret the response frame.
 * PROTOCOL.md is the human mirror of this table.
 *
 * Markers:
 * - "VERIFIED" — wire format observed in live captures + tests pass.
 * - "CORPUS" — wire format lifted from string-corpus analysis but not yet
 *   tested against firmware.
 * - "UNVERIFIED" — code recorded; payload/payload-keys are educated guesses.
 */
object CommandTable {

    /** A registered command: code, human name, optional payload builder and response parser. */
    data class Descriptor<T>(
        val code: Int,
        val name: String,
        val payload: (T) -> String = { EMPTY_CONTENT },
        val parse: ((ResponseParser.Frame) -> T?)? = null,
    )

    // ---- status / telemetry -------------------------------------------------

    val MODE_STATE = Descriptor<MountState>(
        Codes.PUSH_MODE_STATE, "mode state",
        parse = MountState::fromFrame284,
    )

    val GIMBAL_POS = Descriptor<GimbalPosition>(
        Codes.GET_GIMBAL_POS, "gimbal position",
        parse = GimbalPosition::fromFrame517,
    )

    /** Push every 2s while battery poller is active. */
    val BATTERY_STATUS = Descriptor<Unit>(Codes.BATTERY_STATUS, "battery status")
    val BATTERY_DETAIL = Descriptor<Unit>(Codes.BATTERY_DETAIL, "battery detail")
    val WIFI_BAND = Descriptor<Unit>(Codes.GET_WIFI_BAND, "wifi band")

    // ---- tracking ------------------------------------------------------------

    val TRACK_START = Descriptor<Unit>(
        Codes.SET_TRACK_AU_STATE, "tracking start", payload = { "state:1;" },
    )
    /** Tracking start with explicit speed index (see TrackingController.start doc). */
    val TRACK_START_WITH_SPEED = Descriptor<Int>(
        Codes.SET_TRACK_AU_STATE, "tracking start (with speed)",
        payload = { speed -> "state:1;speed:$speed;" },
    )
    val TRACK_STOP = Descriptor<Unit>(
        Codes.SET_TRACK_AU_STATE, "tracking stop", payload = { "state:0;" },
    )

    /** Half-speed payload is INVERTED on the wire: 0 = on, 1 = off. */
    val TRACK_HALF_SPEED = Descriptor<Boolean>(
        Codes.SET_TRACK_HALF_SPEED, "half speed",
        payload = { on -> "halfSpeed:${if (on) 0 else 1};" },
    )

    val AHRS = Descriptor<Boolean>(
        Codes.SET_AHRS_STATE, "AHRS",
        payload = { on -> "state:${if (on) 1 else 0};" },
    )

    // ---- slewing --------------------------------------------------------------

    val GOTO_AZ_ALT = Descriptor<Pair<Double, Double>>(
        Codes.SET_GOTO_AU_STATE, "goto az/alt",
        payload = { (az, alt) -> "az:${az.format4()};alt:${alt.format4()};" },
    )

    val POS_RESET = Descriptor<Unit>(Codes.POS_RESET, "position reset")

    // ---- alignment / calibration ----------------------------------------------

    /**
     * Star-alignment step (530). Firmware format:
     * `step:%d;yaw:%f;pitch:%f;lat:%f;num:%d;lng:%f;`
     * The stock app sends step=2 with the target alt/az computed client-side;
     * num is the star index for multi-star alignment.
     */
    data class AlignmentStar(
        val yawDeg: Double,
        val pitchDeg: Double,
        val latDeg: Double,
        val lngEastDeg: Double,
        val starIndex: Int = 0,
    )
    val ALIGN_STAR = Descriptor<AlignmentStar>(
        Codes.CALIBRATE_START, "star alignment",
        payload = { s ->
            "step:2;yaw:${s.yawDeg.format4()};pitch:${s.pitchDeg.format4()};" +
                "lat:${s.latDeg.format4()};num:${s.starIndex};lng:${s.lngEastDeg.format4()};"
        },
    )

    /** Cancel an in-progress goto (519 `state:0`). */
    val GOTO_CANCEL = Descriptor<Unit>(Codes.SET_GOTO_AU_STATE, "goto cancel", payload = { "state:0;" })

    // ---- astro helper settings --------------------------------------------------

    val DITHER_GET = Descriptor<Int?>(Codes.GET_DITHER_STATE, "dither get", parse = { it.int("state") })
    val DITHER_SET = Descriptor<Boolean>(Codes.SET_DITHER_STATE, "dither set", payload = { "state:${if (it) 1 else 0};" })

    /** UNVERIFIED: LIMITS wire format is a best-effort guess mirrored from
     *  the TILT pattern. Validate against real hardware before relying on
     *  the round-trip — for now the parser returns Int? and the VM
     *  translates !=0 to true. */
    val LIMITS_GET = Descriptor<Int?>(Codes.GET_LIMIT_STATE, "limits get", parse = { it.int("state") })
    val LIMITS_SET = Descriptor<Boolean>(Codes.SET_LIMIT_STATE, "limits set", payload = { "state:${if (it) 1 else 0};" })

    val AUTO_LEVEL_GET_EN = Descriptor<Int?>(Codes.GET_AUTO_LEVEL_EN, "auto level en get", parse = { it.int("en") })
    val AUTO_LEVEL_SET_EN = Descriptor<Boolean>(Codes.SET_AUTO_LEVEL_EN, "auto level en set", payload = { "en:${if (it) 1 else 0};" })
    val AUTO_LEVEL_TRIGGER = Descriptor<Unit>(Codes.SET_AUTO_LEVEL_STATE, "auto level trigger")

    val SETTLING_TIME_GET = Descriptor<Int?>(Codes.GET_SETTLING_TIME, "settling time get", parse = { it.int("time") })
    val SETTLING_TIME_SET = Descriptor<Int>(Codes.SET_SETTLING_TIME, "settling time set", payload = { "time:$it;" })

    // ---- jog -------------------------------------------------------------------
    //
    // All four codes accept `time:Nms;` (jog this axis for N milliseconds). This
    // is the format confirmed via live gimbal captures (see Panes.kt:JogPad +
    // TrackingController.jog). Alpaca/ogecko document codes 513/514 as
    // `speed:%s;` instead and 515/516 as an opaque "angle string" — the open
    // client has not been probed against hardware for these, so we ship the
    // duration encoding for both "speed" and "angle" pairs. A future hardware
    // pass can split these into separate `speed:`/`angle:` payload keys if the
    // firmware rejects the duration encoding. See PROTOCOL.md §3.2 and
    // FIRMWARE-ANALYSIS-ALPACA.md for the divergences.

    val JOG_H_SPEED = Descriptor<Int>(Codes.GIMBAL_HADJ_SPEED, "jog yaw speed", payload = { "time:$it;" })
    val JOG_V_SPEED = Descriptor<Int>(Codes.GIMBAL_VADJ_SPEED, "jog pitch speed", payload = { "time:$it;" })
    val JOG_H_ANGLE = Descriptor<Int>(Codes.GIMBAL_HADJ_ANGLE, "jog yaw angle", payload = { "time:$it;" })
    val JOG_V_ANGLE = Descriptor<Int>(Codes.GIMBAL_VADJ_ANGLE, "jog pitch angle", payload = { "time:$it;" })

    // ---- system (corpus-derived) ----------------------------------------------

    /** Push current extended-axis / tripod state. */
    val EX_AXIS_STA = Descriptor<Unit>(Codes.EX_AXIS_STA, "ex axis state")

    // ---- live-captured device info / telemetry (VERIFIED 2026-08-30) --------

    /** 780 — gimbal hardware/firmware version. Live: `hw:1.1.1.2;sw:6.0.0.54;`. */
    val DEVICE_INFO = Descriptor<DeviceInfo>(
        Codes.DEVICE_INFO, "device info",
        parse = DeviceInfo::fromFrame,
    )

    /** 525 — temperature / IMU read. Live: `Tempa<hex16>`. */
    val GET_TEMPERATURE = Descriptor<Temperature>(
        Codes.GET_TEMPERATURE, "temperature",
        parse = Temperature::fromFrame,
    )

    /** 286 — camera info. Live: `manufacturer:none;model:none;state:-5;storage:0;photoFormat:0;`. */
    val CAM_INFO = Descriptor<CameraAttachment>(
        Codes.CAM_INFO, "camera info",
        parse = CameraAttachment::fromFrame,
    )

    /** 282 — format. Live acks with `ret:0`. Used by storage housekeeping. */
    val SYS_FORMAT = Descriptor<Unit>(Codes.SYS_FORMAT, "format")

    /** 311 — focus adjust step. Stock app sends `step:N;` for an incremental focus nudge. */
    val CAM_FOCUS = Descriptor<Int>(
        Codes.CAM_FOCUS, "camera focus step",
        payload = { "step:$it;" },
    )

    /** 527 — video record. `state:0/1;` toggles recording. */
    val CAM_VIDEO = Descriptor<Boolean>(
        Codes.CAM_VIDEO, "video record",
        payload = { "state:${if (it) 1 else 0};" },
    )

    /** 520 — generic action ack (`ret:0` on success). Used by capture/auto-level triggers. */
    val ACK_GENERIC = Descriptor<Unit>(Codes.ACK_GENERIC, "generic ack")

    /** 287 — verbose state dump (contains password + security answer base64). */
    val STATE_DUMP = Descriptor<Unit>(Codes.STATE_DUMP, "state dump")
    /** Gimbal RTC millis. (inferred from protocol corpus; uses 544) */
    val SET_SYSTEM_TIME = Descriptor<Long>(Codes.SET_SETTLING_TIME, "set system time (corpus: uses 544)",
        payload = { "time:$it;" })
    /** Diagnostic echo — firmwares reply with the same string. (inferred) */
    val TEST_STEP = Descriptor<String>(Codes.SP_TEST, "test step", payload = { "step:$it;" })

    // ---- camera (UNVERIFIED codes — see Codes.kt note; payloads are ground truth) --

    /** Camera parameter value: an index into the firmware-sorted option list. */
    data class CamParam(val index: Int)

    val CAM_GET_ISO = Descriptor<CamParam?>(Codes.CAM_GET_ISO, "camera iso get",
        parse = { f -> f.int("iso")?.let { CamParam(it) } })
    val CAM_SET_ISO = Descriptor<CamParam>(Codes.CAM_SET_ISO, "camera iso set",
        payload = { "iso:${it.index};" })
    val CAM_GET_WB = Descriptor<CamParam?>(Codes.CAM_GET_WB, "camera wb get",
        parse = { f -> f.int("wb")?.let { CamParam(it) } })
    val CAM_SET_WB = Descriptor<CamParam>(Codes.CAM_SET_WB, "camera wb set",
        payload = { "wb:${it.index};" })
    val CAM_GET_FNUM = Descriptor<CamParam?>(Codes.CAM_GET_FNUM, "camera aperture get",
        parse = { f -> f.int("fNum")?.let { CamParam(it) } })
    val CAM_SET_FNUM = Descriptor<CamParam>(Codes.CAM_SET_FNUM, "camera aperture set",
        payload = { "fNum:${it.index};" })
    val CAM_GET_EV = Descriptor<CamParam?>(Codes.CAM_GET_EV, "camera ev get",
        parse = { f -> f.int("ev")?.let { CamParam(it) } })
    val CAM_SET_EV = Descriptor<CamParam>(Codes.CAM_SET_EV, "camera ev set",
        payload = { "ev:${it.index};" })
    val CAM_GET_FOCUS = Descriptor<CamParam?>(Codes.CAM_GET_FOCUS, "camera focus get",
        parse = { f -> f.int("focus")?.let { CamParam(it) } })
    val CAM_SET_FOCUS = Descriptor<CamParam>(Codes.CAM_SET_FOCUS, "camera focus set",
        payload = { "focus:${it.index};" })
    val CAM_GET_IMG_SIZE = Descriptor<CamParam?>(Codes.CAM_GET_IMG_SIZE, "camera img size get",
        parse = { f -> f.int("imgSize")?.let { CamParam(it) } })
    val CAM_SET_IMG_SIZE = Descriptor<CamParam>(Codes.CAM_SET_IMG_SIZE, "camera img size set",
        payload = { "imgSize:${it.index};" })
    val CAM_GET_IMG_FMT = Descriptor<CamParam?>(Codes.CAM_GET_IMG_FMT, "camera img fmt get",
        parse = { f -> f.int("imgFmt")?.let { CamParam(it) } })
    val CAM_SET_IMG_FMT = Descriptor<CamParam>(Codes.CAM_SET_IMG_FMT, "camera img fmt set",
        payload = { "imgFmt:${it.index};" })
    val CAM_GET_COLOR = Descriptor<CamParam?>(Codes.CAM_GET_COLOR, "camera color get",
        parse = { f -> f.int("color")?.let { CamParam(it) } })
    val CAM_SET_COLOR = Descriptor<CamParam>(Codes.CAM_SET_COLOR, "camera color set",
        payload = { "color:${it.index};" })
    val CAM_GET_SHUTTER = Descriptor<CamParam?>(Codes.CAM_GET_SHUTTER, "camera shutter get",
        parse = { f -> f.int("shutter")?.let { CamParam(it) } })
    val CAM_SET_SHUTTER = Descriptor<CamParam>(Codes.CAM_SET_SHUTTER, "camera shutter set",
        payload = { "shutter:${it.index};" })
    val CAM_GET_CAPTURE_MODE = Descriptor<CamParam?>(Codes.CAM_GET_CAPTURE_MODE, "camera capture mode get",
        parse = { f -> f.int("captureMode")?.let { CamParam(it) } })
    val CAM_SET_CAPTURE_MODE = Descriptor<CamParam>(Codes.CAM_SET_CAPTURE_MODE, "camera capture mode set",
        payload = { "captureMode:${it.index};" })

    /** Capture state push: `state:%d;bulb:%d;c:%d;`. */
    data class CaptureState(val state: Int, val bulb: Int, val c: Int)
    val CAM_GET_STATE = Descriptor<CaptureState?>(Codes.CAM_GET_STATE, "capture state",
        parse = { f ->
            val s = f.int("state"); if (s != null) CaptureState(s, f.int("bulb") ?: 0, f.int("c") ?: 0) else null
        })

    /** Trigger a single exposure via the CableRelease task path (SP_CableReleaseMakePhoto). */
    val CAM_CAPTURE = Descriptor<Unit>(Codes.CAM_CAPTURE, "capture photo")

    // ---- post-connect burst ---------------------------------------------------
    //
    // The order here MUST match `tools/cli-probe/.../Burst.kt` so the
    // simulator and the live device see the same traffic.
    // See docs/PLANNING-2026-08.md Step 5.

    /**
     * One GET step in the pre-camera burst. [parse] is reused from the matching
     * descriptor in this table where one exists (battery / SD / OMS / ex-axis)
     * and inlined for codes that don't have a descriptor yet (808/809/802/543).
     */
    data class BurstStep<T>(val code: Int, val parse: (ResponseParser.Frame) -> T?)

    /** Pre-camera GETs fired in order after the lifecycle handshake. */
    val BURST_PRE_CAMERA: List<BurstStep<*>> = listOf(
        BurstStep<String>(808) { it["ver"] },
        BurstStep<String>(809) { it["sn"] },
        BurstStep<Int>(802) { it.int("band") },
        BurstStep<BatteryDetail>(778) { BatteryDetail.fromFrame(it) },
        BurstStep<BatteryDetail>(779) { BatteryDetail.fromFrame(it) },
        BurstStep<SdStatus>(775) { SdStatus.fromFrame(it) },
        BurstStep<OmsState>(824) { OmsState.fromFrame(it) },
        BurstStep<ExAxisState>(524) { ExAxisState.fromFrame(it) },
        BurstStep<Int>(543) { it.int("time") },
        BurstStep<DeviceInfo>(780) { DeviceInfo.fromFrame(it) },
        BurstStep<Temperature>(525) { Temperature.fromFrame(it) },
    )

    /**
     * Camera info GETs (10 codes). Each response merges one field into a
     * running [CameraInfo] snapshot. Codes 266 (STATE) and 267 (CAPTURE) are
     * NOT part of this — they feed the CaptureState pipeline / capture button.
     */
    val BURST_CAMERA_CODES: List<Int> = listOf(258, 260, 262, 264, 268, 270, 272, 274, 276, 278)

    // ---- file / SD (corpus-derived) -------------------------------------------

    /** Page of files: `type:%d;page:%d;` request, `type:0;page:0;...;#` reply. */
    data class FileListRequest(val type: Int, val page: Int)
    val FILE_LIST = Descriptor<FileListRequest>(Codes.FILE_LIST, "file list",
        payload = { req: FileListRequest -> "type:${req.type};page:${req.page};" },
    )
    val FILE_DELETE = Descriptor<Int>(Codes.FILE_DELETE, "file delete",
        payload = { "id:$it;" })
    val FILE_RENAME = Descriptor<Pair<Int, String>>(Codes.FILE_RENAME, "file rename",
        payload = { (id, name) -> "id:$id;name:$name;" })
    val FILE_PROTECT = Descriptor<Pair<Int, Int>>(Codes.FILE_PROTECT, "file protect",
        payload = { (id, prot) -> "id:$id;prot:$prot;" })
    val FILE_INFO = Descriptor<Int>(Codes.FILE_INFO, "file info",
        payload = { "id:$it;" })
    val FILE_SD_STATUS = Descriptor<Unit>(Codes.FILE_SD_STATUS, "file sd status")
    val FILE_SD_FORMAT = Descriptor<Unit>(Codes.FILE_SD_FORMAT, "file sd format")
    val FILE_SET_TYPE = Descriptor<Int>(Codes.FILE_SET_TYPE, "file set type",
        payload = { "type:$it;" })
    val FILE_UPLOAD_FW = Descriptor<String>(Codes.FILE_UPLOAD_FW, "file upload fw (corpus: path)",
        payload = { "path:$it;" })
    val FILE_UPLOAD_CHUNK = Descriptor<String>(Codes.FILE_UPLOAD_CHUNK, "file upload chunk",
        payload = { "data:$it;" })
    val FILE_UPLOAD_END = Descriptor<Unit>(Codes.FILE_UPLOAD_END, "file upload end")

    // ---- WiFi / system (corpus-derived) ---------------------------------------

    val SET_WIFI_BAND = Descriptor<Int>(Codes.SET_WIFI_BAND, "set wifi band",
        payload = { "band:$it;" })
    val WIFI_SCAN = Descriptor<Unit>(Codes.WIFI_SCAN, "wifi scan")
    val WIFI_LIST = Descriptor<Unit>(Codes.WIFI_LIST, "wifi list")
    val WIFI_CONNECT = Descriptor<String>(Codes.WIFI_CONNECT, "wifi connect (corpus: ssid)",
        payload = { "ssid:$it;" })
    val WIFI_DISCONNECT = Descriptor<Unit>(Codes.WIFI_DISCONNECT, "wifi disconnect")
    val WIFI_STATUS = Descriptor<Unit>(Codes.WIFI_STATUS, "wifi status")
    val WIFI_RSSI = Descriptor<Unit>(Codes.WIFI_RSSI, "wifi rssi")
    val SYS_VERSION = Descriptor<Unit>(Codes.SYS_VERSION, "sys version")
    val SYS_SERIAL = Descriptor<Unit>(Codes.SYS_SERIAL, "sys serial")
    val SYS_FW_UPGRADE = Descriptor<Int>(Codes.SYS_FW_UPGRADE, "sys fw upgrade (corpus: state)",
        payload = { "state:$it;" })
    val SYS_FW_PROGRESS = Descriptor<Unit>(Codes.SYS_FW_PROGRESS, "sys fw progress")
    val SYS_REBOOT = Descriptor<Unit>(Codes.SYS_REBOOT, "sys reboot")
    val SYS_SHUTDOWN = Descriptor<Unit>(Codes.SYS_SHUTDOWN, "sys shutdown")
    val SYS_TIME = Descriptor<Long>(Codes.SYS_TIME, "sys time",
        payload = { "time:$it;" })
    val SYS_TIMEZONE = Descriptor<Int>(Codes.SYS_TIMEZONE, "sys timezone",
        payload = { "tz:$it;" })
    val SYS_LANGUAGE = Descriptor<Int>(Codes.SYS_LANGUAGE, "sys language",
        payload = { "lang:$it;" })
    val SYS_BUZZER = Descriptor<Boolean>(Codes.SYS_BUZZER, "sys buzzer",
        payload = { "en:${if (it) 1 else 0};" })
    val SYS_LED = Descriptor<Boolean>(Codes.SYS_LED, "sys led",
        payload = { "en:${if (it) 1 else 0};" })
    val SYS_LOG = Descriptor<Unit>(Codes.SYS_LOG, "sys log")

    // ---- OMS operational mode (corpus-derived) -------------------------------

    /** OMS = On-Mount State. 824 pushes current OMS + error state. */
    val OMS_RUN_STATE = Descriptor<Unit>(Codes.OMS_RUN_STATE, "oms run state")

    /**
     * 825 GET returns the scheduled task table as multiple
     * `id:N;state:N;name:X;` records prefixed with `count:N;`. Parser splits
     * the raw payload on `id:` (look-behind for `;`) since the field map
     * collapses duplicate keys.
     */
    val OMS_TASK_LIST = Descriptor<TaskList>(
        Codes.OMS_TASK_LIST, "oms task list",
        parse = TaskList::fromFrame,
    )

    // ---- app handshake / token (corpus-derived) ------------------------------

    val APP_PASSWORD_INFO = Descriptor<Unit>(Codes.APP_PASSWORD_INFO, "app password info")
    val APP_TOKEN = Descriptor<Unit>(Codes.APP_TOKEN, "app token")
    val APP_PING = Descriptor<Unit>(Codes.APP_PING, "app ping")
    val APP_HELLO = Descriptor<Unit>(Codes.APP_HELLO, "app hello")

    val ALL: Map<Int, List<Descriptor<*>>> =
        listOf(
            MODE_STATE, GIMBAL_POS, BATTERY_STATUS, BATTERY_DETAIL, WIFI_BAND,
            TRACK_START, TRACK_STOP, TRACK_HALF_SPEED, AHRS,
            GOTO_AZ_ALT, GOTO_CANCEL, ALIGN_STAR, POS_RESET,
            JOG_H_SPEED, JOG_V_SPEED, JOG_H_ANGLE, JOG_V_ANGLE,
            DITHER_GET, DITHER_SET, LIMITS_GET, LIMITS_SET, AUTO_LEVEL_GET_EN, AUTO_LEVEL_SET_EN, AUTO_LEVEL_TRIGGER,
            SETTLING_TIME_GET, SETTLING_TIME_SET,
            EX_AXIS_STA, SET_SYSTEM_TIME, TEST_STEP,
            DEVICE_INFO, GET_TEMPERATURE, CAM_INFO, SYS_FORMAT,
            CAM_FOCUS, CAM_VIDEO, ACK_GENERIC, STATE_DUMP,
            CAM_GET_ISO, CAM_SET_ISO, CAM_GET_WB, CAM_SET_WB, CAM_GET_FNUM, CAM_SET_FNUM,
            CAM_GET_EV, CAM_SET_EV,
            CAM_GET_FOCUS, CAM_SET_FOCUS,
            CAM_GET_IMG_SIZE, CAM_SET_IMG_SIZE,
            CAM_GET_IMG_FMT, CAM_SET_IMG_FMT,
            CAM_GET_COLOR, CAM_SET_COLOR,
            CAM_GET_SHUTTER, CAM_SET_SHUTTER,
            CAM_GET_CAPTURE_MODE, CAM_SET_CAPTURE_MODE,
            CAM_GET_STATE, CAM_CAPTURE,
            FILE_LIST, FILE_DELETE, FILE_RENAME, FILE_PROTECT, FILE_INFO,
            FILE_SD_STATUS, FILE_SD_FORMAT, FILE_SET_TYPE, FILE_UPLOAD_FW,
            FILE_UPLOAD_CHUNK, FILE_UPLOAD_END,
            SET_WIFI_BAND, WIFI_SCAN, WIFI_LIST, WIFI_CONNECT, WIFI_DISCONNECT,
            WIFI_STATUS, WIFI_RSSI, SYS_VERSION, SYS_SERIAL, SYS_FW_UPGRADE,
            SYS_FW_PROGRESS, SYS_REBOOT, SYS_SHUTDOWN, SYS_TIME, SYS_TIMEZONE,
            SYS_LANGUAGE, SYS_BUZZER, SYS_LED, SYS_LOG,
            OMS_RUN_STATE, OMS_TASK_LIST,
            APP_PASSWORD_INFO, APP_TOKEN, APP_PING, APP_HELLO,
        ).groupBy { it.code }

    fun describe(code: Int): String =
        ALL[code]?.joinToString("/") { it.name } ?: "code $code"
}

/** Common-main 4-decimal fixed formatting (String.format is JVM-only). */
internal fun Double.format4(): String {
    val neg = this < 0
    val abs = if (neg) -this else this
    val scaled = (abs * 10000.0 + 0.5).toLong()
    val whole = scaled / 10000
    val frac = (scaled % 10000).toString().padStart(4, '0')
    return (if (neg) "-" else "") + "$whole.$frac"
}
