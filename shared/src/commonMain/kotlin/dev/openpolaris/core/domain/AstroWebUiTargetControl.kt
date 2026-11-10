package dev.openpolaris.core.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class AstroWebUiAlignRequest(val compassDeg: Double) {
    init {
        require(compassDeg.isFinite() && compassDeg in 0.0..360.0)
    }
}

data class AstroWebUiGotoRequest(
    val rightAscensionDeg: Double,
    val declinationDeg: Double,
    val name: String = "",
) {
    init {
        require(rightAscensionDeg.isFinite() && rightAscensionDeg >= 0.0 && rightAscensionDeg < 360.0)
        require(declinationDeg.isFinite() && declinationDeg in -90.0..90.0)
        require(name.length <= 64)
    }
}

sealed interface AstroWebUiTargetResult {
    data class AlignmentAccepted(val request: AstroWebUiAlignRequest) : AstroWebUiTargetResult
    data class GotoPreview(val request: AstroWebUiGotoRequest) : AstroWebUiTargetResult
    data class GotoAccepted(val request: AstroWebUiGotoRequest) : AstroWebUiTargetResult
    data class Failure(val reason: String) : AstroWebUiTargetResult
}

sealed interface AstroWebUiTargetUiState {
    data object Idle : AstroWebUiTargetUiState
    data object Loading : AstroWebUiTargetUiState
    data class Preview(val request: AstroWebUiGotoRequest) : AstroWebUiTargetUiState
    data class Accepted(val action: String) : AstroWebUiTargetUiState
    data class Failed(val reason: String) : AstroWebUiTargetUiState
}

private object AstroWebUiTargetJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

private fun JsonObject.targetBoolean(name: String): Boolean {
    val value = this[name] as? JsonPrimitive ?: error("$name must be a JSON boolean")
    require(!value.isString) { "$name must be a JSON boolean" }
    return when (value.content) {
        "true" -> true
        "false" -> false
        else -> error("$name must be a JSON boolean")
    }
}

private fun parseAck(body: String, action: String): JsonObject = runCatching {
    AstroWebUiTargetJson.parser.parseToJsonElement(body) as? JsonObject
        ?: error("response must be a JSON object")
}.getOrElse { error ->
    throw IllegalArgumentException(
        "Astro WebUI $action acknowledgement is malformed: ${error.message ?: "invalid JSON"}",
    )
}

class DefaultAstroWebUiTargetClient(
    private val transport: AstroWebUiCaptureTransport,
) : AstroWebUiTargetClient {
    override suspend fun align(host: String, port: Int, request: AstroWebUiAlignRequest): AstroWebUiTargetResult =
        post(host, port, "/api/astro/align", "compass=${request.compassDeg}", "align") {
            require(it.targetBoolean("ok"))
            AstroWebUiTargetResult.AlignmentAccepted(request)
        }

    override suspend fun previewGoto(host: String, port: Int, request: AstroWebUiGotoRequest): AstroWebUiTargetResult =
        post(host, port, "/api/astro/goto", gotoBody(request, confirm = false), "goto preview") {
            require(it.targetBoolean("ok"))
            require(it.targetBoolean("valid"))
            require(it.targetBoolean("preview"))
            AstroWebUiTargetResult.GotoPreview(request)
        }

    override suspend fun startGoto(host: String, port: Int, request: AstroWebUiGotoRequest): AstroWebUiTargetResult =
        post(host, port, "/api/astro/goto", gotoBody(request, confirm = true), "goto") {
            require(it.targetBoolean("ok"))
            require(it.targetBoolean("slewing"))
            AstroWebUiTargetResult.GotoAccepted(request)
        }

    private suspend fun post(
        host: String,
        port: Int,
        path: String,
        body: String,
        action: String,
        parse: (JsonObject) -> AstroWebUiTargetResult,
    ): AstroWebUiTargetResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiTargetResult.Failure("Astro WebUI host is invalid")
        }
        val response = transport.post(
            AstroWebUiCaptureHttpRequest(
                host = host,
                port = port,
                path = path,
                contentType = "application/x-www-form-urlencoded",
                body = body,
            ),
        )
        if (response.error != null) return AstroWebUiTargetResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiTargetResult.Failure("Astro WebUI ${action} HTTP ${response.statusCode}")
        }
        return runCatching {
            val root = parseAck(response.body.decodeToString(), action)
            if (root["ok"]?.toString() == "false") {
                val error = (root["error"] as? JsonPrimitive)?.content
                error(error ?: "Astro WebUI rejected $action")
            }
            parse(root)
        }.getOrElse { error ->
            AstroWebUiTargetResult.Failure(error.message ?: "Astro WebUI $action failed")
        }
    }

    private fun gotoBody(request: AstroWebUiGotoRequest, confirm: Boolean): String =
        "ra=${request.rightAscensionDeg}&dec=${request.declinationDeg}" +
            (if (request.name.isNotBlank()) "&name=${request.name}" else "") +
            (if (confirm) "&confirm=1" else "")
}

interface AstroWebUiTargetClient {
    suspend fun align(host: String, port: Int, request: AstroWebUiAlignRequest): AstroWebUiTargetResult
    suspend fun previewGoto(host: String, port: Int, request: AstroWebUiGotoRequest): AstroWebUiTargetResult
    suspend fun startGoto(host: String, port: Int, request: AstroWebUiGotoRequest): AstroWebUiTargetResult
}

fun createAstroWebUiTargetClient(): AstroWebUiTargetClient =
    DefaultAstroWebUiTargetClient(createAstroWebUiCaptureTransport())
