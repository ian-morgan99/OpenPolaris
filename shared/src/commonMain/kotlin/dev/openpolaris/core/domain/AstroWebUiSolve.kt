package dev.openpolaris.core.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One bounded, read-only observation from the Astro WebUI solve files. */
data class AstroWebUiSolveRecord(
    val solved: Boolean? = null,
    val rightAscensionDeg: Double? = null,
    val declinationDeg: Double? = null,
    val targetRightAscensionDeg: Double? = null,
    val targetDeclinationDeg: Double? = null,
    val rotationDeg: Double? = null,
    val driftDeg: Double? = null,
    val updated: String? = null,
    val sessionId: String? = null,
    val frame: String? = null,
    val failureReason: String? = null,
    val action: String? = null,
    val matchedCount: Int? = null,
    val rmsPx: Double? = null,
    val logOdds: Double? = null,
)

/** Source-aligned /api/astro/solve state; it does not prove mount alignment. */
data class AstroWebUiSolveState(
    val target: AstroWebUiSolveRecord? = null,
    val lastFrame: AstroWebUiSolveRecord? = null,
)

sealed interface AstroWebUiSolveResult {
    data class Success(val state: AstroWebUiSolveState) : AstroWebUiSolveResult
    data class Failure(val reason: String) : AstroWebUiSolveResult
}

sealed interface AstroWebUiSolveUiState {
    data object Idle : AstroWebUiSolveUiState
    data object Loading : AstroWebUiSolveUiState
    data class Ready(val state: AstroWebUiSolveState) : AstroWebUiSolveUiState
    data class Failed(val reason: String) : AstroWebUiSolveUiState
}

private object AstroWebUiSolveJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

private fun JsonObject.number(name: String): Double? {
    val value = this[name] ?: return null
    val primitive = value as? JsonPrimitive
        ?: error("$name must be a JSON number")
    require(!primitive.isString) { "$name must be a JSON number" }
    return primitive.content.toDoubleOrNull()
        ?: error("$name must be a JSON number")
}

private fun JsonObject.boolean(name: String): Boolean? {
    val value = this[name] ?: return null
    val primitive = value as? JsonPrimitive
        ?: error("$name must be a JSON boolean")
    require(!primitive.isString) { "$name must be a JSON boolean" }
    return when (primitive.content) {
        "true" -> true
        "false" -> false
        else -> error("$name must be a JSON boolean")
    }
}

private fun JsonObject.text(name: String): String? {
    val value = this[name] ?: return null
    val primitive = value as? JsonPrimitive
        ?: error("$name must be a JSON string")
    require(primitive.isString) { "$name must be a JSON string" }
    return primitive.content.takeIf { it.length <= 128 }
        ?: error("$name is too long")
}

private fun JsonObject.integer(name: String): Int? {
    val number = number(name) ?: return null
    require(number.isFinite() && number % 1.0 == 0.0) { "$name must be an integer" }
    return number.toInt().also { require(it.toDouble() == number) { "$name is out of range" } }
}

private fun parseRecord(element: JsonElement?, label: String): AstroWebUiSolveRecord? {
    if (element == null || element is JsonNull) return null
    val objectValue = element as? JsonObject
        ?: error("$label must be an object or null")
    val rightAscension = objectValue.number("ra_deg")
        ?: objectValue.number("solved_ra_deg")
    val declination = objectValue.number("dec_deg")
        ?: objectValue.number("solved_dec_deg")
    val targetRightAscension = objectValue.number("target_ra_deg")
    val targetDeclination = objectValue.number("target_dec_deg")
    require((rightAscension == null) == (declination == null)) {
        "$label solved coordinates must be supplied together"
    }
    require((targetRightAscension == null) == (targetDeclination == null)) {
        "$label target coordinates must be supplied together"
    }
    rightAscension?.let {
        require(it.isFinite() && it >= 0.0 && it < 360.0) {
            "$label right ascension is outside the valid range"
        }
    }
    declination?.let {
        require(it.isFinite() && it in -90.0..90.0) {
            "$label declination is outside the valid range"
        }
    }
    targetRightAscension?.let {
        require(it.isFinite() && it >= 0.0 && it < 360.0) {
            "$label target right ascension is outside the valid range"
        }
    }
    targetDeclination?.let {
        require(it.isFinite() && it in -90.0..90.0) {
            "$label target declination is outside the valid range"
        }
    }
    val rotation = objectValue.number("rotation_deg")
        ?: objectValue.number("roll_deg")
    rotation?.let { require(it.isFinite()) { "$label rotation is invalid" } }
    val drift = objectValue.number("drift_deg")
    drift?.let { require(it.isFinite() && it >= 0.0) { "$label drift is invalid" } }
    val rms = objectValue.number("rms_px")
    rms?.let { require(it.isFinite() && it >= 0.0) { "$label RMS is invalid" } }
    val logOdds = objectValue.number("logodds")
    logOdds?.let { require(it.isFinite()) { "$label log odds is invalid" } }
    val matchedCount = objectValue.integer("matched_count")
        ?: objectValue.integer("nmatch")
    matchedCount?.let { require(it >= 0) { "$label match count is invalid" } }
    return AstroWebUiSolveRecord(
        solved = objectValue.boolean("solved"),
        rightAscensionDeg = rightAscension,
        declinationDeg = declination,
        targetRightAscensionDeg = targetRightAscension,
        targetDeclinationDeg = targetDeclination,
        rotationDeg = rotation,
        driftDeg = drift,
        updated = objectValue.text("updated"),
        sessionId = objectValue.text("session_id"),
        frame = objectValue.text("frame"),
        failureReason = objectValue.text("failure_reason"),
        action = objectValue.text("action"),
        matchedCount = matchedCount,
        rmsPx = rms,
        logOdds = logOdds,
    )
}

object AstroWebUiSolveParser {
    fun parse(body: String): AstroWebUiSolveResult = runCatching {
        val root = AstroWebUiSolveJson.parser.parseToJsonElement(body) as? JsonObject
            ?: error("response must be a JSON object")
        AstroWebUiSolveState(
            target = parseRecord(root["target"], "target"),
            lastFrame = parseRecord(root["last_frame"], "last_frame"),
        )
    }.fold(
        onSuccess = { AstroWebUiSolveResult.Success(it) },
        onFailure = { error ->
            AstroWebUiSolveResult.Failure(
                "Astro WebUI solve state is malformed: ${error.message ?: "invalid JSON"}",
            )
        },
    )
}

interface AstroWebUiSolveClient {
    suspend fun read(host: String, port: Int): AstroWebUiSolveResult
}

class DefaultAstroWebUiSolveClient : AstroWebUiSolveClient {
    override suspend fun read(host: String, port: Int): AstroWebUiSolveResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiSolveResult.Failure("Astro WebUI host is invalid")
        }
        val response = astroWebUiHttpGet(host, port, "/api/astro/solve", MAX_SOLVE_BYTES)
        if (response.error != null) return AstroWebUiSolveResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiSolveResult.Failure("Astro WebUI solve state HTTP ${response.statusCode}")
        }
        return AstroWebUiSolveParser.parse(response.body.decodeToString())
    }

    private companion object {
        const val MAX_SOLVE_BYTES = 64L * 1024L
    }
}

fun createAstroWebUiSolveClient(): AstroWebUiSolveClient = DefaultAstroWebUiSolveClient()
