package dev.openpolaris.core.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Read-only state exposed by the resident Astro WebUI. */
data class AstroWebUiStatus(
    val active: Boolean,
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
)

object AstroWebUiStatusParser {
    fun parse(body: String): AstroWebUiStatusResult = runCatching {
        val wire = AstroWebUiStatusJson.parser.decodeFromString<WireAstroWebUiStatus>(body)
        AstroWebUiStatusResult.Success(AstroWebUiStatus(active = wire.active))
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
