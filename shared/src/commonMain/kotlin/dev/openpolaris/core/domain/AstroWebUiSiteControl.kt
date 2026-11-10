package dev.openpolaris.core.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class AstroWebUiSiteWriteRequest(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
) {
    init {
        require(latitudeDeg.isFinite() && latitudeDeg in -90.0..90.0)
        require(longitudeDeg.isFinite() && longitudeDeg in -180.0..180.0)
    }
}

sealed interface AstroWebUiSiteWriteResult {
    data class Accepted(val site: AstroWebUiSite) : AstroWebUiSiteWriteResult
    data class Failure(val reason: String) : AstroWebUiSiteWriteResult
}

sealed interface AstroWebUiSiteWriteState {
    data object Idle : AstroWebUiSiteWriteState
    data class Working(val latitudeDeg: Double, val longitudeDeg: Double) : AstroWebUiSiteWriteState
    data class Accepted(val site: AstroWebUiSite) : AstroWebUiSiteWriteState
    data class Failed(val reason: String) : AstroWebUiSiteWriteState
}

interface AstroWebUiSiteControlClient {
    suspend fun save(host: String, port: Int, request: AstroWebUiSiteWriteRequest): AstroWebUiSiteWriteResult
}

private object AstroWebUiSiteControlJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

@Serializable
private data class WireSiteWriteAck(
    val ok: Boolean,
    val error: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)

class DefaultAstroWebUiSiteControlClient(
    private val transport: AstroWebUiCaptureTransport,
) : AstroWebUiSiteControlClient {
    override suspend fun save(
        host: String,
        port: Int,
        request: AstroWebUiSiteWriteRequest,
    ): AstroWebUiSiteWriteResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiSiteWriteResult.Failure("Astro WebUI host is invalid")
        }

        val response = transport.post(
            AstroWebUiCaptureHttpRequest(
                host = host,
                port = port,
                path = "/api/site",
                contentType = "application/x-www-form-urlencoded",
                body = "lat=${request.latitudeDeg}&lon=${request.longitudeDeg}",
            ),
        )
        response.error?.let { return AstroWebUiSiteWriteResult.Failure(it) }
        if (response.statusCode !in 200..299) {
            return AstroWebUiSiteWriteResult.Failure("Astro WebUI site save HTTP ${response.statusCode}")
        }

        val ack = runCatching {
            AstroWebUiSiteControlJson.parser.decodeFromString(WireSiteWriteAck.serializer(), response.body.decodeToString())
        }.getOrElse {
            return AstroWebUiSiteWriteResult.Failure("Astro WebUI site save returned invalid JSON")
        }
        if (!ack.ok) {
            return AstroWebUiSiteWriteResult.Failure(ack.error ?: "Astro WebUI rejected site save")
        }

        val latitude = ack.lat
            ?: return AstroWebUiSiteWriteResult.Failure("Astro WebUI site save omitted latitude")
        val longitude = ack.lon
            ?: return AstroWebUiSiteWriteResult.Failure("Astro WebUI site save omitted longitude")
        val savedRequest = runCatching {
            AstroWebUiSiteWriteRequest(latitude, longitude)
        }.getOrElse {
            return AstroWebUiSiteWriteResult.Failure("Astro WebUI site save returned an invalid site")
        }
        return AstroWebUiSiteWriteResult.Accepted(
            AstroWebUiSite(
                havePosition = true,
                latitudeDeg = savedRequest.latitudeDeg,
                longitudeDeg = savedRequest.longitudeDeg,
            ),
        )
    }
}

fun createAstroWebUiSiteControlClient(): AstroWebUiSiteControlClient =
    DefaultAstroWebUiSiteControlClient(createAstroWebUiCaptureTransport())
