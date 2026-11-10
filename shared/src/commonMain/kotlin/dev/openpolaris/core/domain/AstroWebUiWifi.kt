package dev.openpolaris.core.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Read-only resident Astro Wi-Fi interface/configuration summary. */
data class AstroWebUiWifiState(
    val apIp: String,
    val stationIp: String,
    val ssid: String,
    val configured: Boolean,
    val autojoin: Boolean,
)

sealed interface AstroWebUiWifiResult {
    data class Success(val state: AstroWebUiWifiState) : AstroWebUiWifiResult
    data class Failure(val reason: String) : AstroWebUiWifiResult
}

sealed interface AstroWebUiWifiUiState {
    data object Idle : AstroWebUiWifiUiState
    data object Loading : AstroWebUiWifiUiState
    data class Ready(val state: AstroWebUiWifiState) : AstroWebUiWifiUiState
    data class Failed(val reason: String) : AstroWebUiWifiUiState
}

private object AstroWebUiWifiJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

private fun JsonObject.wifiBoolean(name: String): Boolean {
    val value = this[name] as? JsonPrimitive ?: error("$name must be a JSON boolean")
    require(!value.isString) { "$name must be a JSON boolean" }
    return when (value.content) {
        "true" -> true
        "false" -> false
        else -> error("$name must be a JSON boolean")
    }
}

private fun JsonObject.wifiText(name: String, maxLength: Int): String {
    val value = this[name] as? JsonPrimitive ?: error("$name must be a JSON string")
    require(value.isString) { "$name must be a JSON string" }
    return value.content.also { require(it.length <= maxLength) { "$name is too long" } }
}

object AstroWebUiWifiParser {
    fun parse(body: String): AstroWebUiWifiResult = runCatching {
        val root = AstroWebUiWifiJson.parser.parseToJsonElement(body) as? JsonObject
            ?: error("response must be a JSON object")
        require(root.wifiBoolean("ok")) { "response was not acknowledged" }
        AstroWebUiWifiState(
            apIp = root.wifiText("ap_ip", 64),
            stationIp = root.wifiText("sta_ip", 64),
            ssid = root.wifiText("ssid", 128),
            configured = root.wifiBoolean("configured"),
            autojoin = root.wifiBoolean("autojoin"),
        )
    }.fold(
        onSuccess = { AstroWebUiWifiResult.Success(it) },
        onFailure = { error ->
            AstroWebUiWifiResult.Failure(
                "Astro WebUI Wi-Fi state is malformed: ${error.message ?: "invalid JSON"}",
            )
        },
    )
}

interface AstroWebUiWifiClient {
    suspend fun read(host: String, port: Int): AstroWebUiWifiResult
}

class DefaultAstroWebUiWifiClient : AstroWebUiWifiClient {
    override suspend fun read(host: String, port: Int): AstroWebUiWifiResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiWifiResult.Failure("Astro WebUI host is invalid")
        }
        val response = astroWebUiHttpGet(host, port, "/api/wifi", MAX_BYTES)
        if (response.error != null) return AstroWebUiWifiResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiWifiResult.Failure("Astro WebUI Wi-Fi HTTP ${response.statusCode}")
        }
        return AstroWebUiWifiParser.parse(response.body.decodeToString())
    }

    private companion object {
        const val MAX_BYTES = 16L * 1024L
    }
}

fun createAstroWebUiWifiClient(): AstroWebUiWifiClient =
    DefaultAstroWebUiWifiClient()
