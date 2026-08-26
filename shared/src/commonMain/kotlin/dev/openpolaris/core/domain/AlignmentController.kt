package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.CommandTable

/**
 * Multi-star alignment flow (code 530). Each step sends the mount the
 * alt/az of a chosen alignment star plus observer location; the firmware
 * builds a pointing model from `num` stars.
 *
 * Typical flow:
 * 1. Pick 2–3 bright stars spread across the sky, compute their alt/az
 *    with [AstroMath.toHorizontalAt] using current time and location.
 * 2. Center each star visually (jog), then call [submitStar] in order.
 * 3. After the final star, gotos use the corrected model.
 */
class AlignmentController(private val session: MountSession) {

    /** Stars submitted so far (0-based index for the next is [starCount]). */
    var starCount: Int = 0
        private set

    /**
     * Submit one alignment point. The caller supplies the mount's current
     * pointing (yaw/pitch, i.e. az/alt it was centered on) and the
     * observer location; the firmware records star [starIndex].
     */
    suspend fun submitStar(yawDeg: Double, pitchDeg: Double, latDeg: Double, lngEastDeg: Double) {
        val payload = CommandTable.ALIGN_STAR.payload(
            CommandTable.AlignmentStar(yawDeg, pitchDeg, latDeg, lngEastDeg, starCount)
        )
        session.send(CommandTable.ALIGN_STAR.code, payload)
        starCount++
    }

    /** Reset for a fresh alignment run. */
    fun reset() {
        starCount = 0
    }
}
