package dev.openpolaris.core.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Read-only state exposed by the resident Astro WebUI. */
data class AstroWebUiStatus(
    val active: Boolean,
    val latitudeDeg: Double? = null,
    val longitudeDeg: Double? = null,
    val trackRequested: Boolean? = null,
    val rateIndex: Int? = null,
    val halfSpeed: Boolean? = null,
    val alignmentHeadingDeg: Double? = null,
)

sealed interface AstroWebUiStatusResult {
    data class Success(val status: AstroWebUiStatus) : AstroWebUiStatusResult
    data class Failure(val reason: String) : AstroWebUiStatusResult
}

sealed interface AstroWebUiStatusState {
    data object Idle : AstroWebUiStatusState
    data object Loading : AstroWebUiStatusState
    data class Ready(val status: AstroWebUiStatus) : AstroWebUiStatusState
    data class Failed(val reason: String) : AstroWebUiStatusState
}

private object AstroWebUiStatusJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

@Serializable
private data class WireAstroWebUiStatus(
    val active: Boolean,
    val lat: Double? = null,
    val lon: Double? = null,
    val track_requested: Boolean? = null,
    val rate: Int? = null,
    val half: Boolean? = null,
    val align_deg: Double? = null,
)

object AstroWebUiStatusParser {
    fun parse(body: String): AstroWebUiStatusResult = runCatching {
        val wire = AstroWebUiStatusJson.parser.decodeFromString<WireAstroWebUiStatus>(body)
        require((wire.lat == null) == (wire.lon == null)) {
            "latitude and longitude must be supplied together"
        }
        wire.lat?.let { latitude ->
            require(latitude.isFinite() && latitude in -90.0..90.0) {
                "latitude is outside the valid range"
            }
        }
        wire.lon?.let { longitude ->
            require(longitude.isFinite() && longitude in -180.0..180.0) {
                "longitude is outside the valid range"
            }
        }
        wire.align_deg?.let { heading ->
            require(heading.isFinite() && heading >= 0.0 && heading < 360.0) {
                "alignment heading is outside the valid range"
            }
        }
        wire.rate?.let { rate ->
            require(rate >= 0) { "tracking rate is negative" }
        }
        AstroWebUiStatusResult.Success(
            AstroWebUiStatus(
                active = wire.active,
                latitudeDeg = wire.lat,
                longitudeDeg = wire.lon,
                trackRequested = wire.track_requested,
                rateIndex = wire.rate,
                halfSpeed = wire.half,
                alignmentHeadingDeg = wire.align_deg,
            ),
        )
    }.getOrElse { error ->
        AstroWebUiStatusResult.Failure(
            "Astro WebUI status is malformed: ${error.message ?: "invalid JSON"}",
        )
    }
}

interface AstroWebUiStatusClient {
    suspend fun read(host: String, port: Int): AstroWebUiStatusResult
}

class DefaultAstroWebUiStatusClient : AstroWebUiStatusClient {
    override suspend fun read(host: String, port: Int): AstroWebUiStatusResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiStatusResult.Failure("Astro WebUI host is invalid")
        }
        val response = astroWebUiHttpGet(host, port, "/api/astro", MAX_STATUS_BYTES)
        if (response.error != null) return AstroWebUiStatusResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiStatusResult.Failure("Astro WebUI status HTTP ${response.statusCode}")
        }
        return AstroWebUiStatusParser.parse(response.body.decodeToString())
    }

    private companion object {
        const val MAX_STATUS_BYTES = 64L * 1024L
    }
}

fun createAstroWebUiStatusClient(): AstroWebUiStatusClient = DefaultAstroWebUiStatusClient()
