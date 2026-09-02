package dev.openpolaris.core.astro

/**
 * Helper accessors over a [Catalog]. Kept as top-level extension functions
 * rather than members of [Catalog] for compositional reasons — callers
 * may want to attach additional lookup strategies (fuzzy match, alias
 * resolution, plate-solver hints, etc.) without bloating [Catalog]'s
 * surface.
 */

fun Catalog.findByDesignation(designation: String): AstroObject =
    objects.firstOrNull { it.designation == designation }
        ?: AstroObject(designation = designation)

fun Catalog.findByName(name: String): AstroObject =
    objects.firstOrNull { it.name == name }
        ?: AstroObject(designation = name, name = name)

fun Catalog.searchByPrefix(prefix: String): List<AstroObject> =
    objects.filter {
        it.designation.startsWith(prefix) || it.name.startsWith(prefix)
    }
