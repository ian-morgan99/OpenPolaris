package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.EMPTY_CONTENT

/**
 * High-level tracking control. Encapsulates the half-speed quirk:
 * SET_TRACK_HALF_SPEED (536) payload value is INVERTED on the wire —
 * sending `1` turns half-speed OFF, `0` turns it ON.
 */
class TrackingController(private val session: MountSession) {

    suspend fun start() {
        session.send(Codes.SET_TRACK_AU_STATE, "state:1;")
    }

    suspend fun stop() {
        session.send(Codes.SET_TRACK_AU_STATE, "state:0;")
    }

    suspend fun setHalfSpeed(on: Boolean) {
        // Wire quirk: inverted. See class doc and ProtocolTest golden frames.
        val wire = if (on) 0 else 1
        session.send(Codes.SET_TRACK_HALF_SPEED, "halfSpeed:$wire;")
    }

    suspend fun gotoAzAlt(azimuthDeg: Double, altitudeDeg: Double) {
        session.send(
            Codes.SET_GOTO_AU_STATE,
            "az:${azimuthDeg.format4()};alt:${altitudeDeg.format4()};"
        )
    }

    suspend fun enableAhrs(on: Boolean) {
        session.send(Codes.SET_AHRS_STATE, "state:${if (on) 1 else 0};")
    }

    /** Jog one axis. Codes 513–516 (GIMBAL_HADJ_SPEED..GIMBAL_VADJ_ANGLE). */
    suspend fun jog(code: Int, durationMs: Int = 500) {
        require(code in Codes.GIMBAL_HADJ_SPEED..Codes.GIMBAL_VADJ_ANGLE) { "not a jog code: $code" }
        session.send(code, "time:$durationMs;")
    }

    companion object {
        const val EMPTY = EMPTY_CONTENT
    }
}

/** Common-main 4-decimal fixed formatting (String.format is JVM-only). */
private fun Double.format4(): String {
    val neg = this < 0
    val abs = if (neg) -this else this
    val scaled = (abs * 10000.0 + 0.5).toLong()
    val whole = scaled / 10000
    val frac = (scaled % 10000).toString().padStart(4, '0')
    return (if (neg) "-" else "") + "$whole.$frac"
}
