package dev.openpolaris.core.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class AstroWebUiLinkPhase { UP, CONNECTING, DOWN }

data class AstroWebUiOpcodeState(
    val command: Int,
    val args: String,
    val ageMs: Long,
    val count: Long,
)

data class AstroWebUiLinkState(
    val phase: AstroWebUiLinkPhase,
    val wanted: Boolean,
    val connects: Long,
    val drops: Long,
    val receivedFrames: Long,
    val transmittedFrames: Long,
    val parseErrors: Long,
    val evictions: Long,
    val upMs: Long,
    val lastRxAgeMs: Long,
    val error: String,
    val opcodes: List<AstroWebUiOpcodeState>,
)

sealed interface AstroWebUiLinkResult {
    data class Success(val state: AstroWebUiLinkState) : AstroWebUiLinkResult
    data class Failure(val reason: String) : AstroWebUiLinkResult
}

sealed interface AstroWebUiLinkUiState {
    data object Idle : AstroWebUiLinkUiState
    data object Loading : AstroWebUiLinkUiState
    data class Ready(val state: AstroWebUiLinkState) : AstroWebUiLinkUiState
    data class Failed(val reason: String) : AstroWebUiLinkUiState
}

private object AstroWebUiLinkJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

private fun JsonObject.boolean(name: String): Boolean {
    val value = this[name] as? JsonPrimitive ?: error("$name must be a JSON boolean")
    require(!value.isString) { "$name must be a JSON boolean" }
    return when (value.content) {
        "true" -> true
        "false" -> false
        else -> error("$name must be a JSON boolean")
    }
}

private fun JsonObject.long(name: String): Long {
    val value = this[name] as? JsonPrimitive ?: error("$name must be a JSON integer")
    require(!value.isString) { "$name must be a JSON integer" }
    return value.content.toLongOrNull()?.also { require(it >= 0) { "$name must be non-negative" } }
        ?: error("$name must be a JSON integer")
}

private fun JsonObject.age(name: String): Long {
    val value = this[name] as? JsonPrimitive ?: error("$name must be a JSON integer")
    require(!value.isString) { "$name must be a JSON integer" }
    return value.content.toLongOrNull()?.also { require(it >= 0) { "$name must be non-negative" } }
        ?: error("$name must be a JSON integer")
}

private fun JsonObject.text(name: String, maxLength: Int): String {
    val value = this[name] as? JsonPrimitive ?: error("$name must be a JSON string")
    require(value.isString) { "$name must be a JSON string" }
    return value.content.also { require(it.length <= maxLength) { "$name is too long" } }
}

object AstroWebUiLinkParser {
    fun parse(body: String): AstroWebUiLinkResult = runCatching {
        val root = AstroWebUiLinkJson.parser.parseToJsonElement(body) as? JsonObject
            ?: error("response must be a JSON object")
        val phase = when (root.text("status", 16)) {
            "up" -> AstroWebUiLinkPhase.UP
            "connecting" -> AstroWebUiLinkPhase.CONNECTING
            "down" -> AstroWebUiLinkPhase.DOWN
            else -> error("status is not a known link phase")
        }
        val opcodeObject = root["opcodes"] as? JsonObject ?: error("opcodes must be an object")
        require(opcodeObject.size <= 256) { "opcodes is too large" }
        val opcodes = opcodeObject.entries.map { (key, value) ->
            val command = key.toIntOrNull() ?: error("opcode key is not an integer")
            require(command in 0..9999) { "opcode is outside the safe range" }
            val item = value as? JsonObject ?: error("opcode $command must be an object")
            AstroWebUiOpcodeState(
                command = command,
                args = item.text("args", 512),
                ageMs = item.age("age_ms"),
                count = item.long("count"),
            )
        }.sortedBy { it.command }
        val lastRxAge = (root["last_rx_age_ms"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?: error("last_rx_age_ms must be an integer")
        require(lastRxAge >= -1) { "last_rx_age_ms is invalid" }
        AstroWebUiLinkState(
            phase = phase,
            wanted = root.boolean("wanted"),
            connects = root.long("connects"),
            drops = root.long("drops"),
            receivedFrames = root.long("rx"),
            transmittedFrames = root.long("tx"),
            parseErrors = root.long("parse_errors"),
            evictions = root.long("evictions"),
            upMs = root.long("up_ms"),
            lastRxAgeMs = lastRxAge,
            error = root.text("error", 512),
            opcodes = opcodes,
        )
    }.fold(
        onSuccess = { AstroWebUiLinkResult.Success(it) },
        onFailure = { error ->
            AstroWebUiLinkResult.Failure(
                "Astro WebUI link state is malformed: ${error.message ?: "invalid JSON"}",
            )
        },
    )
}

interface AstroWebUiLinkClient {
    suspend fun read(host: String, port: Int): AstroWebUiLinkResult
}

class DefaultAstroWebUiLinkClient : AstroWebUiLinkClient {
    override suspend fun read(host: String, port: Int): AstroWebUiLinkResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiLinkResult.Failure("Astro WebUI host is invalid")
        }
        val response = astroWebUiHttpGet(host, port, "/api/link", MAX_LINK_BYTES)
        if (response.error != null) return AstroWebUiLinkResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiLinkResult.Failure("Astro WebUI link state HTTP ${response.statusCode}")
        }
        return AstroWebUiLinkParser.parse(response.body.decodeToString())
    }

    private companion object {
        const val MAX_LINK_BYTES = 256L * 1024L
    }
}

fun createAstroWebUiLinkClient(): AstroWebUiLinkClient = DefaultAstroWebUiLinkClient()
