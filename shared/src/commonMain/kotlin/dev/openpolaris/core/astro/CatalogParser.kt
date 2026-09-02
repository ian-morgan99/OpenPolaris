package dev.openpolaris.core.astro

/**
 * Standalone parser for the bundled catalog JSON. Kept out of the [Catalog]
 * companion object to sidestep a Kotlin 2.0.21 JVM IR lowering crash that
 * fires whenever a [Catalog]-returning function lives in [Catalog]'s
 * companion object (KT-63735, fixed in 2.0.22+).
 */
object CatalogParser {
    /** Convenience constructor used by tests and desktop seeding. */
    fun of(
        objects: List<AstroObject>,
        version: Int = 1,
    ): Catalog = Catalog(version = version, objects = objects)

    /** Merge two catalogs, preferring `b` on designation collisions. */
    fun merge(a: Catalog, b: Catalog): Catalog {
        val byDesignation = LinkedHashMap<String, AstroObject>()
        for (o in a.objects) byDesignation[o.designation] = o
        for (o in b.objects) byDesignation[o.designation] = o
        return Catalog(
            version = maxOf(a.version, b.version),
            objects = byDesignation.values.toList(),
        )
    }

    fun parse(text: String): Catalog {
        val objects = ArrayList<AstroObject>()
        var version = 1

        val rxDesignation = Regex("\"designation\"\\s*:\\s*\"([^\"]*)\"")
        val rxName = Regex("\"name\"\\s*:\\s*\"([^\"]*)\"")
        val rxType = Regex("\"type\"\\s*:\\s*\"([A-Z_]+)\"")
        val rxRa = Regex("\"raDeg\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
        val rxDec = Regex("\"decDeg\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
        val rxMag = Regex("\"magnitude\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
        val rxConst = Regex("\"constellation\"\\s*:\\s*\"([^\"]*)\"")
        val rxSize = Regex("\"sizeArcmin\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
        val rxDesc = Regex("\"description\"\\s*:\\s*\"([^\"]*)\"")
        val rxVersion = Regex("\"version\"\\s*:\\s*(\\d+)")

        val versionMatch = rxVersion.find(text)
        if (versionMatch != null) version = versionMatch.groupValues[1].toInt()

        val objectBlocks = text.split(Regex("(?<=})\\s*,\\s*(?=\\{)"))
        for (block in objectBlocks) {
            if (!block.trim().startsWith("{")) continue
            val desig = rxDesignation.find(block)?.groupValues?.get(1) ?: continue
            val name = rxName.find(block)?.groupValues?.get(1)
            val typeStr = rxType.find(block)?.groupValues?.get(1) ?: "OTHER"
            val ra = rxRa.find(block)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val dec = rxDec.find(block)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val mag = rxMag.find(block)?.groupValues?.get(1)?.toDoubleOrNull()
            val const = rxConst.find(block)?.groupValues?.get(1)
            val size = rxSize.find(block)?.groupValues?.get(1)?.toDoubleOrNull()
            val desc = rxDesc.find(block)?.groupValues?.get(1)
            val type = runCatching { ObjectType.valueOf(typeStr) }.getOrDefault(ObjectType.OTHER)
            objects.add(
                AstroObject(
                    designation = desig,
                    name = name ?: "",
                    type = type,
                    raDeg = ra,
                    decDeg = dec,
                    magnitude = mag ?: Double.NaN,
                    constellation = const ?: "",
                    sizeArcmin = size ?: 0.0,
                    description = desc ?: "",
                )
            )
        }

        return Catalog(version = version, objects = objects)
    }
}
