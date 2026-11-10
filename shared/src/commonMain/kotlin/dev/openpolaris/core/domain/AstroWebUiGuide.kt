package dev.openpolaris.core.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** One bounded drift sample from the resident Astro WebUI guider. */
data class AstroWebUiGuidePoint(
    val time: String,
    val driftArcsec: Double,
    val corrected: Boolean,
)

/** Read-only /api/guide state; it does not prove that a correction reached the mount. */
data class AstroWebUiGuideState(
    val running: Boolean,
    val thresholdArcsec: Double,
    val intervalSeconds: Double,
    val lastDriftArcsec: Double?,
    val points: List<AstroWebUiGuidePoint>,
    val log: String,
)

sealed interface AstroWebUiGuideResult {
    data class Success(val state: AstroWebUiGuideState) : AstroWebUiGuideResult
    data class Accepted(val running: Boolean) : AstroWebUiGuideResult
    data class Failure(val reason: String) : AstroWebUiGuideResult
}

sealed interface AstroWebUiGuideUiState {
    data object Idle : AstroWebUiGuideUiState
    data object Loading : AstroWebUiGuideUiState
    data class Ready(val state: AstroWebUiGuideState) : AstroWebUiGuideUiState
    data class Accepted(val running: Boolean) : AstroWebUiGuideUiState
    data class Failed(val reason: String) : AstroWebUiGuideUiState
}

private object AstroWebUiGuideJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

private fun JsonObject.guideBoolean(name: String): Boolean {
    val value = this[name] as? JsonPrimitive ?: error("$name must be a JSON boolean")
    require(!value.isString) { "$name must be a JSON boolean" }
    return when (value.content) {
        "true" -> true
        "false" -> false
        else -> error("$name must be a JSON boolean")
    }
}

private fun JsonObject.guideNumber(name: String, required: Boolean = true): Double? {
    val value = this[name] ?: if (required) error("$name is missing") else return null
    val primitive = value as? JsonPrimitive ?: error("$name must be a JSON number")
    require(!primitive.isString) { "$name must be a JSON number" }
    return primitive.content.toDoubleOrNull()?.also {
        require(it.isFinite()) { "$name must be finite" }
    } ?: error("$name must be a JSON number")
}

private fun JsonObject.guideText(name: String, maxLength: Int): String {
    val value = this[name] as? JsonPrimitive ?: error("$name must be a JSON string")
    require(value.isString) { "$name must be a JSON string" }
    return value.content.also { require(it.length <= maxLength) { "$name is too long" } }
}

private fun parseGuidePoint(element: JsonObject): AstroWebUiGuidePoint {
    val drift = element.guideNumber("d") ?: error("point drift is missing")
    require(drift >= 0.0) { "point drift is negative" }
    return AstroWebUiGuidePoint(
        time = element.guideText("t", 32),
        driftArcsec = drift,
        corrected = element.guideBoolean("c"),
    )
}

object AstroWebUiGuideParser {
    fun parse(body: String): AstroWebUiGuideResult = runCatching {
        val root = AstroWebUiGuideJson.parser.parseToJsonElement(body) as? JsonObject
            ?: error("response must be a JSON object")
        require(root.guideBoolean("ok")) { "response was not acknowledged" }
        val threshold = root.guideNumber("threshold") ?: error("threshold is missing")
        val interval = root.guideNumber("interval") ?: error("interval is missing")
        require(threshold in 0.0..10_000.0) { "threshold is out of range" }
        require(interval in 0.1..86_400.0) { "interval is out of range" }
        val last = root.guideNumber("last_drift", required = false)
        last?.let { require(it >= -1.0) { "last_drift is out of range" } }
        val points = (root["points"] as? JsonArray ?: error("points must be an array"))
            .map { it as? JsonObject ?: error("guide point must be an object") }
            .also { require(it.size <= 64) { "too many guide points" } }
            .map(::parseGuidePoint)
        AstroWebUiGuideResult.Success(
            AstroWebUiGuideState(
                running = root.guideBoolean("running"),
                thresholdArcsec = threshold,
                intervalSeconds = interval,
                lastDriftArcsec = last?.takeIf { it >= 0.0 },
                points = points,
                log = root.guideText("log", 4096),
            ),
        )
    }.fold(
        onSuccess = { it },
        onFailure = { error ->
            AstroWebUiGuideResult.Failure(
                "Astro WebUI guide state is malformed: ${error.message ?: "invalid JSON"}",
            )
        },
    )
}

interface AstroWebUiGuideClient {
    suspend fun read(host: String, port: Int): AstroWebUiGuideResult
    suspend fun setRunning(host: String, port: Int, running: Boolean): AstroWebUiGuideResult
}

class DefaultAstroWebUiGuideClient(
    private val transport: AstroWebUiCaptureTransport,
) : AstroWebUiGuideClient {
    override suspend fun read(host: String, port: Int): AstroWebUiGuideResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiGuideResult.Failure("Astro WebUI host is invalid")
        }
        val response = astroWebUiHttpGet(host, port, "/api/guide", MAX_BYTES)
        if (response.error != null) return AstroWebUiGuideResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiGuideResult.Failure("Astro WebUI guide HTTP ${response.statusCode}")
        }
        return AstroWebUiGuideParser.parse(response.body.decodeToString())
    }

    override suspend fun setRunning(host: String, port: Int, running: Boolean): AstroWebUiGuideResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiGuideResult.Failure("Astro WebUI host is invalid")
        }
        val response = transport.post(
            AstroWebUiCaptureHttpRequest(
                host = host,
                port = port,
                path = "/api/guide",
                contentType = "application/x-www-form-urlencoded",
                body = "on=" + if (running) "1" else "0",
            ),
        )
        if (response.error != null) return AstroWebUiGuideResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiGuideResult.Failure("Astro WebUI guide write HTTP ${response.statusCode}")
        }
        val body = response.body.decodeToString()
        val acknowledged = runCatching {
            AstroWebUiGuideJson.parser.parseToJsonElement(body) as? JsonObject
        }.getOrNull()
        if (acknowledged?.get("ok")?.jsonPrimitive?.content == "false") {
            val error = (acknowledged["error"] as? JsonPrimitive)?.content
            return AstroWebUiGuideResult.Failure(error ?: "Astro WebUI rejected guide change")
        }
        if (acknowledged?.get("ok")?.jsonPrimitive?.content != "true") {
            return AstroWebUiGuideResult.Failure("Astro WebUI did not acknowledge guide change")
        }
        return AstroWebUiGuideResult.Accepted(running)
    }

    private companion object {
        const val MAX_BYTES = 64L * 1024L
    }
}

fun createAstroWebUiGuideClient(): AstroWebUiGuideClient =
    DefaultAstroWebUiGuideClient(createAstroWebUiCaptureTransport())
