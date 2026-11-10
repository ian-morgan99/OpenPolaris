package dev.openpolaris.core.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class AstroWebUiTrackRate(val index: Int) {
    SIDEREAL(0),
    LUNAR(2),
}

data class AstroWebUiTrackRequest(
    val on: Boolean,
    val rate: Int,
    val halfSpeed: Boolean,
) {
    init {
        require(rate == AstroWebUiTrackRate.SIDEREAL.index || rate == AstroWebUiTrackRate.LUNAR.index) {
            "Astro WebUI rate must be sidereal (0) or lunar (2)"
        }
    }
}

sealed interface AstroWebUiSessionControlResult {
    data class Accepted(val action: String) : AstroWebUiSessionControlResult
    data class Failure(val reason: String) : AstroWebUiSessionControlResult
}

sealed interface AstroWebUiSessionControlState {
    data object Idle : AstroWebUiSessionControlState
    data class Working(val action: String) : AstroWebUiSessionControlState
    data class Accepted(val action: String) : AstroWebUiSessionControlState
    data class Failed(val reason: String) : AstroWebUiSessionControlState
}

interface AstroWebUiSessionControlClient {
    suspend fun enter(host: String, port: Int): AstroWebUiSessionControlResult
    suspend fun leave(host: String, port: Int): AstroWebUiSessionControlResult
    suspend fun setTracking(
        host: String,
        port: Int,
        request: AstroWebUiTrackRequest,
    ): AstroWebUiSessionControlResult
}

private object AstroWebUiSessionControlJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

@Serializable
private data class WireSessionControlAck(
    val ok: Boolean,
    val error: String? = null,
)

class DefaultAstroWebUiSessionControlClient(
    private val transport: AstroWebUiCaptureTransport,
) : AstroWebUiSessionControlClient {
    override suspend fun enter(host: String, port: Int): AstroWebUiSessionControlResult =
        post(host, port, "/api/astro/enter", "", "enter Astro mode")

    override suspend fun leave(host: String, port: Int): AstroWebUiSessionControlResult =
        post(host, port, "/api/astro/leave", "", "leave Astro mode")

    override suspend fun setTracking(
        host: String,
        port: Int,
        request: AstroWebUiTrackRequest,
    ): AstroWebUiSessionControlResult =
        post(
            host,
            port,
            "/api/astro/track",
            "on=" + (if (request.on) "1" else "0") +
                "&rate=" + request.rate +
                "&half=" + (if (request.halfSpeed) "1" else "0"),
            if (request.on) "start Astro tracking" else "stop Astro tracking",
        )

    private suspend fun post(
        host: String,
        port: Int,
        path: String,
        body: String,
        action: String,
    ): AstroWebUiSessionControlResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiSessionControlResult.Failure("Astro WebUI host is invalid")
        }
        val response = transport.post(
            AstroWebUiCaptureHttpRequest(
                host = host,
                port = port,
                path = path,
                contentType = "application/x-www-form-urlencoded",
                body = body,
            ),
        )
        if (response.error != null) return AstroWebUiSessionControlResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiSessionControlResult.Failure(
                "Astro WebUI " + action + " HTTP " + response.statusCode,
            )
        }
        val ack = runCatching {
            AstroWebUiSessionControlJson.parser.decodeFromString<WireSessionControlAck>(
                response.body.decodeToString(),
            )
        }.getOrElse { error ->
            return AstroWebUiSessionControlResult.Failure(
                "Astro WebUI " + action + " acknowledgement is malformed: " +
                    (error.message ?: "invalid JSON"),
            )
        }
        if (!ack.ok) {
            return AstroWebUiSessionControlResult.Failure(
                ack.error?.takeIf { it.isNotBlank() } ?: "Astro WebUI rejected " + action,
            )
        }
        return AstroWebUiSessionControlResult.Accepted(action)
    }
}

fun createAstroWebUiSessionControlClient(): AstroWebUiSessionControlClient =
    DefaultAstroWebUiSessionControlClient(createAstroWebUiCaptureTransport())
