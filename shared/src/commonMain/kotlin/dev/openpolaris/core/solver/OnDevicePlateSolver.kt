package dev.openpolaris.core.solver

import dev.openpolaris.core.astro.AstroMath

/**
 * On-device plate-solver: pyramid star-pattern match against a [StarCatalog].
 *
 * Pure common-main, no network. Default v1 implementation behind [PlateSolver].
 *
 * The solve has two phases:
 *  1. **Hint -> search patch.** If [SolveHint] is localized (mount reported an
 *     az/alt + observer has lat/lng/jd), convert the hint az/alt to RA/Dec
 *     and call [StarCatalog.starsWithin] with a [searchRadiusDeg] window.
 *     Otherwise fall back to the full catalog.
 *  2. **Pyramid match.** Run [PyramidMatcher] over the search patch. If it
 *     converges with sufficient confidence, return a [SolveResult].
 */
class OnDevicePlateSolver(
    private val catalog: StarCatalog,
    private val matcher: PyramidMatcher = PyramidMatcher(),
    private val searchRadiusDeg: Double = 5.0,
    private val minConfidence: Double = 0.5,
) : PlateSolver {

    override suspend fun solve(
        detections: List<StarDetection>,
        frameWidth: Int,
        frameHeight: Int,
        hint: SolveHint?,
    ): SolveResult? {
        require(detections.size >= 3) { "need at least 3 detections, got ${detections.size}" }

        val candidates: List<CatalogStar> = if (hint != null && hint.isLocalized) {
            val (az, alt) = hint.azAltDeg!!
            val radec = AstroMath.toEquatorialAt(
                azDeg = az,
                altDeg = alt,
                latDeg = hint.latDeg!!,
                lngEastDeg = hint.lngEastDeg!!,
                jd = hint.jdUtc!!,
            )
            val cands = catalog.starsWithin(radec.raDeg, radec.decDeg, searchRadiusDeg)
            cands
        } else {
            when (catalog) {
                is InMemoryCatalog -> catalog.stars
                else -> {
                    val out = ArrayList<CatalogStar>(catalog.size)
                    var ra = 0.0
                    while (ra < 360.0) {
                        var dec = -60.0
                        while (dec <= 60.0) {
                            out.addAll(catalog.starsWithin(ra, dec, 30.0))
                            dec += 30.0
                        }
                        ra += 30.0
                    }
                    out
                }
            }
        }

        if (candidates.size < 3) {
            return null
        }

        val match = matcher.match(
            detections = detections,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            candidates = candidates,
        )
        if (match == null) {
            return null
        }

        if (match.confidence < minConfidence) {
            return null
        }

        return SolveResult(
            raDeg = match.centerRaDeg,
            decDeg = match.centerDecDeg,
            confidence = match.confidence,
            matchedStars = match.matchedStars,
            timestampMs = System.currentTimeMillis(),
        )
    }
}
