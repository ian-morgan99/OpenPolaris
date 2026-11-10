package dev.openpolaris.core.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Resident Astro Wi-Fi keepalive state. */
data class AstroWebUiKeepWifiState(
    val enabled: Boolean,
    val running: Boolean,
    val aligned: Boolean,
    val forced: Boolean,
)

sealed interface AstroWebUiKeepWifiResult {
    data class Success(val state: AstroWebUiKeepWifiState) : AstroWebUiKeepWifiResult
    data class Accepted(val enabled: Boolean, val forced: Boolean) : AstroWebUiKeepWifiResult
    data class Failure(val reason: String) : AstroWebUiKeepWifiResult
}

sealed interface AstroWebUiKeepWifiUiState {
    data object Idle : AstroWebUiKeepWifiUiState
    data object Loading : AstroWebUiKeepWifiUiState
    data class Ready(val state: AstroWebUiKeepWifiState) : AstroWebUiKeepWifiUiState
    data class Accepted(val enabled: Boolean, val forced: Boolean) : AstroWebUiKeepWifiUiState
    data class Failed(val reason: String) : AstroWebUiKeepWifiUiState
}

private object AstroWebUiKeepWifiJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

private fun JsonObject.keepWifiBoolean(name: String): Boolean {
    val value = this[name] as? JsonPrimitive ?: error("$name must be a JSON boolean")
    require(!value.isString) { "$name must be a JSON boolean" }
    return when (value.content) {
        "true" -> true
        "false" -> false
        else -> error("$name must be a JSON boolean")
    }
}

object AstroWebUiKeepWifiParser {
    fun parse(body: String): AstroWebUiKeepWifiResult = runCatching {
        val root = AstroWebUiKeepWifiJson.parser.parseToJsonElement(body) as? JsonObject
            ?: error("response must be a JSON object")
        require(root.keepWifiBoolean("ok")) { "response was not acknowledged" }
        AstroWebUiKeepWifiState(
            enabled = root.keepWifiBoolean("enabled"),
            running = root.keepWifiBoolean("running"),
            aligned = root.keepWifiBoolean("aligned"),
            forced = root.keepWifiBoolean("forced"),
        )
    }.fold(
        onSuccess = { AstroWebUiKeepWifiResult.Success(it) },
        onFailure = { error ->
            AstroWebUiKeepWifiResult.Failure(
                "Astro WebUI keep-Wi-Fi state is malformed: ${error.message ?: "invalid JSON"}",
            )
        },
    )
}

interface AstroWebUiKeepWifiClient {
    suspend fun read(host: String, port: Int): AstroWebUiKeepWifiResult
    suspend fun set(host: String, port: Int, enabled: Boolean, forced: Boolean): AstroWebUiKeepWifiResult
}

class DefaultAstroWebUiKeepWifiClient(
    private val transport: AstroWebUiCaptureTransport,
) : AstroWebUiKeepWifiClient {
    override suspend fun read(host: String, port: Int): AstroWebUiKeepWifiResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiKeepWifiResult.Failure("Astro WebUI host is invalid")
        }
        val response = astroWebUiHttpGet(host, port, "/api/keepwifi", MAX_BYTES)
        if (response.error != null) return AstroWebUiKeepWifiResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiKeepWifiResult.Failure("Astro WebUI keep-Wi-Fi HTTP ${response.statusCode}")
        }
        return AstroWebUiKeepWifiParser.parse(response.body.decodeToString())
    }

    override suspend fun set(host: String, port: Int, enabled: Boolean, forced: Boolean): AstroWebUiKeepWifiResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiKeepWifiResult.Failure("Astro WebUI host is invalid")
        }
        val value = when {
            !enabled -> "0"
            forced -> "force"
            else -> "1"
        }
        val response = transport.post(
            AstroWebUiCaptureHttpRequest(
                host = host,
                port = port,
                path = "/api/keepwifi",
                contentType = "application/x-www-form-urlencoded",
                body = "on=" + value,
            ),
        )
        if (response.error != null) return AstroWebUiKeepWifiResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiKeepWifiResult.Failure("Astro WebUI keep-Wi-Fi write HTTP ${response.statusCode}")
        }
        val root = runCatching {
            AstroWebUiKeepWifiJson.parser.parseToJsonElement(response.body.decodeToString()) as? JsonObject
        }.getOrNull() ?: return AstroWebUiKeepWifiResult.Failure("Astro WebUI keep-Wi-Fi acknowledgement is malformed")
        if (root["ok"]?.toString() != "true") {
            val error = (root["error"] as? JsonPrimitive)?.content
            return AstroWebUiKeepWifiResult.Failure(error ?: "Astro WebUI rejected keep-Wi-Fi change")
        }
        return AstroWebUiKeepWifiResult.Accepted(enabled, forced && enabled)
    }

    private companion object {
        const val MAX_BYTES = 16L * 1024L
    }
}

fun createAstroWebUiKeepWifiClient(): AstroWebUiKeepWifiClient =
    DefaultAstroWebUiKeepWifiClient(createAstroWebUiCaptureTransport())
