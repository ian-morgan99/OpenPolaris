package dev.openpolaris.core.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Read-only observing-site state exposed by the resident Astro WebUI. */
data class AstroWebUiSite(
    val havePosition: Boolean,
    val latitudeDeg: Double? = null,
    val longitudeDeg: Double? = null,
)

sealed interface AstroWebUiSiteResult {
    data class Success(val site: AstroWebUiSite) : AstroWebUiSiteResult
    data class Failure(val reason: String) : AstroWebUiSiteResult
}

sealed interface AstroWebUiSiteState {
    data object Idle : AstroWebUiSiteState
    data object Loading : AstroWebUiSiteState
    data class Ready(val site: AstroWebUiSite) : AstroWebUiSiteState
    data class Failed(val reason: String) : AstroWebUiSiteState
}

private object AstroWebUiSiteJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

@Serializable
private data class WireAstroWebUiSite(
    val have_pos: Boolean,
    val lat: Double? = null,
    val lon: Double? = null,
)

object AstroWebUiSiteParser {
    fun parse(body: String): AstroWebUiSiteResult = runCatching {
        val wire = AstroWebUiSiteJson.parser.decodeFromString<WireAstroWebUiSite>(body)
        if (!wire.have_pos) {
            return@runCatching AstroWebUiSiteResult.Success(AstroWebUiSite(havePosition = false))
        }
        val latitude = requireNotNull(wire.lat) { "latitude is missing" }
        val longitude = requireNotNull(wire.lon) { "longitude is missing" }
        require(latitude.isFinite() && latitude in -90.0..90.0) { "latitude is out of range" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "longitude is out of range" }
        AstroWebUiSiteResult.Success(
            AstroWebUiSite(
                havePosition = true,
                latitudeDeg = latitude,
                longitudeDeg = longitude,
            ),
        )
    }.getOrElse { error ->
        AstroWebUiSiteResult.Failure(
            "Astro WebUI site is malformed: ${error.message ?: "invalid JSON"}",
        )
    }
}

interface AstroWebUiSiteClient {
    suspend fun read(host: String, port: Int): AstroWebUiSiteResult
}

class DefaultAstroWebUiSiteClient : AstroWebUiSiteClient {
    override suspend fun read(host: String, port: Int): AstroWebUiSiteResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiSiteResult.Failure("Astro WebUI host is invalid")
        }
        val response = astroWebUiHttpGet(host, port, "/api/site", MAX_SITE_BYTES)
        if (response.error != null) return AstroWebUiSiteResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiSiteResult.Failure("Astro WebUI site HTTP ${response.statusCode}")
        }
        return AstroWebUiSiteParser.parse(response.body.decodeToString())
    }

    private companion object {
        const val MAX_SITE_BYTES = 64L * 1024L
    }
}

fun createAstroWebUiSiteClient(): AstroWebUiSiteClient = DefaultAstroWebUiSiteClient()
