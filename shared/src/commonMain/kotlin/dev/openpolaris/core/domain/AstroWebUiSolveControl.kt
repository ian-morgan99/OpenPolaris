package dev.openpolaris.core.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class AstroWebUiSolveMode(val wireValue: String) {
    LATEST("latest"),
    CAPTURE("capture"),
    WAIT("wait"),
}

data class AstroWebUiSolveRequest(
    val mode: AstroWebUiSolveMode,
    val apply: Boolean = false,
)

sealed interface AstroWebUiSolveControlResult {
    data class Started(val request: AstroWebUiSolveRequest) : AstroWebUiSolveControlResult
    data class AlreadyRunning(val reason: String, val elapsedSeconds: Long?) : AstroWebUiSolveControlResult
    data class Applied(val ageSeconds: Long, val result: String?) : AstroWebUiSolveControlResult
    data object Cancelled : AstroWebUiSolveControlResult
    data class NothingToCancel(val reason: String) : AstroWebUiSolveControlResult
    data class Failure(val reason: String) : AstroWebUiSolveControlResult
}

sealed interface AstroWebUiSolveControlUiState {
    data object Idle : AstroWebUiSolveControlUiState
    data object Loading : AstroWebUiSolveControlUiState
    data class Accepted(val action: String) : AstroWebUiSolveControlUiState
    data class Failed(val reason: String) : AstroWebUiSolveControlUiState
}

private object AstroWebUiSolveControlJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

private fun JsonObject.boolean(name: String): Boolean? {
    val value = this[name] ?: return null
    val primitive = value as? JsonPrimitive ?: error("$name must be a JSON boolean")
    require(!primitive.isString) { "$name must be a JSON boolean" }
    return when (primitive.content) {
        "true" -> true
        "false" -> false
        else -> error("$name must be a JSON boolean")
    }
}

private fun JsonObject.text(name: String): String? {
    val value = this[name] ?: return null
    val primitive = value as? JsonPrimitive ?: error("$name must be a JSON string")
    require(primitive.isString) { "$name must be a JSON string" }
    return primitive.content.takeIf { it.length <= 512 } ?: error("$name is too long")
}

private fun JsonObject.long(name: String): Long? {
    val value = this[name] ?: return null
    val primitive = value as? JsonPrimitive ?: error("$name must be a JSON number")
    require(!primitive.isString) { "$name must be a JSON number" }
    return primitive.content.toLongOrNull()?.also { require(it >= 0) { "$name must be non-negative" } }
        ?: error("$name must be a JSON integer")
}

private fun parseObject(body: String, action: String): JsonObject = runCatching {
    AstroWebUiSolveControlJson.parser.parseToJsonElement(body) as? JsonObject
        ?: error("response must be a JSON object")
}.getOrElse { error ->
    throw IllegalArgumentException(
        "Astro WebUI $action acknowledgement is malformed: ${error.message ?: "invalid JSON"}",
    )
}

class DefaultAstroWebUiSolveControlClient(
    private val transport: AstroWebUiCaptureTransport,
) : AstroWebUiSolveControlClient {
    override suspend fun start(
        host: String,
        port: Int,
        request: AstroWebUiSolveRequest,
    ): AstroWebUiSolveControlResult = post(
        host = host,
        port = port,
        path = "/api/solve?mode=${request.mode.wireValue}&apply=${if (request.apply) 1 else 0}",
        action = "solve",
    ) { root ->
        when (root.boolean("started")) {
            true -> AstroWebUiSolveControlResult.Started(request)
            false -> AstroWebUiSolveControlResult.AlreadyRunning(
                reason = root.text("reason") ?: "a solve is already running",
                elapsedSeconds = root.long("elapsed_sec"),
            )
            null -> error("solve response must include started")
        }
    }

    override suspend fun apply(
        host: String,
        port: Int,
    ): AstroWebUiSolveControlResult = post(
        host = host,
        port = port,
        path = "/api/apply",
        action = "apply",
    ) { root ->
        require(root.boolean("ok") == true)
        AstroWebUiSolveControlResult.Applied(
            ageSeconds = root.long("age_sec") ?: error("apply response must include age_sec"),
            result = root.text("result"),
        )
    }

    override suspend fun cancel(
        host: String,
        port: Int,
    ): AstroWebUiSolveControlResult = post(
        host = host,
        port = port,
        path = "/api/cancel",
        action = "cancel",
    ) { root ->
        when (root.boolean("cancelled")) {
            true -> AstroWebUiSolveControlResult.Cancelled
            false -> AstroWebUiSolveControlResult.NothingToCancel(
                root.text("reason") ?: "nothing is running",
            )
            null -> error("cancel response must include cancelled")
        }
    }

    private suspend fun post(
        host: String,
        port: Int,
        path: String,
        action: String,
        parse: (JsonObject) -> AstroWebUiSolveControlResult,
    ): AstroWebUiSolveControlResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiSolveControlResult.Failure("Astro WebUI host is invalid")
        }
        val response = transport.post(
            AstroWebUiCaptureHttpRequest(
                host = host,
                port = port,
                path = path,
                contentType = "application/x-www-form-urlencoded",
                body = "",
            ),
        )
        if (response.error != null) return AstroWebUiSolveControlResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiSolveControlResult.Failure("Astro WebUI $action HTTP ${response.statusCode}")
        }
        return runCatching {
            val root = parseObject(response.body.decodeToString(), action)
            if (root.boolean("ok") == false) {
                error(root.text("error") ?: "Astro WebUI rejected $action")
            }
            parse(root)
        }.getOrElse { error ->
            AstroWebUiSolveControlResult.Failure(error.message ?: "Astro WebUI $action failed")
        }
    }
}

interface AstroWebUiSolveControlClient {
    suspend fun start(host: String, port: Int, request: AstroWebUiSolveRequest): AstroWebUiSolveControlResult
    suspend fun apply(host: String, port: Int): AstroWebUiSolveControlResult
    suspend fun cancel(host: String, port: Int): AstroWebUiSolveControlResult
}

fun createAstroWebUiSolveControlClient(): AstroWebUiSolveControlClient =
    DefaultAstroWebUiSolveControlClient(createAstroWebUiCaptureTransport())
