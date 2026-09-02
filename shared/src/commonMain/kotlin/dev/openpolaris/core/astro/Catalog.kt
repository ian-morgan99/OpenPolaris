package dev.openpolaris.core.astro

class AstroObject(
    val designation: String,
    val name: String = "",
    val type: ObjectType = ObjectType.OTHER,
    val raDeg: Double = 0.0,
    val decDeg: Double = 0.0,
    val magnitude: Double = Double.NaN,
    val constellation: String = "",
    val sizeArcmin: Double = 0.0,
    val description: String = "",
)

enum class ObjectType {
    STAR, PLANET, COMET, ASTEROID, GALAXY, NEBULA, CLUSTER, STAR_SYSTEM, OTHER
}

class Catalog(
    val version: Int,
    val objects: List<AstroObject>,
)


