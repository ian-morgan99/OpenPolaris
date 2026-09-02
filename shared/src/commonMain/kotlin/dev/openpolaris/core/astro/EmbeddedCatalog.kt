package dev.openpolaris.core.astro

/**
 * Bundled `catalog.json` shipped inside the shared/commonMain resources.
 * Loaded at app start-up; the in-memory [Catalog] is then immutable.
 *
 * For tests, prefer constructing a `Catalog.of(...)` directly so the
 * disk read is not part of the assertion path.
 */
object EmbeddedCatalog {

    /** The relative path inside the multiplatform resources directory. */
    const val RESOURCE_PATH: String = "catalog.json"

    /**
     * The set of catalogue shards shipped with the app, in load order.
     * Later shards may override earlier ones on a duplicate designation
     * (see [Catalog.merge]).
     */
    val DEFAULT_SHARDS: List<String> = listOf(
        "catalog.json",
        "stars.json",
        "ngc.json",
    )

    /**
     * Parse the bundled catalogue JSON. The resource lookup is delegated
     * to a platform-specific reader injected via [reader]; the default
     * null reader lets tests inject a string directly via [parse].
     */
    fun parse(text: String): Catalog = CatalogParser.parse(text)

    /**
     * Convenience for callers that already have the JSON text in hand
     * (e.g. tests, or platform code that knows how to read resources
     * natively). This wrapper exists so the call-site stays symmetric
     * with the bundled version above.
     */
    fun fromJson(jsonText: String): Catalog = CatalogParser.parse(jsonText)

    /**
     * Combine multiple embedded shards into one [Catalog]. The
     * [reader] function is called once per shard and must return the
     * raw JSON text, or null if the shard is missing (e.g. on a
     * stripped-down install). Missing shards are silently skipped.
     */
    fun loadFrom(
        paths: List<String> = DEFAULT_SHARDS,
        reader: (String) -> String? = { null },
    ): Catalog {
        val catalogs = paths.mapNotNull { p ->
            val text = reader(p) ?: return@mapNotNull null
            try {
                CatalogParser.parse(text)
            } catch (e: Exception) {
                // Don't blow up the whole app for one bad shard; log and skip.
                null
            }
        }
        return if (catalogs.isEmpty()) {
            Catalog(version = 0, objects = emptyList())
        } else {
            Catalog(
                version = catalogs.maxOf { it.version },
                objects = catalogs.flatMap { it.objects },
            )
        }
    }
}
