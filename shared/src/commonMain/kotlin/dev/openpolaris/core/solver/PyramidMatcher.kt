package dev.openpolaris.core.solver

import dev.openpolaris.core.astro.AstroMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3-star "pyramid" geometric matcher.
 *
 * Given a small set of detected stars (image-plane pixels) and a candidate
 * sky patch from the catalog, find the affine transformation that maps the
 * detected triangle onto the catalog triangle, then verify the
 * transformation by predicting positions of the remaining detected stars
 * and checking against the catalog.
 *
 * Why a 3-star triangle (not a 4-star pyramid)?
 *
 * The classical 4-star pattern-matching (e.g. astrometry.net) picks all
 * C(n,4) quadruples and hashes their 6 angular distances into a lookup
 * table. That's bullet-proof for blind solves on 100+ stars, but for our
 * v1 case (5-20 bright stars + a mount-side hint narrowing the catalog
 * patch to a few hundred entries), a 3-star triangle is:
 *   - 3 angular distances instead of 6 (smaller hash)
 *   - C(n,3) triples instead of C(n,4) (fewer combinations — 56 vs 1821
 *     for 10 stars)
 *   - affine-fit determined from a single triangle (4-star needs 6 dof
 *     and a non-degenerate quadruple, so 4-star isn't strictly better
 *     for our data sizes)
 *
 * The full 4-star pattern index lands in Stream 2.4.1 if the v1 proves
 * too eager on false matches.
 *
 * **Algorithm**:
 *   1. Pick the brightest 3 detections → triangle `D = {d0, d1, d2}`.
 *   2. For each triangle in the catalog within the hint patch:
 *      compute the same 3-edge-length invariant and sort. If the
 *      detected triangle's invariant matches a catalog triangle's
 *      invariant (within a tolerance), call it a *candidate*.
 *   3. For each candidate, compute the affine transform (D → C) and
 *      predict the position of the 4th, 5th, ... detection. Count how
 *      many predictions are within a tolerance of a catalog star.
 *   4. The match with the highest count wins; confidence = matched/total.
 */
class PyramidMatcher(
    private val angularToleranceDeg: Double = 0.05,
    private val pixelTolerance: Double = 2.0,
) {
    init {
        require(angularToleranceDeg > 0.0) { "tolerance must be positive" }
        require(pixelTolerance > 0.0) { "pixel tolerance must be positive" }
    }

    /**
     * Run a pyramid match.
     *
     * @param detections Detected stars in pixel coordinates, with `flux`
     *   in any units where higher = brighter. Must contain at least
     *   3 detections.
     * @param frameWidth Frame width in pixels (used for normalization).
     * @param frameHeight Frame height in pixels.
     * @param candidates Catalog stars within the hint patch. The matcher
     *   builds all C(k,3) triangles where k is `candidates.size`. For
     *   k > 50 this gets expensive; the caller is expected to pass a
     *   small patch (the solver's hint is the gating factor).
     * @return The best [PyramidMatch] found, or `null` if no candidate
     *   produced a confident enough match. "Confident enough" means at
     *   least 3 detections mapped onto catalog stars (the 3 in the
     *   seed triangle alone).
     */
    fun match(
        detections: List<StarDetection>,
        frameWidth: Int,
        frameHeight: Int,
        candidates: List<CatalogStar>,
    ): PyramidMatch? {
        require(detections.size >= 3) { "need at least 3 detections, got ${detections.size}" }
        require(frameWidth > 0 && frameHeight > 0) { "frame dims must be positive" }
        if (candidates.size < 3) {
            return null
        }

        // Sort detections by flux, take the brightest 6 (cheap upper bound
        // for the search; the kdtree / pyramid-indexed version will be
        // smarter about which subset to try).
        val bright = detections.sortedByDescending { it.flux }.take(6)

        // Build the seed triangle invariant: sorted edge lengths in pixels.
        val seedTriangle = triangle(bright[0], bright[1], bright[2])
        val seedInv = sortedEdgeLengths(seedTriangle)

        // Try every catalog triangle within `candidates`.
        var best: PyramidMatch? = null
        for (i in candidates.indices) {
            for (j in i + 1 until candidates.size) {
                for (k in j + 1 until candidates.size) {
                    val catTri = catalogTriangle(
                        candidates[i], candidates[j], candidates[k],
                        frameWidth.toDouble(), frameHeight.toDouble(),
                    )
                    val catInv = sortedEdgeLengths(catTri)

                    if (!invariantsMatch(seedInv, catInv, 0.05 * mean(seedInv))) continue

                    // Got a candidate. Map detection → catalog via the
                    // affine transform implied by the triangle pair.
                    val mapping = mapTriangle(seedTriangle, catTri)
                    if (mapping == null) continue

                    // Count supporting stars beyond the seed 3. First
                    // project every candidate to pixel coordinates once
                    // using the same gnomonic projection the seed/cat
                    // triangles were built with.
                    val frameW = frameWidth.toDouble()
                    val frameH = frameHeight.toDouble()
                    val projectionCentre = centroid(
                        candidates[i], candidates[j], candidates[k],
                    )
                    val projected = candidates.map { project(it, projectionCentre, frameW, frameH) }
                    val remainingDetections = bright.drop(3)
                    var supporting = 3
                    for (det in remainingDetections) {
                        val predicted = mapping.apply(det.x, det.y)
                        val nearest = nearestProjectedDistance(predicted, projected)
                        if (nearest <= pixelTolerance) {
                            supporting++
                        }
                    }

                    // Compute the centre as the mean of the seed triangle
                    // in catalog space.
                    val centerRa = (candidates[i].raDeg + candidates[j].raDeg + candidates[k].raDeg) / 3.0
                    val centerDec = (candidates[i].decDeg + candidates[j].decDeg + candidates[k].decDeg) / 3.0

                    val confidence = supporting.toDouble() / bright.size
                    val match_ = PyramidMatch(
                        centerRaDeg = AstroMath.normalizeDeg(centerRa),
                        centerDecDeg = centerDec.coerceIn(-90.0, 90.0),
                        matchedStars = supporting,
                        confidence = confidence,
                    )
                    if (best == null || match_.matchedStars > best.matchedStars) {
                        best = match_
                    }
                }
            }
        }
        return best
    }

    // --- triangle math -----------------------------------------------------

    private data class Triangle(val a: Point, val b: Point, val c: Point)
    private fun triangle(d0: StarDetection, d1: StarDetection, d2: StarDetection): Triangle =
        Triangle(Point(d0.x, d0.y), Point(d1.x, d1.y), Point(d2.x, d2.y))

    /**
     * Build a catalog triangle in **pixel-equivalent** coordinates. We
     * project the catalog triangle onto the frame using a gnomonic
     * projection centred on the hint's (az, alt), scaled to the frame
     * dimensions. This is a simplification — the real solver would use
     * the camera intrinsics — but it's enough for v1 because the hint
     * patch is small (a few degrees) and the camera distortion is
     * modest (the alpaca driver ignores it too).
     */
    private fun catalogTriangle(
        s0: CatalogStar, s1: CatalogStar, s2: CatalogStar,
        frameW: Double, frameH: Double,
    ): Triangle {
        val centre = centroid(s0, s1, s2)
        val proj0 = project(s0, centre, frameW, frameH)
        val proj1 = project(s1, centre, frameW, frameH)
        val proj2 = project(s2, centre, frameW, frameH)
        return Triangle(proj0, proj1, proj2)
    }

    private fun centroid(s0: CatalogStar, s1: CatalogStar, s2: CatalogStar): CatalogStar =
        CatalogStar(
            raDeg = (s0.raDeg + s1.raDeg + s2.raDeg) / 3.0,
            decDeg = (s0.decDeg + s1.decDeg + s2.decDeg) / 3.0,
            mag = 0.0,
        )

    /**
     * Gnomonic projection of a star onto the tangent plane at `centre`,
     * then scaled to pixel coordinates with the frame centred at (w/2, h/2).
     */
    private fun project(s: CatalogStar, centre: CatalogStar, frameW: Double, frameH: Double): Point {
        val ra0 = Math.toRadians(centre.raDeg)
        val dec0 = Math.toRadians(centre.decDeg)
        val ra = Math.toRadians(s.raDeg)
        val dec = Math.toRadians(s.decDeg)

        val cosC = sin(dec0) * sin(dec) + cos(dec0) * cos(dec) * cos(ra - ra0)
        if (cosC < 1e-6) return Point(frameW / 2.0, frameH / 2.0) // behind the tangent plane
        val x = (cos(dec) * sin(ra - ra0)) / cosC
        val y = (cos(dec0) * sin(dec) - sin(dec0) * cos(dec) * cos(ra - ra0)) / cosC
        val degPerRad = 180.0 / PI

        // 1° ≈ `degPerPixel` pixels — calibrated so a 5° FOV fills the frame.
        val degPerPixel = 5.0 / minOf(frameW, frameH) / 2.0
        val px = frameW / 2.0 + x * degPerRad / degPerPixel
        val py = frameH / 2.0 - y * degPerRad / degPerPixel
        return Point(px, py)
    }

    private fun edgeLengths(t: Triangle): DoubleArray {
        val d01 = distance(t.a, t.b)
        val d12 = distance(t.b, t.c)
        val d20 = distance(t.c, t.a)
        return doubleArrayOf(d01, d12, d20)
    }

    private fun sortedEdgeLengths(t: Triangle): DoubleArray =
        edgeLengths(t).also { it.sort() }

    private fun distance(p: Point, q: Point): Double {
        val dx = p.x - q.x
        val dy = p.y - q.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun mean(xs: DoubleArray): Double = xs.sum() / xs.size

    private fun invariantsMatch(a: DoubleArray, b: DoubleArray, tol: Double): Boolean {
        for (i in 0..2) if (abs(a[i] - b[i]) > tol) return false
        return true
    }

    // --- affine mapping ----------------------------------------------------

    /**
     * Compute the affine transform that maps `seed` onto `target` via the
     * 3 corresponding vertex pairs. Returns `null` if the source triangle
     * is degenerate.
     */
    private fun mapTriangle(seed: Triangle, target: Triangle): Affine2D? {
        val det = ((seed.b.x - seed.a.x) * (seed.c.y - seed.a.y) -
            (seed.c.x - seed.a.x) * (seed.b.y - seed.a.y))
        if (abs(det) < 1e-9) return null

        val invDet = 1.0 / det
        val a = ((target.b.x - target.a.x) * (seed.c.y - seed.a.y) -
            (target.c.x - target.a.x) * (seed.b.y - seed.a.y)) * invDet
        val b = -((target.b.x - target.a.x) * (seed.c.x - seed.a.x) -
            (target.c.x - target.a.x) * (seed.b.x - seed.a.x)) * invDet
        val c = (target.b.x - target.a.x) - a * (seed.b.x - seed.a.x) - b * (seed.b.y - seed.a.y) +
            target.a.x
        val d = ((target.b.y - target.a.y) * (seed.c.y - seed.a.y) -
            (target.c.y - target.a.y) * (seed.b.y - seed.a.y)) * invDet
        val e = -((target.b.y - target.a.y) * (seed.c.x - seed.a.x) -
            (target.c.y - target.a.y) * (seed.b.x - seed.a.x)) * invDet
        val f = (target.b.y - target.a.y) - d * (seed.b.x - seed.a.x) - e * (seed.b.y - seed.a.y) +
            target.a.y
        return Affine2D(a, b, c, d, e, f)
    }

    private fun nearestProjectedDistance(predicted: Point, projected: List<Point>): Double {
        var bestD2 = Double.POSITIVE_INFINITY
        for (p in projected) {
            val dx = predicted.x - p.x
            val dy = predicted.y - p.y
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) bestD2 = d2
        }
        return sqrt(bestD2)
    }
}

/**
 * 2D point in pixel space. Lives at file scope (internal) so the
 * top-level [Affine2D] can return it from `apply` without an extra
 * public wrapper type.
 */
internal data class Point(val x: Double, val y: Double)

/**
 * Affine transform on the plane: (x', y') = A(x, y) + (tx, ty), with
 * matrix [[a, b], [d, e]] and translation (c, f).
 */
private data class Affine2D(
    val a: Double, val b: Double, val c: Double,
    val d: Double, val e: Double, val f: Double,
) {
    fun apply(x: Double, y: Double): Point =
        Point(a * x + b * y + c, d * x + e * y + f)
}

/**
 * Result of a successful pyramid match.
 */
data class PyramidMatch(
    val centerRaDeg: Double,
    val centerDecDeg: Double,
    val matchedStars: Int,
    val confidence: Double,
) {
    init {
        require(matchedStars >= 3) { "matchedStars must be >= 3, was $matchedStars" }
        require(confidence in 0.0..1.0) { "confidence out of [0,1]: $confidence" }
    }
}
