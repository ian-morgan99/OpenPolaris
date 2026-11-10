package dev.openpolaris.core.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Source-specific in-head Astro capture, `/api/astro/capture`. */
data class AstroWebUiAstroCaptureParameters(
    val intervalSeconds: Int,
    val shots: Int = -1,
    val bulbSeconds: Double = 0.0,
) {
    init {
        require(intervalSeconds in 1..3_600) { "interval must be between 1 and 3600 seconds" }
        require(shots == -1 || shots in 1..5_000) { "shots must be 1..5000, or unlimited" }
        require(bulbSeconds.isFinite() && bulbSeconds in 0.0..3_600.0) {
            "bulb must be between 0 and 3600 seconds"
        }
        require(bulbSeconds == 0.0 || bulbSeconds < intervalSeconds) {
            "bulb exposure must be shorter than the interval"
        }
    }
}

sealed interface AstroWebUiAstroCaptureResult {
    /** HTTP accepted the run; resident programme state remains authoritative. */
    data class Accepted(val programme: AstroWebUiProgramme? = null) : AstroWebUiAstroCaptureResult
    data class Failure(val reason: String) : AstroWebUiAstroCaptureResult
}

sealed interface AstroWebUiAstroCaptureState {
    data object Idle : AstroWebUiAstroCaptureState
    data object Loading : AstroWebUiAstroCaptureState
    data class Accepted(val programme: AstroWebUiProgramme? = null) : AstroWebUiAstroCaptureState
    data class Failed(val reason: String) : AstroWebUiAstroCaptureState
}

interface AstroWebUiAstroCaptureClient {
    suspend fun start(
        host: String,
        port: Int,
        parameters: AstroWebUiAstroCaptureParameters,
    ): AstroWebUiAstroCaptureResult
}

@Serializable
private data class WireAstroCaptureAck(
    val ok: Boolean,
    val error: String? = null,
    val prog: JsonObject? = null,
)

private object AstroWebUiAstroCaptureJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

class DefaultAstroWebUiAstroCaptureClient(
    private val transport: AstroWebUiCaptureTransport,
) : AstroWebUiAstroCaptureClient {
    override suspend fun start(
        host: String,
        port: Int,
        parameters: AstroWebUiAstroCaptureParameters,
    ): AstroWebUiAstroCaptureResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiAstroCaptureResult.Failure("Astro WebUI host is invalid")
        }
        val response = transport.post(
            AstroWebUiCaptureHttpRequest(
                host = host,
                port = port,
                path = "/api/astro/capture",
                contentType = "application/x-www-form-urlencoded",
                body = form(parameters),
            ),
        )
        if (response.error != null) return AstroWebUiAstroCaptureResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiAstroCaptureResult.Failure(
                "Astro WebUI Astro capture HTTP ${response.statusCode}",
            )
        }
        val ack = runCatching {
            AstroWebUiAstroCaptureJson.parser.decodeFromString<WireAstroCaptureAck>(
                response.body.decodeToString(),
            )
        }.getOrElse { error ->
            return AstroWebUiAstroCaptureResult.Failure(
                "Astro WebUI Astro capture acknowledgement is malformed: " +
                    (error.message ?: "invalid JSON"),
            )
        }
        if (!ack.ok) {
            return AstroWebUiAstroCaptureResult.Failure(
                ack.error?.takeIf { it.isNotBlank() } ?: "Astro WebUI rejected Astro capture",
            )
        }
        val programme = ack.prog?.let { encoded ->
            when (val parsed = AstroWebUiProgrammeParser.parse(encoded.toString())) {
                is AstroWebUiProgrammeResult.Success -> parsed.programme
                is AstroWebUiProgrammeResult.Failure -> {
                    return AstroWebUiAstroCaptureResult.Failure(parsed.reason)
                }
            }
        }
        return AstroWebUiAstroCaptureResult.Accepted(programme)
    }

    private fun form(parameters: AstroWebUiAstroCaptureParameters): String = listOf(
        "interval=${parameters.intervalSeconds}",
        "shots=${if (parameters.shots == -1) "" else parameters.shots}",
        "bulb=${number(parameters.bulbSeconds)}",
        "confirm=1",
    ).joinToString("&")

    private fun number(value: Double): String =
        if (value == kotlin.math.floor(value)) value.toLong().toString() else value.toString()
}

fun createAstroWebUiAstroCaptureClient(): AstroWebUiAstroCaptureClient =
    DefaultAstroWebUiAstroCaptureClient(createAstroWebUiCaptureTransport())
