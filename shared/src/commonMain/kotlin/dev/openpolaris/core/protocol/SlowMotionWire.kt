package dev.openpolaris.core.protocol

import dev.openpolaris.core.domain.RateAxis
import dev.openpolaris.core.domain.SlowMotionPoint
import dev.openpolaris.core.domain.SlowScheduleCommand
import dev.openpolaris.core.domain.SlowScheduleKind

/**
 * Host-only formatter for the source-aligned Benro Connect SLOW controls.
 *
 * This deliberately stops at a pure frame. It does not own a socket, claim a
 * live capability, or decide whether a target firmware is safe to command.
 * The live adapter remains gated until acknowledgement, watchdog, signs,
 * disconnect and physical-stop evidence exist for the exact firmware.
 */
data class SlowMotionWireFrame(
    val code: Int,
    val subtype: Int,
    val payload: String,
    val bytes: ByteArray,
)

object SlowMotionWire {
    fun encode(command: SlowScheduleCommand): SlowMotionWireFrame {
        val point = command.point
            ?: throw IllegalArgumentException("SLOW wire command requires a retained point")
        validate(point)
        val code = when (command.axis) {
            RateAxis.M1_PAN -> Codes.YAW_KEY
            RateAxis.M2_TILT -> Codes.PITCH_KEY
            RateAxis.M3_ROTATION -> Codes.ROLL_KEY
        }
        val state = when (command.kind) {
            SlowScheduleKind.SLOW -> point.state
            SlowScheduleKind.STOP -> 0
        }
        val payload = "key:${point.key};state:$state;level:${point.level};"
        val bytes = commandWithSubtype(code) { putRaw(payload) }
        return SlowMotionWireFrame(
            code = code,
            subtype = Codes.subtypeFor(code),
            payload = payload,
            bytes = bytes,
        )
    }

    /** Rejects an invalid retained point before any future transport can use it. */
    private fun validate(point: SlowMotionPoint) {
        require(point.key == 0 || point.key == 1) { "SLOW wire key must be 0 or 1" }
        require(point.state == 1 || point.state == 2) { "SLOW wire state must be 1 or 2" }
        require(point.level in 1..5) { "SLOW wire level must be 1..5" }
    }
}
