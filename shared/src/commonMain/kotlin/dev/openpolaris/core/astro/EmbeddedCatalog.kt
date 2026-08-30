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
     * Parse the bundled catalogue JSON. The resource lookup is delegated
     * to a platform-specific reader injected via [reader]; the default
     * null reader lets tests inject a string directly via [parse].
     */
    fun parse(text: String): Catalog = Catalog.parse(text)

    /**
     * Convenience for callers that already have the JSON text in hand
     * (e.g. tests, or platform code that knows how to read resources
     * natively). This wrapper exists so the call-site stays symmetric
     * with the bundled version above.
     */
    fun fromJson(jsonText: String): Catalog = Catalog.parse(jsonText)
}
