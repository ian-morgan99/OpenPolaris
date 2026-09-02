package dev.openpolaris.core.solver

import dev.openpolaris.core.astro.AstroMath
import dev.openpolaris.core.domain.SystemMillis

/**
 * Plate-solver contract for the phone app.
 *
 * A solver takes the **detected image-plane positions** of stars (pixels in
 * the camera frame) plus a **hint** describing where the mount currently
 * thinks it is pointing, and returns the most likely equatorial coordinates
 * (RA/Dec) of the frame centre, or `null` if it cannot converge.
 *
 * The detection → image-plane-coords step is **out of scope for this
 * interface**; it lives in platform-specific code that owns the camera
 * (Android CameraX / Compose Desktop preview). The solver only sees the
 * `StarField` — a list of unit-agnostic pixel positions + relative fluxes.
 *
 * Implementations:
 * - [OnDevicePlateSolver] — kdtree + pyramid, no network, default for v1.
 * - [OffDevicePlateSolver] — REST to nova.astrometry.net, behind a flag.
 *
 * Both implementations are expected to be pure (deterministic given a
 * catalog + detections) and side-effect free; I/O is the caller's problem.
 */
sealed interface PlateSolver {

    /**
     * Solve a star field.
     *
     * @param detections Detected stars in image-plane coordinates, with
     *   pixel positions in a frame of size [frameWidth] x [frameHeight] and
     *   `flux` in arbitrary units (higher = brighter). At least 4 detections
     *   are required; the solver may impose a stricter cap (e.g. top-50 by
     *   flux) to bound work.
     * @param hint Mount's current pointing — typically from `Codes.GET_GIMBAL_POS`
     *   plus the observer site/time. The solver uses this as a search prior:
     *   the answer should be within a few degrees of `hint.azAlt` if the
     *   mount is roughly pointed where it thinks it is. A null hint forces
     *   an unguided solve (slower, may return null on partial fields).
     * @return Best `SolveResult` if the solver converged with confidence
     *   above its own threshold, else `null`. Callers should treat `null`
     *   as a soft failure (try again with more detections or a better hint).
     */
    suspend fun solve(
        detections: List<StarDetection>,
        frameWidth: Int,
        frameHeight: Int,
        hint: SolveHint?,
    ): SolveResult?
}

/**
 * A single detected star, as seen by the camera-side pre-processor.
 *
 * Pixel coordinates use the convention `(x = 0, y = 0)` at the top-left
 * of the frame. The solver converts this to angular coordinates using
 * a simple pinhole model (no distortion correction — that is the job of
 * a future calibration pass; the reference alpaca driver ignores it too).
 */
data class StarDetection(
    val x: Double,
    val y: Double,
    val flux: Double,
)

/**
 * Mount-side hint to bias the solver. All optional — a null field means
 * "unknown" and the solver falls back to a wider search.
 *
 * @param azAltDeg Mount's reported (azimuth, altitude) in degrees. Used to
 *   project the candidate sky patch into image-plane coordinates for the
 *   geometric hash match.
 * @param latDeg Observer latitude, north positive. Required if `jdUtc` is set.
 * @param lngEastDeg Observer longitude, east positive.
 * @param jdUtc Julian date of the observation, UTC.
 */
data class SolveHint(
    val azAltDeg: Pair<Double, Double>?,
    val latDeg: Double?,
    val lngEastDeg: Double?,
    val jdUtc: Double?,
) {
    /** True when the hint carries enough info to localize the sky patch. */
    val isLocalized: Boolean
        get() = azAltDeg != null && latDeg != null && lngEastDeg != null && jdUtc != null

    companion object {
        /** Build a hint from the standard observer package, no az/alt. */
        fun atTime(latDeg: Double, lngEastDeg: Double, jdUtc: Double): SolveHint =
            SolveHint(azAltDeg = null, latDeg = latDeg, lngEastDeg = lngEastDeg, jdUtc = jdUtc)
    }
}

/**
 * Result of a successful plate solve.
 *
 * `raDeg` / `decDec` are the equatorial coordinates of the **frame centre**
 * — the projection of the geometric centre of the camera frame onto the
 * celestial sphere, given the orientation implied by the match. This is
 * what the user wants to feed back into [dev.openpolaris.core.domain.GoToController.refine].
 *
 * `confidence` is in `[0, 1]`. 0.0 means "matched but dubious"; 1.0 means
 * "the geometric match was unambiguous". The v1 wiring treats anything
 * `>= 0.6` as "good enough to refine"; the caller may override.
 *
 * `matchedStars` is the number of detections that were mapped onto catalog
 * stars. Useful for diagnostics and for deciding whether the match is
 * trustworthy on a sparse field.
 *
 * `timestampMs` is the wall-clock time the solve completed, in ms since the
 * Unix epoch. The default of `0L` exists for back-compat with test fixtures
 * and for callers that just want the projection math (the VR renderer, for
 * example, which reads `ageMs` from a separate Intent extra). **Production
 * plate-solve call sites must stamp this** so that downstream UI can
 * honestly report how old the solve is.
 */
data class SolveResult(
    val raDeg: Double,
    val decDeg: Double,
    val confidence: Double,
    val matchedStars: Int,
    val timestampMs: Long = 0L,
) {
    init {
        require(confidence in 0.0..1.0) { "confidence must be in [0,1], was $confidence" }
        require(matchedStars >= 3) { "matchedStars must be >=3, was $matchedStars" }
        require(timestampMs >= 0L) { "timestampMs must be >=0, was $timestampMs" }
    }

    /** Normalize RA into [0, 360) for storage. */
    val raDegNormalized: Double get() = AstroMath.normalizeDeg(raDeg)

    /** Age of this solve in ms relative to [nowMs] (defaults to wall-clock). */
    fun ageMs(nowMs: Long = currentTimeMillisProvider()): Long = (nowMs - timestampMs).coerceAtLeast(0L)

    companion object {
        /**
         * Indirection for [ageMs] so tests can pin "now" without a
         * `System.currentTimeMillis()` mock. Production callers get
         * wall-clock time.
         */
        internal var currentTimeMillisProvider: () -> Long = { SystemMillis.now() }
    }
}
