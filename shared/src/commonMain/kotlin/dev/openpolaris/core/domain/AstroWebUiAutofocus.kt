package dev.openpolaris.core.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class AstroWebUiAutofocusParameters(
    val steps: Int = 12,
    val adjustment: Int = 1,
    val settleSeconds: Double = 0.8,
    val backlash: Int = 3,
) {
    init {
        require(steps in 3..40)
        require(adjustment in 1..10)
        require(settleSeconds.isFinite() && settleSeconds in 0.0..5.0)
        require(backlash in 0..20)
    }
}

data class AstroWebUiAutofocusSample(
    val step: Int,
    val score: Double,
    val hfr: Double?,
)

data class AstroWebUiAutofocusBest(
    val step: Int,
    val score: Double,
    val hfr: Double?,
)

data class AstroWebUiAutofocusProgress(
    val state: String,
    val step: Int,
    val steps: Int,
    val best: AstroWebUiAutofocusBest?,
    val samples: List<AstroWebUiAutofocusSample>,
    val error: String?,
)

data class AstroWebUiAutofocusState(
    val status: String,
    val progress: AstroWebUiAutofocusProgress,
)

sealed interface AstroWebUiAutofocusResult {
    data class Success(val state: AstroWebUiAutofocusState) : AstroWebUiAutofocusResult
    data class Accepted(val action: String) : AstroWebUiAutofocusResult
    data class Failure(val reason: String) : AstroWebUiAutofocusResult
}

sealed interface AstroWebUiAutofocusUiState {
    data object Idle : AstroWebUiAutofocusUiState
    data object Loading : AstroWebUiAutofocusUiState
    data class Ready(val state: AstroWebUiAutofocusState) : AstroWebUiAutofocusUiState
    data class Accepted(val action: String) : AstroWebUiAutofocusUiState
    data class Failed(val reason: String) : AstroWebUiAutofocusUiState
}

private object AstroWebUiAutofocusJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

private fun JsonObject.afBoolean(name: String): Boolean {
    val value = this[name] as? JsonPrimitive ?: error("$name must be a JSON boolean")
    require(!value.isString) { "$name must be a JSON boolean" }
    return when (value.content) {
        "true" -> true
        "false" -> false
        else -> error("$name must be a JSON boolean")
    }
}

private fun JsonObject.afNumber(name: String, required: Boolean = true): Double? {
    val value = this[name] ?: if (required) error("$name is missing") else return null
    val primitive = value as? JsonPrimitive ?: error("$name must be a JSON number")
    require(!primitive.isString) { "$name must be a JSON number" }
    return primitive.content.toDoubleOrNull()?.also {
        require(it.isFinite()) { "$name must be finite" }
    } ?: error("$name must be a JSON number")
}

private fun JsonObject.afInt(name: String, required: Boolean = true): Int? {
    val value = afNumber(name, required) ?: return null
    require(value % 1.0 == 0.0 && value >= Int.MIN_VALUE && value <= Int.MAX_VALUE) {
        "$name must be an integer"
    }
    return value.toInt()
}

private fun JsonObject.afText(name: String, maxLength: Int, required: Boolean = true): String? {
    val value = this[name] ?: if (required) error("$name is missing") else return null
    val primitive = value as? JsonPrimitive ?: error("$name must be a JSON string")
    require(primitive.isString) { "$name must be a JSON string" }
    return primitive.content.also { require(it.length <= maxLength) { "$name is too long" } }
}

private fun parseBest(element: kotlinx.serialization.json.JsonElement?): AstroWebUiAutofocusBest? {
    if (element == null || element is JsonNull) return null
    val objectValue = element as? JsonObject ?: error("best must be an object or null")
    val step = objectValue.afInt("step") ?: error("best.step is missing")
    val score = objectValue.afNumber("score") ?: error("best.score is missing")
    val hfr = objectValue.afNumber("hfr", required = false)
    require(step in 0..40 && score >= 0.0 && (hfr == null || hfr >= 0.0)) {
        "best autofocus values are out of range"
    }
    return AstroWebUiAutofocusBest(step, score, hfr)
}

object AstroWebUiAutofocusParser {
    fun parse(body: String): AstroWebUiAutofocusResult = runCatching {
        val root = AstroWebUiAutofocusJson.parser.parseToJsonElement(body) as? JsonObject
            ?: error("response must be a JSON object")
        require(root.afBoolean("ok")) { "response was not acknowledged" }
        val status = root.afText("status", 16) ?: error("status is missing")
        require(status in setOf("idle", "running", "done", "failed")) { "status is invalid" }
        val progress = root["progress"] as? JsonObject ?: error("progress must be an object")
        val state = progress.afText("state", 16) ?: error("progress.state is missing")
        require(state in setOf("idle", "running", "done", "failed")) { "progress.state is invalid" }
        val step = progress.afInt("step") ?: error("progress.step is missing")
        val steps = progress.afInt("steps") ?: error("progress.steps is missing")
        require(step in 0..40 && steps in 0..40 && step <= steps) { "progress step is out of range" }
        val samples = (progress["samples"] as? JsonArray ?: error("progress.samples must be an array"))
            .map { it as? JsonObject ?: error("autofocus sample must be an object") }
            .also { require(it.size <= 40) { "too many autofocus samples" } }
            .map { sample ->
                val sampleStep = sample.afInt("step") ?: error("sample.step is missing")
                val score = sample.afNumber("score") ?: error("sample.score is missing")
                val hfr = sample.afNumber("hfr", required = false)
                require(sampleStep in 0..40 && score >= 0.0 && (hfr == null || hfr >= 0.0)) {
                    "autofocus sample is out of range"
                }
                AstroWebUiAutofocusSample(sampleStep, score, hfr)
            }
        AstroWebUiAutofocusResult.Success(
            AstroWebUiAutofocusState(
                status = status,
                progress = AstroWebUiAutofocusProgress(
                    state = state,
                    step = step,
                    steps = steps,
                    best = parseBest(progress["best"]),
                    samples = samples,
                    error = progress.afText("error", 256, required = false),
                ),
            ),
        )
    }.fold(
        onSuccess = { it },
        onFailure = { error ->
            AstroWebUiAutofocusResult.Failure(
                "Astro WebUI autofocus state is malformed: ${error.message ?: "invalid JSON"}",
            )
        },
    )
}

interface AstroWebUiAutofocusClient {
    suspend fun read(host: String, port: Int): AstroWebUiAutofocusResult
    suspend fun start(host: String, port: Int, parameters: AstroWebUiAutofocusParameters): AstroWebUiAutofocusResult
    suspend fun stop(host: String, port: Int): AstroWebUiAutofocusResult
}

class DefaultAstroWebUiAutofocusClient(
    private val transport: AstroWebUiCaptureTransport,
) : AstroWebUiAutofocusClient {
    override suspend fun read(host: String, port: Int): AstroWebUiAutofocusResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiAutofocusResult.Failure("Astro WebUI host is invalid")
        }
        val response = astroWebUiHttpGet(host, port, "/api/astro/autofocus", MAX_BYTES)
        if (response.error != null) return AstroWebUiAutofocusResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiAutofocusResult.Failure("Astro WebUI autofocus HTTP ${response.statusCode}")
        }
        return AstroWebUiAutofocusParser.parse(response.body.decodeToString())
    }

    override suspend fun start(host: String, port: Int, parameters: AstroWebUiAutofocusParameters) =
        write(host, port, "start", "confirm=1&steps=${parameters.steps}&adj=${parameters.adjustment}&settle=${parameters.settleSeconds}&backlash=${parameters.backlash}")

    override suspend fun stop(host: String, port: Int) =
        write(host, port, "stop", "stop=1")

    private suspend fun write(host: String, port: Int, action: String, body: String): AstroWebUiAutofocusResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiAutofocusResult.Failure("Astro WebUI host is invalid")
        }
        val response = transport.post(
            AstroWebUiCaptureHttpRequest(
                host = host,
                port = port,
                path = "/api/astro/autofocus",
                contentType = "application/x-www-form-urlencoded",
                body = body,
            ),
        )
        if (response.error != null) return AstroWebUiAutofocusResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiAutofocusResult.Failure("Astro WebUI autofocus $action HTTP ${response.statusCode}")
        }
        val root = runCatching {
            AstroWebUiAutofocusJson.parser.parseToJsonElement(response.body.decodeToString()) as? JsonObject
        }.getOrNull() ?: return AstroWebUiAutofocusResult.Failure("Astro WebUI autofocus acknowledgement is malformed")
        if (root["ok"]?.toString() != "true") {
            val error = (root["error"] as? JsonPrimitive)?.content
            return AstroWebUiAutofocusResult.Failure(error ?: "Astro WebUI rejected autofocus $action")
        }
        return AstroWebUiAutofocusResult.Accepted(action)
    }

    private companion object {
        const val MAX_BYTES = 64L * 1024L
    }
}

fun createAstroWebUiAutofocusClient(): AstroWebUiAutofocusClient =
    DefaultAstroWebUiAutofocusClient(createAstroWebUiCaptureTransport())
