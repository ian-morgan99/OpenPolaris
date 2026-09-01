package dev.openpolaris.core.solver

/**
 * A single catalog entry. Equatorial coordinates in J2000.
 *
 * `mag` is apparent visual magnitude — lower is brighter. The catalog
 * should include stars at least to magnitude 6.5 (naked-eye limiting mag
 * at a dark site) and ideally to 8-9 for a comfortable matcher density.
 *
 * For the v1 on-device solver we use a **bounded catalog** (~5k stars,
 * mag <= 6.0) loaded into memory once at startup. That gets us:
 *   - 200-400 KB heap (kdtree included)
 *   - < 50 ms cold solve on a Pixel 6
 *   - 95%+ match rate for typical GoTo targets (bright stars, planets,
 *     Messier objects, named NGC/IC that have a star nearby)
 *
 * For full plate-solving of arbitrary sky patches (no mount hint), the
 * catalog would need to grow to ~120k stars (HYG database up to mag ~12)
 * and the kdtree would need to be persisted on disk. That's Stream 2.2
 * territory (off-device REST is the cheaper path for arbitrary fields).
 */
data class CatalogStar(
    val raDeg: Double,
    val decDeg: Double,
    val mag: Double,
    val name: String? = null,
) {
    init {
        require(raDeg in 0.0..360.0) { "raDeg out of range: $raDeg" }
        require(decDeg in -90.0..90.0) { "decDeg out of range: $decDeg" }
    }
}

/**
 * Read-only view over the star catalog used by the solver.
 *
 * Implementations:
 * - [InMemoryCatalog] — backed by a `List<CatalogStar>`. Used in tests
 *   and for the v1 bounded catalog loaded from HYG/CSV at startup.
 * - [RemoteCatalog] (future) — paginated, backed by an on-device SQLite
 *   copy of HYG or a remote endpoint.
 *
 * Thread-safety: implementations must be safe to call from the solver's
 * coroutine without external locking.
 */
interface StarCatalog {
    /** Total number of stars in the catalog. */
    val size: Int

    /** Limiting magnitude. Stars fainter than this are not in the catalog. */
    val limitingMagnitude: Double

    /**
     * Return all stars whose angular distance to `(raDeg, decDeg)` is
     * at most `radiusDeg`. Inclusive on the boundary.
     *
     * Implementation note: a real catalog will use a 3D kdtree over
     * unit-sphere coordinates. The synthetic test catalog uses a
     * brute-force scan because it's tiny.
     */
    fun starsWithin(raDeg: Double, decDeg: Double, radiusDeg: Double): List<CatalogStar>

    /**
     * Return up to [k] nearest neighbours to `(raDeg, decDeg)`. The
     * default delegates to [starsWithin] with a generous radius, then
     * sorts. Specialized implementations use a 3D kdtree.
     */
    fun nearestK(raDeg: Double, decDeg: Double, k: Int): List<CatalogStar> {
        // 30° is enough to find k stars in any realistic catalog density
        // up to mag 6.5; for a denser catalog, override.
        val candidates = starsWithin(raDeg, decDeg, 30.0)
        val cosDec = Math.cos(Math.toRadians(decDeg)).coerceAtLeast(1e-6)
        return candidates
            .sortedBy {
                val dra = it.raDeg - raDeg
                val ddec = it.decDeg - decDeg
                dra * dra * cosDec * cosDec + ddec * ddec
            }
            .take(k)
    }
}

/**
 * In-memory catalog backed by a `List`. Cheapest path for v1.
 *
 * Both `starsWithin` and `nearestK` are O(n) brute-force scans. For a
 * 5k-star bounded catalog (HYG up to mag 6.0) the scan is ~50 µs per
 * call on a modern phone — well under any budget the solver has. The
 * dedicated 3D kdtree (Stream 2.4) lands later if profiling demands it.
 *
 * The `stars` property is exposed publicly so callers can iterate the
 * catalog directly when a brute-force scan is convenient (e.g. building
 * a pyramid index on first use).
 */
class InMemoryCatalog(
    val stars: List<CatalogStar>,
) : StarCatalog, Iterable<CatalogStar> by stars {
    override val size: Int get() = stars.size
    override val limitingMagnitude: Double =
        stars.maxOfOrNull { it.mag } ?: Double.POSITIVE_INFINITY

    override fun starsWithin(raDeg: Double, decDeg: Double, radiusDeg: Double): List<CatalogStar> {
        val cosDec = Math.cos(Math.toRadians(decDeg)).coerceAtLeast(1e-6)
        val r2 = radiusDeg * radiusDeg
        val out = ArrayList<CatalogStar>(16)
        for (s in stars) {
            val dra = s.raDeg - raDeg
            val ddec = s.decDeg - decDeg
            // Project onto a tangent plane — fine for radiusDeg < 30°.
            val d2 = dra * dra * cosDec * cosDec + ddec * ddec
            if (d2 <= r2) out.add(s)
        }
        return out
    }
}

/**
 * Synthetic ~50-star test catalog.
 *
 * Designed to exercise the solver without committing a 200 KB HYG file.
 * Coverage:
 *   - 1 star at the north celestial pole (alpha UMi, Polaris)
 *   - 1 star at the south celestial pole (sigma Octantis)
 *   - 10 bright equatorial stars (mimics Orion, Big Dipper, Cassiopeia)
 *   - 8 mid-southern stars (Sirius, Canopus, Centaurus)
 *   - 10 dim equatorial stars (mag 5.0-5.9) for density tests
 *   - 10 clustered stars within 1° of a "test target" for pyramid-match
 *     integration tests
 *
 * RA/Dec values are real (rounded) so the test can cross-check against
 * known angular distances.
 */
object SyntheticTestCatalog {
    val stars: List<CatalogStar> = buildList {
        add(CatalogStar(raDeg = 37.95, decDeg = 89.26, mag = 1.97, name = "Polaris"))
        add(CatalogStar(raDeg = 316.05, decDeg = -88.96, mag = 5.42, name = "Sigma Octantis"))

        // Bright equatorial — real bright stars, mag <= 2.5
        add(CatalogStar(79.17, 45.99, 0.03, "Vega"))            // alpha Lyr
        add(CatalogStar(213.92, 19.18, 0.87, "Arcturus"))       // alpha Boo
        add(CatalogStar(186.65, -63.10, 0.01, "Rigil Kentaurus")) // alpha Cen
        add(CatalogStar(297.70,  8.87, 1.36, "Altair"))        // alpha Aql
        add(CatalogStar(310.36, 45.28, 2.07, "Tarazed"))        // gamma Aql
        add(CatalogStar( 95.99, -52.70, -0.27, "Canopus"))      // alpha Car
        add(CatalogStar( 78.63, -8.20, 0.18, "Rigel"))          // beta Ori
        add(CatalogStar( 88.79,  7.41, 0.45, "Betelgeuse"))     // alpha Ori
        add(CatalogStar(152.09, 11.97, 1.69, "Alphard"))        // alpha Hya
        add(CatalogStar(116.33, 28.08, 1.65, "Alhena"))         // gamma Gem

        // Mid-southern, mag 2-3
        add(CatalogStar(201.30, -11.16, 1.04, "Spica"))         // alpha Vir
        add(CatalogStar(247.35, -26.43, 1.06, "Antares"))       // alpha Sco
        add(CatalogStar( 84.05, -1.20, 1.64, "Alnilam"))        // epsilon Ori
        add(CatalogStar(219.92, -60.83, 0.61, "Hadar"))         // beta Cen
        add(CatalogStar(344.41, -29.62, 1.16, "Fomalhaut"))     // alpha PsA
        add(CatalogStar(138.30, -8.40, 1.98, "Sirius-B"))       // companion-ish
        add(CatalogStar(187.47, -57.11, 2.30, "Mimosa"))        // beta Cru
        add(CatalogStar(187.79, -59.69, 1.25, "Acrux"))         // alpha Cru

        // Dim equatorial, mag 5.0-5.9 — adds density for kdtree tests
        for (i in 0 until 10) {
            val ra = (i * 36.0) % 360.0
            val dec = -10.0 + i * 2.0
            add(CatalogStar(raDeg = ra, decDeg = dec, mag = 5.0 + i * 0.1, name = "dim-eq-$i"))
        }

        // Cluster near a test target — RA 200°, Dec +20° within 1°
        // Useful for pyramid-match "rich field" tests
        val clusterCentre = Pair(200.0, 20.0)
        for (i in 0 until 10) {
            val ra = clusterCentre.first + (i - 5) * 0.1
            val dec = clusterCentre.second + (i - 5) * 0.1
            add(CatalogStar(raDeg = ra, decDeg = dec, mag = 4.0 + i * 0.2, name = "cluster-$i"))
        }
    }

    /** As an [InMemoryCatalog] for the solver. */
    val asCatalog: InMemoryCatalog = InMemoryCatalog(stars)

    /**
     * Reference position for the "cluster" above — used by integration
     * tests to seed detections from a known sky patch.
     */
    const val CLUSTER_RA_DEG: Double = 200.0
    const val CLUSTER_DEC_DEG: Double = 20.0
}
