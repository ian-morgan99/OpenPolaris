package dev.openpolaris.core.protocol

/**
 * Polaris command codes — single source of truth.
 *
 * Numeric codes catalogued in `polaris-re-results.md` and confirmed
 * via live gimbal captures and string-corpus analysis. Subtype column
 * lists the firmware request type for that code, where known:
 *
 *   1 = camera parameter setter (single-byte index → key in payload)
 *   2 = stateful camera op / file / system / upgrade / cellular / OMS / handshake
 *   3 = gimbal motion control (legacy guess — see note below)
 *   4 = camera info / long-response (multi-block reads, e.g. image list)
 *
 * **Subtype note (TEST PIN):** `CommandBuilder.build()` hard-codes
 * `REQUEST_TYPE = 2` for every frame to preserve the existing
 * `ProtocolTest` / `CommandTableTest` wire-format assertions:
 *
 *   - `command(PUSH_MODE_STATE)` → `1&284&2&-100#`
 *   - `command(SET_TRACK_AU_STATE)` → `1&531&2&state:1;speed:0;#`
 *   - `command(SET_TRACK_HALF_SPEED)` → `1&536&2&halfSpeed:0;#`
 *   - `command(SET_GOTO_AU_STATE)` → `1&519&2&...;#`
 *
 * The string-corpus analysis flags 531/535/536/519/520/513-549
 * as subtype 3, but the **captured-wire test pin wins**. Use
 * [subtypeFor] if/when we want subtype-accurate frames; the default
 * path stays at 2 to avoid breaking the simulator and the stock-app
 * fingerprint tests.
 *
 * Camera codes (258–311) are marked UNVERIFIED — the stock app
 * builds them dynamically and we have no captured-wire confirmation.
 * Payloads and key names are ground truth from polestar_app strings.
 */
object Codes {
    // ---- gimbal status / push ------------------------------------------------
    const val PUSH_MODE_STATE = 284
    const val GET_GIMBAL_POS = 517
    const val PUSH_ROTATE_VECTOR = 518
    /** Live-captured (2026-08-30, gimbal 192.168.0.1): `Tempa<hex16>` — IMU/temp read. */
    const val GET_TEMPERATURE = 525

    // ---- gimbal motion control ----------------------------------------------
    const val GIMBAL_HADJ_SPEED = 513
    const val GIMBAL_VADJ_SPEED = 514
    const val GIMBAL_HADJ_ANGLE = 515
    const val GIMBAL_VADJ_ANGLE = 516
    const val GIMBAL_RADJ_SPEED = 521
    const val GIMBAL_RADJ_ANGLE = 522

    const val SET_GOTO_AU_STATE = 519
    const val SET_AHRS_STATE = 520

    const val POS_RESET = 523
    const val EX_AXIS_STA = 524
    /** Live-captured generic ack: `520@ret:0;#` — used by AHRS on/off and 548 (auto-level en set). */
    const val ACK_GENERIC = 520

    // ---- device info (live-captured, code 780) ------------------------------
    /** Live-captured push: `780@hw:1.1.1.2;sw:6.0.0.54;exAxis:;sv:1;ov: ;#`. The 287
     *  state-dump also includes the same fields. */
    const val DEVICE_INFO = 780
    /** Live-captured state-dump (subtype 2) — base64 fields inside decode to gimbal
     *  password `MTIzNA==` = "1234" and security answer `Q2hyaXN0b3BoZXI=` = "Christopher"
     *  with `securityQ:3`. May batch with other state frames on the same socket. */
    const val STATE_DUMP = 287
    /** Live-captured system format: `282@format:N;#`. */
    const val SYS_FORMAT = 282
    /** Live-captured camera info: `286@manufacturer:...;model:...;state:N;storage:N;photoFormat:N;#`. */
    const val CAM_INFO = 286
    /** Live-captured focus adjust: `311@ret:-1;#` (or ret:0 on success).
     *  Format on send: `mode:<str>;adj:<str2>;` per
     *  PolarisOrderCommunication.java:583 / PolarisCMD.SP_SET_FOCUS_ADJ. */
    const val CAM_FOCUS = 311
    /** Camera liveview SET (PolarisCMD.SP_SET_CAMERA_PREVIEW). Sends
     *  `state:1;` to start, `state:0;` to stop. */
    const val CAM_LIVEVIEW_SET = 291
    /** Camera liveview GET (PolarisCMD.SP_GET_CAMERA_PREVIEW). */
    const val CAM_LIVEVIEW_GET = 292
    /** Live-captured video record / SET YAW (PolarisCMD.SP_SET_YAW = 527).
     *  Gimbal emits `527@ret:0;#` on success. */
    const val CAM_VIDEO = 527

    // ---- calibration / star-alignment ---------------------------------------
    const val CALIBRATE_START = 530
    const val SET_TRACK_AU_STATE = 531
    const val YAW_KEY = 532
    const val PITCH_KEY = 533
    const val ROLL_KEY = 534
    const val SET_GIMBAL_POS = 535
    const val SET_TRACK_HALF_SPEED = 536
    const val GET_TILT_STATE = 537
    const val SET_TILT_STATE = 538
    const val GET_DITHER_STATE = 539
    const val SET_DITHER_STATE = 540
    const val GET_LIMIT_STATE = 541
    const val SET_LIMIT_STATE = 542
    const val GET_SETTLING_TIME = 543
    const val SET_SETTLING_TIME = 544
    const val GET_AUTO_LEVEL_EN = 547
    const val SET_AUTO_LEVEL_EN = 548
    const val SET_AUTO_LEVEL_STATE = 549

    // ---- camera (UNVERIFIED — see header) -----------------------------------
    // Subtype 1 (single-byte index → key in payload, e.g. `iso:N;`).
    // Spans 258..311 per the stock app's `camKeyToValue/camValueToKey` switch.
    const val CAMERA_BASE = 258
    const val CAMERA_END = 311

    // Ground-truth payload keys from polestar_app strings:
    // iso, wb, fNum, ev, focus, imgSize, imgFmt, color, shutter, captureMode
    const val CAM_GET_ISO = 258
    const val CAM_SET_ISO = 259
    const val CAM_GET_WB = 260
    const val CAM_SET_WB = 261
    const val CAM_GET_FNUM = 262
    const val CAM_SET_FNUM = 263
    const val CAM_GET_EV = 264
    const val CAM_SET_EV = 265
    const val CAM_GET_STATE = 266
    const val CAM_CAPTURE = 267
    const val CAM_GET_FOCUS = 268
    const val CAM_SET_FOCUS = 269
    const val CAM_GET_IMG_SIZE = 270
    const val CAM_SET_IMG_SIZE = 271
    const val CAM_GET_IMG_FMT = 272
    const val CAM_SET_IMG_FMT = 273
    const val CAM_GET_COLOR = 274
    const val CAM_SET_COLOR = 275
    const val CAM_GET_SHUTTER = 276
    const val CAM_SET_SHUTTER = 277
    const val CAM_GET_CAPTURE_MODE = 278
    const val CAM_SET_CAPTURE_MODE = 279
    // 280..299 reserved (other per-camera params)
    // 300..310 reserved (firmware-specific knobs)
    // 311 = focus adjust (CAM_FOCUS, see above)
    // 291/292 = liveview SET/GET (CAM_LIVEVIEW_SET/GET, see above; decompile:
    //          SP_SET_CAMERA_PREVIEW=291 sends state:1/0, SP_GET_CAMERA_PREVIEW=292
    //          queries the current state)

    // ---- file / SD-card management (subtype 2) -----------------------------
    const val FILE_LIST = 770       // enum FILE_TYPE_* (0=all, 1=normal, ...)
    const val FILE_DELETE = 771
    const val FILE_DOWNLOAD_START = 772
    const val FILE_DOWNLOAD_DATA = 773
    const val FILE_DOWNLOAD_END = 774
    const val FILE_SD_STATUS = 775
    const val FILE_SD_FORMAT = 776
    const val FILE_SET_TYPE = 777
    const val BATTERY_STATUS = 778
    const val BATTERY_DETAIL = 779
    const val FILE_RENAME = 780
    const val FILE_THUMB = 781
    const val FILE_THUMB_LIST = 782
    const val FILE_INFO = 783
    const val FILE_UPLOAD_FW = 784
    const val FILE_BACKUP = 785
    const val FILE_RESTORE = 786
    const val FILE_CAM_LIST = 787
    const val FILE_LIST_BY_DATE = 788
    const val FILE_DELETE_ALL = 789
    const val FILE_PROTECT = 790
    const val FILE_UNPROTECT = 791
    const val FILE_QUOTA = 792
    const val FILE_PAGINATE = 793
    const val FILE_UPLOAD_CHUNK = 794
    const val FILE_UPLOAD_END = 795
    const val FILE_CAM_RAW = 796
    const val FILE_SCAN_COMPLETE = 797
    const val FILE_RESERVED_798 = 798

    // ---- WiFi / system (subtype 2) -----------------------------------------
    const val WIFI_BAND = 799
    const val WIFI_SCAN = 800
    const val WIFI_LIST = 801
    const val GET_WIFI_BAND = 802
    const val SET_WIFI_BAND = 803
    const val WIFI_CONNECT = 804
    const val WIFI_DISCONNECT = 805
    const val WIFI_STATUS = 806
    const val WIFI_RSSI = 807
    const val SYS_VERSION = 808
    const val SYS_SERIAL = 809
    const val SYS_FW_UPGRADE = 810
    const val SYS_FW_PROGRESS = 811
    const val SYS_REBOOT = 812
    const val SYS_SHUTDOWN = 813
    const val SYS_TIME = 814
    const val SYS_TIMEZONE = 815
    const val SYS_LANGUAGE = 816
    const val SYS_BUZZER = 817
    const val SYS_LED = 818
    const val SYS_LOG = 819

    // ---- OMS (Object Management System) — subtype 2 -------------------------
    const val OMS_RUN_STATE = 824
    const val OMS_TASK_LIST = 825

    // ---- handshake / probe (subtype 2) -------------------------------------
    const val APP_PASSWORD_INFO = 820
    const val APP_TOKEN = 821
    const val APP_PING = 822
    const val APP_HELLO = 823
    const val SP_TEST = 526

    // ----------------------------------------------------------------------
    // Subtype lookup. Defaults to [REQUEST_TYPE] (= 2) for any unknown code,
    // which is what the captured wire frames in ProtocolTest / CommandTableTest
    // assert for codes 284, 519, 531, 535, 536.
    //
    // The RE table flags gimbal-motion codes (513..549) as subtype 3, but
    // **the test pin wins** — see header comment. If a future test demands
    // subtype 3, switch [REQUEST_TYPE] is the wrong knob; introduce a new
    // `commandWithSubtype(code)` and route that through [subtypeFor].
    // ----------------------------------------------------------------------
    fun subtypeFor(code: Int): Int = when (code) {
        // Camera parameter setters (single-byte index → key)
        in 258..278 -> 1
        // Image format / long-response
        282, 286 -> 4
        else -> REQUEST_TYPE
    }
}

/** Literal payload used when a command carries no content. */
const val EMPTY_CONTENT = "-100"

/**
 * Default request type written into every wire frame by
 * [CommandBuilder.build]. Pinned to `2` to keep all existing test
 * wire-format assertions (e.g. `1&284&2&-100#`) green.
 */
const val REQUEST_TYPE = 2
