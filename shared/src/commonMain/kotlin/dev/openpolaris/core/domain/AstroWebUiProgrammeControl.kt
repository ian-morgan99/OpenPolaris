package dev.openpolaris.core.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface AstroWebUiProgrammeControlResult {
    data object Accepted : AstroWebUiProgrammeControlResult
    data class Failure(val reason: String) : AstroWebUiProgrammeControlResult
}

/**
 * Source-aligned safety controls for an already resident Astro WebUI run.
 *
 * These endpoints acknowledge a request; they do not prove that the head has
 * reached a terminal state. The caller must continue polling /api/prog.
 */
interface AstroWebUiProgrammeControlClient {
    suspend fun cancel(host: String, port: Int): AstroWebUiProgrammeControlResult
    suspend fun setPanoramaPaused(
        host: String,
        port: Int,
        paused: Boolean,
    ): AstroWebUiProgrammeControlResult
}

private object AstroWebUiProgrammeControlJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

@Serializable
private data class WireProgrammeControlAck(
    val ok: Boolean,
    val error: String? = null,
)

class DefaultAstroWebUiProgrammeControlClient(
    private val transport: AstroWebUiCaptureTransport,
) : AstroWebUiProgrammeControlClient {
    override suspend fun cancel(host: String, port: Int): AstroWebUiProgrammeControlResult =
        post(host, port, "/api/prog/cancel", "", "programme cancel")

    override suspend fun setPanoramaPaused(
        host: String,
        port: Int,
        paused: Boolean,
    ): AstroWebUiProgrammeControlResult =
        post(
            host,
            port,
            "/api/prog/pano/pause",
            "paused=" + if (paused) "1" else "0",
            if (paused) "panorama pause" else "panorama resume",
        )

    private suspend fun post(
        host: String,
        port: Int,
        path: String,
        body: String,
        action: String,
    ): AstroWebUiProgrammeControlResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiProgrammeControlResult.Failure("Astro WebUI host is invalid")
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
        if (response.error != null) return AstroWebUiProgrammeControlResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiProgrammeControlResult.Failure(
                "Astro WebUI " + action + " HTTP " + response.statusCode,
            )
        }
        val ack = runCatching {
            AstroWebUiProgrammeControlJson.parser.decodeFromString<WireProgrammeControlAck>(
                response.body.decodeToString(),
            )
        }.getOrElse { error ->
            return AstroWebUiProgrammeControlResult.Failure(
                "Astro WebUI " + action + " acknowledgement is malformed: " +
                    (error.message ?: "invalid JSON"),
            )
        }
        if (!ack.ok) {
            return AstroWebUiProgrammeControlResult.Failure(
                ack.error?.takeIf { it.isNotBlank() } ?: "Astro WebUI rejected " + action,
            )
        }
        return AstroWebUiProgrammeControlResult.Accepted
    }
}

expect fun createAstroWebUiProgrammeControlClient(): AstroWebUiProgrammeControlClient
