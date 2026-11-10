package dev.openpolaris.core.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Read-only travel-limit status reported by the resident Astro WebUI. */
data class AstroWebUiLimitsState(
    val released: Boolean?,
)

sealed interface AstroWebUiLimitsResult {
    data class Success(val state: AstroWebUiLimitsState) : AstroWebUiLimitsResult
    data class Failure(val reason: String) : AstroWebUiLimitsResult
}

sealed interface AstroWebUiLimitsUiState {
    data object Idle : AstroWebUiLimitsUiState
    data object Loading : AstroWebUiLimitsUiState
    data class Ready(val state: AstroWebUiLimitsState) : AstroWebUiLimitsUiState
    data class Failed(val reason: String) : AstroWebUiLimitsUiState
}

private object AstroWebUiLimitsJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

object AstroWebUiLimitsParser {
    fun parse(body: String): AstroWebUiLimitsResult = runCatching {
        val root = AstroWebUiLimitsJson.parser.parseToJsonElement(body) as? JsonObject
            ?: error("response must be a JSON object")
        val ok = root["ok"] as? JsonPrimitive ?: error("ok must be a JSON boolean")
        require(!ok.isString && ok.content == "true") { "response was not acknowledged" }
        val released = when (val value = root["released"]) {
            null, JsonNull -> null
            is JsonPrimitive -> {
                require(!value.isString) { "released must be a JSON boolean or null" }
                when (value.content) {
                    "true" -> true
                    "false" -> false
                    else -> error("released must be a JSON boolean or null")
                }
            }
            else -> error("released must be a JSON boolean or null")
        }
        AstroWebUiLimitsState(released)
    }.fold(
        onSuccess = { AstroWebUiLimitsResult.Success(it) },
        onFailure = { error ->
            AstroWebUiLimitsResult.Failure(
                "Astro WebUI travel-limit state is malformed: ${error.message ?: "invalid JSON"}",
            )
        },
    )
}

interface AstroWebUiLimitsClient {
    suspend fun read(host: String, port: Int): AstroWebUiLimitsResult
}

class DefaultAstroWebUiLimitsClient : AstroWebUiLimitsClient {
    override suspend fun read(host: String, port: Int): AstroWebUiLimitsResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiLimitsResult.Failure("Astro WebUI host is invalid")
        }
        val response = astroWebUiHttpGet(host, port, "/api/astro/limits", MAX_BYTES)
        if (response.error != null) return AstroWebUiLimitsResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiLimitsResult.Failure("Astro WebUI travel limits HTTP ${response.statusCode}")
        }
        return AstroWebUiLimitsParser.parse(response.body.decodeToString())
    }

    private companion object {
        const val MAX_BYTES = 8L * 1024L
    }
}

fun createAstroWebUiLimitsClient(): AstroWebUiLimitsClient =
    DefaultAstroWebUiLimitsClient()
