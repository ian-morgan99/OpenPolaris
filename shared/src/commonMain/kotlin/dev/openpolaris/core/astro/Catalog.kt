package dev.openpolaris.core.astro

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A single astronomical object — star, Messier entry, NGC target, etc.
 *
 * `magnitude` is visual (V-band where the source has it). Magnitudes are
 * stored as `Double?` because some objects (e.g. extended nebulae) are
 * routinely quoted as integrated magnitudes while others are
 * surface-brightness limited.
 */
@Serializable
data class AstroObject(
    val designation: String,
    val name: String? = null,
    val type: ObjectType,
    val raDeg: Double,
    val decDeg: Double,
    val magnitude: Double? = null,
    val constellation: String? = null,
    val sizeArcmin: Double? = null,
    val description: String? = null,
)

/** Catalogue object classification — see ARCHITECTURE.md §3.5. */
@Serializable
enum class ObjectType {
    STAR,
    PLANET,
    GALAXY,
    NEBULA,
    CLUSTER_OPEN,
    CLUSTER_GLOBULAR,
    PLANETARY_NEBULA,
    SUPERNOVA_REMNANT,
    DOUBLE_STAR,
    VARIABLE_STAR,
    COMET,
    OTHER,
}

/**
 * Wire format for an embedded catalogue. The shipped file
 * `commonMain/resources/catalog.json` is parsed at app start-up; the
 * in-memory list is then immutable.
 */
@Serializable
data class Catalog(
    val version: Int,
    val objects: List<AstroObject>,
) {
    fun findByDesignation(designation: String): AstroObject? =
        objects.firstOrNull { it.designation.equals(designation, ignoreCase = true) }

    fun findByName(name: String): AstroObject? =
        objects.firstOrNull { it.name?.equals(name, ignoreCase = true) == true }

    fun searchByPrefix(prefix: String, limit: Int = 20): List<AstroObject> {
        val p = prefix.trim().lowercase()
        if (p.isEmpty()) return emptyList()
        return objects.asSequence()
            .filter { obj ->
                obj.designation.lowercase().startsWith(p) ||
                    (obj.name?.lowercase()?.startsWith(p) == true)
            }
            .take(limit)
            .toList()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Parse JSON text into a [Catalog]. */
        fun parse(text: String): Catalog = json.decodeFromString(Catalog.serializer(), text)

        /** Convenience loader for in-memory fixtures (used in tests). */
        fun of(objects: List<AstroObject>, version: Int = 1): Catalog =
            Catalog(version, objects)

        /**
         * Combine multiple catalogs into one. Later catalogs win on
         * duplicate `designation` (so the bundled core catalog can be
         * overridden by an up-to-date NGC/IC supplement, for example).
         * Version of the first catalog is preserved.
         */
        fun merge(vararg catalogs: Catalog): Catalog {
            require(catalogs.isNotEmpty()) { "merge requires at least one catalog" }
            val seen = LinkedHashMap<String, AstroObject>()
            for (c in catalogs) {
                for (o in c.objects) {
                    seen[o.designation] = o
                }
            }
            return Catalog(catalogs[0].version, seen.values.toList())
        }
    }
}
