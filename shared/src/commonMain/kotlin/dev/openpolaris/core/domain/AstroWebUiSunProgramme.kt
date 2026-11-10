package dev.openpolaris.core.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Source-aligned scheduled sunrise/sunset lapse request for /api/prog/sun. */
data class AstroWebUiSunParameters(
    val sunset: Boolean,
    val intervalSeconds: Double,
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
    val nowEpochSeconds: Long,
) {
    init {
        require(intervalSeconds.isFinite() && intervalSeconds > 0.0 && intervalSeconds <= 3600.0)
        require(startEpochSeconds > 0L)
        require(endEpochSeconds > startEpochSeconds)
        require(nowEpochSeconds > 0L)
    }
}

sealed interface AstroWebUiSunResult {
    data class Preview(val frame: String) : AstroWebUiSunResult
    data object Accepted : AstroWebUiSunResult
    data class Failure(val reason: String) : AstroWebUiSunResult
}

sealed interface AstroWebUiSunState {
    data object Idle : AstroWebUiSunState
    data object Loading : AstroWebUiSunState
    data class Preview(val frame: String) : AstroWebUiSunState
    data object Accepted : AstroWebUiSunState
    data class Failed(val reason: String) : AstroWebUiSunState
}

interface AstroWebUiSunProgrammeClient {
    suspend fun preview(host: String, port: Int, parameters: AstroWebUiSunParameters): AstroWebUiSunResult
    suspend fun start(host: String, port: Int, parameters: AstroWebUiSunParameters): AstroWebUiSunResult
}

@Serializable
private data class WireSunAck(
    val ok: Boolean,
    val valid: Boolean? = null,
    val preview: Boolean? = null,
    val frame: String? = null,
    val error: String? = null,
)

private object AstroWebUiSunJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

class DefaultAstroWebUiSunProgrammeClient(
    private val transport: AstroWebUiCaptureTransport,
) : AstroWebUiSunProgrammeClient {
    override suspend fun preview(
        host: String,
        port: Int,
        parameters: AstroWebUiSunParameters,
    ): AstroWebUiSunResult = submit(host, port, parameters, confirm = false)

    override suspend fun start(
        host: String,
        port: Int,
        parameters: AstroWebUiSunParameters,
    ): AstroWebUiSunResult = submit(host, port, parameters, confirm = true)

    private suspend fun submit(
        host: String,
        port: Int,
        parameters: AstroWebUiSunParameters,
        confirm: Boolean,
    ): AstroWebUiSunResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiSunResult.Failure("Astro WebUI host is invalid")
        }
        val body = buildString {
            append("sunset=").append(if (parameters.sunset) 1 else 0)
            append("&interval=").append(number(parameters.intervalSeconds))
            append("&start=").append(parameters.startEpochSeconds)
            append("&end=").append(parameters.endEpochSeconds)
            append("&now=").append(parameters.nowEpochSeconds)
            if (confirm) append("&confirm=1")
        }
        val response = transport.post(
            AstroWebUiCaptureHttpRequest(
                host = host,
                port = port,
                path = "/api/prog/sun",
                contentType = "application/x-www-form-urlencoded",
                body = body,
            ),
        )
        response.error?.let { return AstroWebUiSunResult.Failure(it) }
        if (response.statusCode !in 200..299) {
            return AstroWebUiSunResult.Failure("Astro WebUI sun programme HTTP ${response.statusCode}")
        }
        val ack = runCatching {
            AstroWebUiSunJson.parser.decodeFromString<WireSunAck>(response.body.decodeToString())
        }.getOrElse { error ->
            return AstroWebUiSunResult.Failure(
                "Astro WebUI sun programme acknowledgement is malformed: " +
                    (error.message ?: "invalid JSON"),
            )
        }
        if (!ack.ok) {
            return AstroWebUiSunResult.Failure(
                ack.error?.takeIf { it.isNotBlank() } ?: "Astro WebUI rejected sun programme",
            )
        }
        if (confirm) return AstroWebUiSunResult.Accepted
        if (ack.valid != true || ack.preview != true) {
            return AstroWebUiSunResult.Failure(
                ack.error?.takeIf { it.isNotBlank() }
                    ?: "Astro WebUI sun programme did not return a valid preview",
            )
        }
        return AstroWebUiSunResult.Preview(
            frame = ack.frame ?: return AstroWebUiSunResult.Failure(
                "Astro WebUI sun programme preview omitted its frame",
            ),
        )
    }

    private fun number(value: Double): String =
        if (value == kotlin.math.floor(value)) value.toLong().toString() else value.toString()
}

fun createAstroWebUiSunProgrammeClient(): AstroWebUiSunProgrammeClient =
    DefaultAstroWebUiSunProgrammeClient(createAstroWebUiCaptureTransport())
