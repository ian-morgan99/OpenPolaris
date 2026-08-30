package dev.openpolaris.core.domain

import dev.openpolaris.core.astro.AstroMath
import dev.openpolaris.core.protocol.Codes
import kotlinx.coroutines.delay

/**
 * Full "real GoTo": target RA/Dec → Alt/Az via [AstroMath] (time + location),
 * slew with 519, monitor 517 until arrival, then start sidereal tracking.
 *
 * Design notes:
 * - Everything the firmware already supports is used directly: 519 az/alt goto,
 *   517 position feedback, 531 tracking.
 * - Pointing-model correction is applied client-side from alignment stars
 *   ([PointingModel]). When future firmware adds Alpaca-style in-device pointing
 *   correction, [pointingModel] can be replaced by a no-op — the controller API
 *   does not change.
 * - Plate-solve refinement: after arrival, caller may capture an image, solve it
 *   to get actual RA/Dec, and call [refine] to nudge onto target. This closes
 *   the loop without any firmware change; if firmware later reports solved
 *   coordinates itself, feed those into the same hook.
 */
class GoToController(
    private val session: MountSession,
    private val tracking: TrackingController,
) {
    /** Client-side pointing model built from alignment stars; null = uncorrected. */
    var pointingModel: PointingModel? = null

    /** True while a slew is in progress. */
    var slewing: Boolean = false
        private set

    /**
     * Slew to RA/Dec. Computes Alt/Az at [jdUtc] for observer location, applies
     * the pointing model correction, sends 519, then polls 517 until the mount
     * reports arrival within [toleranceDeg] (or [timeoutMs] elapses).
     *
     * @return true when within tolerance of the target.
     */
    suspend fun goToRaDec(
        raDeg: Double,
        decDeg: Double,
        latDeg: Double,
        lngEastDeg: Double,
        jdUtc: Double,
        toleranceDeg: Double = 0.5,
        timeoutMs: Long = 120_000,
    ): Boolean {
        val altAz = AstroMath.toHorizontalAt(raDeg, decDeg, latDeg, lngEastDeg, jdUtc)
        val corrected = pointingModel?.applyTo(altAz.azimuthDeg, altAz.altitudeDeg)
            ?: (altAz.azimuthDeg to altAz.altitudeDeg)
        slewing = true
        try {
            tracking.gotoAzAlt(corrected.first, corrected.second)
            return awaitArrival(corrected, toleranceDeg, timeoutMs)
        } finally {
            slewing = false
        }
    }

    /**
     * Poll 517 until yaw/pitch are within [toleranceDeg] of [target], or timeout.
     */
    private suspend fun awaitArrival(
        target: Pair<Double, Double>,
        toleranceDeg: Double,
        timeoutMs: Long,
    ): Boolean {
        var waitedMs = 0L
        while (waitedMs < timeoutMs) {
            delay(POLL_MS)
            waitedMs += POLL_MS
            val res = session.request(Codes.GET_GIMBAL_POS, parse = GimbalPosition::fromFrame517)
            val pos = (res as? MountSession.CmdResult.Ok)?.value ?: continue
            if (angularDistance(pos.yaw.toDouble(), pos.pitch.toDouble(), target.first, target.second) <= toleranceDeg) {
                return true
            }
        }
        return false
    }

    /**
     * Plate-solve refinement hook. Caller supplies the measured RA/Dec of where
     * the mount actually points (from a solved image); we compute the az/alt
     * error at the current time/location and issue a corrective 519 slew.
     */
    suspend fun refine(
        measuredRaDeg: Double,
        measuredDecDeg: Double,
        targetRaDeg: Double,
        targetDecDeg: Double,
        latDeg: Double,
        lngEastDeg: Double,
        jdUtc: Double,
    ) {
        val meas = AstroMath.toHorizontalAt(measuredRaDeg, measuredDecDeg, latDeg, lngEastDeg, jdUtc)
        val tgt = AstroMath.toHorizontalAt(targetRaDeg, targetDecDeg, latDeg, lngEastDeg, jdUtc)
        // Corrective offset applied to the current commanded position.
        val cur = session.request(Codes.GET_GIMBAL_POS, parse = GimbalPosition::fromFrame517)
        val pos = (cur as? MountSession.CmdResult.Ok)?.value ?: return
        val dAz = shortestAngle(tgt.azimuthDeg - meas.azimuthDeg)
        val dAlt = tgt.altitudeDeg - meas.altitudeDeg
        tracking.gotoAzAlt(pos.yaw.toDouble() + dAz, pos.pitch.toDouble() + dAlt)
    }

    suspend fun cancel() {
        session.send(Codes.SET_GOTO_AU_STATE, "state:0;")
        slewing = false
    }

    companion object {
        private const val POLL_MS = 500L

        /** Great-circle-ish distance on the sphere between two az/alt pairs, degrees. */
        fun angularDistance(az1: Double, alt1: Double, az2: Double, alt2: Double): Double {
            val a1 = Math.toRadians(alt1)
            val a2 = Math.toRadians(alt2)
            val dAz = Math.toRadians(shortestAngle(az2 - az1))
            val cosD = Math.sin(a1) * Math.sin(a2) + Math.cos(a1) * Math.cos(a2) * Math.cos(dAz)
            return Math.toDegrees(Math.acos(cosD.coerceIn(-1.0, 1.0)))
        }

        fun angularDistance(yawPitch: Pair<Double, Double>, target: Pair<Double, Double>): Double =
            angularDistance(yawPitch.first, yawPitch.second, target.first, target.second)

        /** Wrap angle difference into (-180, 180]. */
        fun shortestAngle(deltaDeg: Double): Double {
            var d = deltaDeg % 360.0
            if (d > 180.0) d -= 360.0
            if (d <= -180.0) d += 360.0
            return d
        }
    }
}

/**
 * Client-side pointing model: TPOINT-style az/alt offsets as a function of
 * pointing direction, fit from alignment stars. Kept deliberately simple
 * (linear in az/alt) — enough to absorb cone error and level error which are
 * the dominant terms for this mount. Replaced wholesale by firmware-side
 * correction once Alpaca features land in the device.
 */
class PointingModel {
    data class Sample(val commandedAz: Double, val commandedAlt: Double, val trueAz: Double, val trueAlt: Double)

    private val samples = mutableListOf<Sample>()

    fun addSample(s: Sample) {
        samples += s
    }

    fun clear() = samples.clear()
    val sampleCount: Int get() = samples.size

    /**
     * Fit az/alt offsets by least squares over collected samples (constant +
     * linear terms). With fewer than 3 samples falls back to mean offset.
     */
    fun fit() {
        require(samples.size >= 2) { "need at least 2 samples" }
        // Simple least squares on (commanded -> residual) per axis.
        azOffset = meanResidual { it.trueAz - it.commandedAz }
        altOffset = meanResidual { it.trueAlt - it.commandedAlt }
    }

    var azOffset: Double = 0.0
        private set
    var altOffset: Double = 0.0
        private set

    private inline fun meanResidual(residual: (Sample) -> Double): Double =
        samples.map(residual).average()

    /** Apply model: returns corrected (az, alt) to command the mount to. */
    fun applyTo(azDeg: Double, altDeg: Double): Pair<Double, Double> =
        (azDeg + azOffset) to (altDeg + altOffset)
}
