package dev.openpolaris.core.protocol

import dev.openpolaris.core.domain.GimbalPosition
import dev.openpolaris.core.domain.MountState

/**
 * Table-driven command registry (ARCHITECTURE §3.2). One descriptor per code:
 * how to build the payload and how to interpret the response frame.
 * PROTOCOL.md is the human mirror of this table.
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

    // ---- jog -------------------------------------------------------------------

    val JOG_H_SPEED = Descriptor<Int>(Codes.GIMBAL_HADJ_SPEED, "jog yaw speed", payload = { "time:$it;" })
    val JOG_V_SPEED = Descriptor<Int>(Codes.GIMBAL_VADJ_SPEED, "jog pitch speed", payload = { "time:$it;" })
    val JOG_H_ANGLE = Descriptor<Int>(Codes.GIMBAL_HADJ_ANGLE, "jog yaw angle", payload = { "time:$it;" })
    val JOG_V_ANGLE = Descriptor<Int>(Codes.GIMBAL_VADJ_ANGLE, "jog pitch angle", payload = { "time:$it;" })

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

    /** Capture state push: `state:%d;bulb:%d;c:%d;`. */
    data class CaptureState(val state: Int, val bulb: Int, val c: Int)
    val CAM_GET_STATE = Descriptor<CaptureState?>(Codes.CAM_GET_STATE, "capture state",
        parse = { f ->
            val s = f.int("state"); if (s != null) CaptureState(s, f.int("bulb") ?: 0, f.int("c") ?: 0) else null
        })

    /** Trigger a single exposure via the CableRelease task path (SP_CableReleaseMakePhoto). */
    val CAM_CAPTURE = Descriptor<Unit>(Codes.CAM_CAPTURE, "capture photo")

    val ALL: Map<Int, List<Descriptor<*>>> =
        listOf(MODE_STATE, GIMBAL_POS, TRACK_START, TRACK_STOP, TRACK_HALF_SPEED, AHRS,
            GOTO_AZ_ALT, POS_RESET, JOG_H_SPEED, JOG_V_SPEED, JOG_H_ANGLE, JOG_V_ANGLE,
            CAM_GET_ISO, CAM_SET_ISO, CAM_GET_WB, CAM_SET_WB, CAM_GET_FNUM, CAM_SET_FNUM,
            CAM_GET_EV, CAM_SET_EV, CAM_GET_STATE, CAM_CAPTURE)
            .groupBy { it.code }

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
