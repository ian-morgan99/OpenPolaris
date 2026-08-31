package dev.openpolaris.core.protocol

import dev.openpolaris.core.domain.AutoLevelController

/**
 * Shared codec for the Polaris tilt envelope (537 GET reply / 538 push).
 * Format on the wire: `pitch:%f;roll:%f;` (degrees).
 *
 * Both the standalone TILT_GET descriptor and the auto-level controller's
 * push capture use this so the parsing rules stay in one place.
 */
object TiltCodec {
    fun parse(f: ResponseParser.Frame): AutoLevelController.Tilt? {
        val pitch = f.float("pitch")?.toDouble() ?: f.fields["pitch"]?.toDoubleOrNull()
        val roll = f.float("roll")?.toDouble() ?: f.fields["roll"]?.toDoubleOrNull()
        return if (pitch != null && roll != null) AutoLevelController.Tilt(pitch, roll) else null
    }
}
