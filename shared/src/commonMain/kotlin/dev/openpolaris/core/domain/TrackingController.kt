package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.CommandTable
import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.EMPTY_CONTENT

/**
 * High-level tracking control. Encapsulates the half-speed quirk:
 * SET_TRACK_HALF_SPEED (536) payload value is INVERTED on the wire —
 * sending `1` turns half-speed OFF, `0` turns it ON.
 */
class TrackingController(private val session: MountSession) {

    /**
     * Start tracking. Firmware format string for 531 is `state:%d;speed:%d;`
     * (recovered from polestar_app). The semantic range of `speed` is not
     * hardware-verified; the Benro app exposes star/sun/moon rates which map
     * to small integer indices. When [speed] is null we send the minimal
     * `state:1;` frame (verified working); pass an explicit index to include
     * the speed field.
     */
    suspend fun start(speed: Int? = null) {
        val payload = if (speed != null) "state:1;speed:$speed;" else "state:1;"
        session.send(Codes.SET_TRACK_AU_STATE, payload)
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
        session.send(Codes.SET_GOTO_AU_STATE, CommandTable.GOTO_AZ_ALT.payload(azimuthDeg to altitudeDeg))
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

/** Common-main 2-decimal fixed formatting for UI display. */
fun Double.format2(): String {
    val neg = this < 0
    val abs = if (neg) -this else this
    val scaled = (abs * 100.0 + 0.5).toLong()
    val whole = scaled / 100
    val frac = (scaled % 100).toString().padStart(2, '0')
    return (if (neg) "-" else "") + "$whole.$frac"
}
