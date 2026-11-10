package dev.openpolaris.core.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The focal-length sources reported by the resident Astro WebUI. */
data class AstroWebUiFocalState(
    val overrideMm: Double?,
    val exifCacheMm: Double?,
    val configMm: Double,
)

/** Read/write result for the source-aligned /api/focal contract. */
sealed interface AstroWebUiFocalResult {
    data class Success(val state: AstroWebUiFocalState) : AstroWebUiFocalResult
    data class Accepted(val focalMm: Double?) : AstroWebUiFocalResult
    data class Failure(val reason: String) : AstroWebUiFocalResult
}

sealed interface AstroWebUiFocalUiState {
    data object Idle : AstroWebUiFocalUiState
    data object Loading : AstroWebUiFocalUiState
    data class Ready(val state: AstroWebUiFocalState) : AstroWebUiFocalUiState
    data class Accepted(val focalMm: Double?) : AstroWebUiFocalUiState
    data class Failed(val reason: String) : AstroWebUiFocalUiState
}

private object AstroWebUiFocalJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

private fun JsonObject.focalBoolean(name: String): Boolean {
    val value = this[name] as? JsonPrimitive ?: error("$name must be a JSON boolean")
    require(!value.isString) { "$name must be a JSON boolean" }
    return when (value.content) {
        "true" -> true
        "false" -> false
        else -> error("$name must be a JSON boolean")
    }
}

private fun JsonObject.focalMm(name: String, nullable: Boolean): Double? {
    val element = this[name] ?: if (nullable) return null else error("$name is missing")
    if (element is JsonNull) {
        require(nullable) { "$name must not be null" }
        return null
    }
    val primitive = element as? JsonPrimitive ?: error("$name must be a JSON number or numeric string")
    val value = primitive.content.toDoubleOrNull() ?: error("$name must be numeric")
    require(value.isFinite() && value in 1.0..100_000.0) { "$name is out of range" }
    return value
}

/**
 * Parser for the resident server response. The source emits override and
 * exif_cache as JSON strings when present, and null otherwise; accepting a
 * numeric primitive as well keeps the client forward-compatible without
 * weakening the range check.
 */
object AstroWebUiFocalParser {
    fun parse(body: String): AstroWebUiFocalResult = runCatching {
        val root = AstroWebUiFocalJson.parser.parseToJsonElement(body) as? JsonObject
            ?: error("response must be a JSON object")
        require(root.focalBoolean("ok")) { "response was not acknowledged" }
        AstroWebUiFocalState(
            overrideMm = root.focalMm("override", nullable = true),
            exifCacheMm = root.focalMm("exif_cache", nullable = true),
            configMm = root.focalMm("config", nullable = false) ?: error("config is missing"),
        )
    }.fold(
        onSuccess = { AstroWebUiFocalResult.Success(it) },
        onFailure = { error ->
            AstroWebUiFocalResult.Failure(
                "Astro WebUI focal state is malformed: ${error.message ?: "invalid JSON"}",
            )
        },
    )
}

interface AstroWebUiFocalClient {
    suspend fun read(host: String, port: Int): AstroWebUiFocalResult
    suspend fun setOverride(host: String, port: Int, focalMm: Double?): AstroWebUiFocalResult
}

class DefaultAstroWebUiFocalClient(
    private val transport: AstroWebUiCaptureTransport,
) : AstroWebUiFocalClient {
    override suspend fun read(host: String, port: Int): AstroWebUiFocalResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiFocalResult.Failure("Astro WebUI host is invalid")
        }
        val response = astroWebUiHttpGet(host, port, "/api/focal", MAX_BYTES)
        if (response.error != null) return AstroWebUiFocalResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiFocalResult.Failure("Astro WebUI focal HTTP ${response.statusCode}")
        }
        return AstroWebUiFocalParser.parse(response.body.decodeToString())
    }

    override suspend fun setOverride(host: String, port: Int, focalMm: Double?): AstroWebUiFocalResult {
        if (focalMm != null && (focalMm.isNaN() || focalMm.isInfinite() || focalMm !in 1.0..100_000.0)) {
            return AstroWebUiFocalResult.Failure("focal override must be 1-100000 mm, or auto")
        }
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiFocalResult.Failure("Astro WebUI host is invalid")
        }
        val response = transport.post(
            AstroWebUiCaptureHttpRequest(
                host = host,
                port = port,
                path = "/api/focal",
                contentType = "application/x-www-form-urlencoded",
                body = "focal=" + (focalMm?.toString() ?: "auto"),
            ),
        )
        if (response.error != null) return AstroWebUiFocalResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiFocalResult.Failure("Astro WebUI focal write HTTP ${response.statusCode}")
        }
        val root = runCatching {
            AstroWebUiFocalJson.parser.parseToJsonElement(response.body.decodeToString()) as? JsonObject
        }.getOrNull() ?: return AstroWebUiFocalResult.Failure("Astro WebUI focal acknowledgement is malformed")
        if (root["ok"]?.toString() != "true") {
            val error = (root["error"] as? JsonPrimitive)?.content
            return AstroWebUiFocalResult.Failure(error ?: "Astro WebUI rejected focal override")
        }
        return AstroWebUiFocalResult.Accepted(focalMm)
    }

    private companion object {
        const val MAX_BYTES = 16L * 1024L
    }
}

fun createAstroWebUiFocalClient(): AstroWebUiFocalClient =
    DefaultAstroWebUiFocalClient(createAstroWebUiCaptureTransport())
