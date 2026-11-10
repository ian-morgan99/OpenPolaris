package dev.openpolaris.core.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Read-only solver and alignment state exposed by Astro WebUI /api/state. */
data class AstroWebUiRunSolution(
    val solved: Boolean? = null,
    val rightAscensionDeg: Double? = null,
    val declinationDeg: Double? = null,
)

data class AstroWebUiRunState(
    val status: String,
    val elapsedSeconds: Long,
    /** True only when this response came from an explicit mount=1 read. */
    val mountRead: Boolean,
    /** The WebUI refused the explicit mount read because alignment was unsafe. */
    val mountBlocked: Boolean,
    val altitudeDeg: Double,
    val azimuthDeg: Double,
    val aligned: Boolean,
    val tracking: Boolean,
    val solution: AstroWebUiRunSolution? = null,
    val log: String = "",
)

sealed interface AstroWebUiRunStateResult {
    data class Success(val state: AstroWebUiRunState) : AstroWebUiRunStateResult
    data class Failure(val reason: String) : AstroWebUiRunStateResult
}

sealed interface AstroWebUiRunStateUiState {
    data object Idle : AstroWebUiRunStateUiState
    data object Loading : AstroWebUiRunStateUiState
    data class Ready(val state: AstroWebUiRunState) : AstroWebUiRunStateUiState
    data class Failed(val reason: String) : AstroWebUiRunStateUiState
}

private object AstroWebUiRunStateJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

private fun JsonObject.requiredBoolean(name: String): Boolean {
    val value = this[name] ?: error("$name is missing")
    val primitive = value as? JsonPrimitive ?: error("$name must be a JSON boolean")
    require(!primitive.isString) { "$name must be a JSON boolean" }
    return when (primitive.content) {
        "true" -> true
        "false" -> false
        else -> error("$name must be a JSON boolean")
    }
}

private fun JsonObject.requiredNumber(name: String): Double {
    val value = this[name] ?: error("$name is missing")
    val primitive = value as? JsonPrimitive ?: error("$name must be a JSON number")
    require(!primitive.isString) { "$name must be a JSON number" }
    return primitive.content.toDoubleOrNull()?.also {
        require(it.isFinite()) { "$name must be finite" }
    } ?: error("$name must be a JSON number")
}

private fun JsonObject.requiredLong(name: String): Long {
    val number = requiredNumber(name)
    require(number >= 0.0 && number <= Long.MAX_VALUE.toDouble() && number % 1.0 == 0.0) {
        "$name must be a non-negative integer"
    }
    return number.toLong()
}

private fun JsonObject.optionalText(name: String, maxLength: Int): String? {
    val value = this[name] ?: return null
    val primitive = value as? JsonPrimitive ?: error("$name must be a JSON string")
    require(primitive.isString) { "$name must be a JSON string" }
    return primitive.content.also {
        require(it.length <= maxLength) { "$name is too long" }
    }
}

private fun parseSolution(element: JsonElement?): AstroWebUiRunSolution? {
    if (element == null || element is JsonNull) return null
    val objectValue = element as? JsonObject ?: error("solution must be an object or null")
    val solved = objectValue["solved"]?.let {
        val primitive = it as? JsonPrimitive ?: error("solution.solved must be a JSON boolean")
        require(!primitive.isString) { "solution.solved must be a JSON boolean" }
        when (primitive.content) {
            "true" -> true
            "false" -> false
            else -> error("solution.solved must be a JSON boolean")
        }
    }
    val ra = objectValue["ra_deg"]?.let {
        val primitive = it as? JsonPrimitive ?: error("solution.ra_deg must be a JSON number")
        require(!primitive.isString) { "solution.ra_deg must be a JSON number" }
        primitive.content.toDoubleOrNull() ?: error("solution.ra_deg must be a JSON number")
    }
    val dec = objectValue["dec_deg"]?.let {
        val primitive = it as? JsonPrimitive ?: error("solution.dec_deg must be a JSON number")
        require(!primitive.isString) { "solution.dec_deg must be a JSON number" }
        primitive.content.toDoubleOrNull() ?: error("solution.dec_deg must be a JSON number")
    }
    require((ra == null) == (dec == null)) { "solution coordinates must be supplied together" }
    ra?.let { require(it.isFinite() && it >= 0.0 && it < 360.0) { "solution right ascension is out of range" } }
    dec?.let { require(it.isFinite() && it in -90.0..90.0) { "solution declination is out of range" } }
    return AstroWebUiRunSolution(solved = solved, rightAscensionDeg = ra, declinationDeg = dec)
}

object AstroWebUiRunStateParser {
    fun parse(body: String): AstroWebUiRunStateResult = runCatching {
        val root = AstroWebUiRunStateJson.parser.parseToJsonElement(body) as? JsonObject
            ?: error("response must be a JSON object")
        val status = root.optionalText("status", 32)?.also { value ->
            require(value.isNotBlank() && value.all { it in ' '..'~' }) { "status is invalid" }
        } ?: error("status is missing")
        val elapsed = root.requiredLong("elapsed_sec")
        require(elapsed <= 7L * 24L * 60L * 60L) { "elapsed_sec is out of range" }
        val mountRead = root.requiredBoolean("mount_read")
        val mountBlocked = root.requiredBoolean("mount_blocked")
        require(!(mountRead && mountBlocked)) { "mount_read and mount_blocked cannot both be true" }
        val altitude = root.requiredNumber("alt")
        val azimuth = root.requiredNumber("az")
        require(altitude in -90.0..90.0) { "altitude is out of range" }
        require(azimuth in 0.0..360.0) { "azimuth is out of range" }
        AstroWebUiRunState(
            status = status,
            elapsedSeconds = elapsed,
            mountRead = mountRead,
            mountBlocked = mountBlocked,
            altitudeDeg = altitude,
            azimuthDeg = azimuth,
            aligned = root.requiredBoolean("aligned"),
            tracking = root.requiredBoolean("tracking"),
            solution = parseSolution(root["solution"]),
            log = root.optionalText("log", 4096) ?: "",
        )
    }.fold(
        onSuccess = { AstroWebUiRunStateResult.Success(it) },
        onFailure = { error ->
            AstroWebUiRunStateResult.Failure(
                "Astro WebUI run state is malformed: ${error.message ?: "invalid JSON"}",
            )
        },
    )
}

interface AstroWebUiRunStateClient {
    suspend fun read(host: String, port: Int, readMount: Boolean = false): AstroWebUiRunStateResult
}

class DefaultAstroWebUiRunStateClient : AstroWebUiRunStateClient {
    override suspend fun read(host: String, port: Int, readMount: Boolean): AstroWebUiRunStateResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiRunStateResult.Failure("Astro WebUI host is invalid")
        }
        val path = if (readMount) "/api/state?mount=1" else "/api/state"
        val response = astroWebUiHttpGet(host, port, path, MAX_RUN_STATE_BYTES)
        if (response.error != null) return AstroWebUiRunStateResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiRunStateResult.Failure("Astro WebUI run state HTTP ${response.statusCode}")
        }
        return AstroWebUiRunStateParser.parse(response.body.decodeToString())
    }

    private companion object {
        const val MAX_RUN_STATE_BYTES = 64L * 1024L
    }
}

fun createAstroWebUiRunStateClient(): AstroWebUiRunStateClient = DefaultAstroWebUiRunStateClient()