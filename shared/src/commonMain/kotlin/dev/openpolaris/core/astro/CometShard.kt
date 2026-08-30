package dev.openpolaris.core.astro

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One comet's orbital elements as stored in `comets.json`.
 *
 * Comets are not stored as fixed RA/Dec entries in the catalogue: their
 * positions are computed on demand from the orbital elements (Meeus
 * ch. 33) using [Comets.position]. The [cometsShardFor] helper below
 * parses a comet shard into a list of these records, which a higher
 * layer can then expose to the go-to solver.
 */
@Serializable
data class CometOrbitalElements(
    val designation: String,
    val name: String,
    /** Epoch of perihelion passage, Julian Date (TT). */
    val tJD: Double,
    /** Eccentricity. 1.0 means a perfectly parabolic orbit. */
    val e: Double,
    /** Perihelion distance, AU. */
    val qAU: Double,
    /** Inclination to the ecliptic, degrees. */
    val iDeg: Double,
    /** Longitude of ascending node Ω, degrees. */
    val omegaDeg: Double,
    /** Argument of perihelion ω, degrees. */
    val wDeg: Double,
    /** Element epoch Julian Date (informational). */
    val epochJD: Double,
    /** Approximate peak visual magnitude (informational, may be null). */
    val magnitude: Double? = null,
)

/**
 * Wire format of a comets shard. Distinct from [Catalog] because
 * comets have orbital elements rather than a fixed RA/Dec.
 */
@Serializable
data class CometsShard(
    val version: Int,
    val type: String,
    val objects: List<CometOrbitalElements>,
)

object CometShardLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): CometsShard = json.decodeFromString(CometsShard.serializer(), text)
}

/** Convenience accessor: parse a comet shard text into a list of orbital elements. */
fun Comets.fromShard(text: String): List<CometOrbitalElements> =
    CometShardLoader.parse(text).objects
