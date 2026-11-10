package dev.openpolaris.core.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private object AstroWebUiCaptureAckParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }

    /**
     * /api/link/send acknowledges a queued bridge write. Require a parsed JSON
     * object, a real boolean true and the exact 264 command; substring checks
     * could accept a malformed response or a command such as 2640.
     */
    fun isPositive(body: ByteArray): Boolean {
        if (body.size.toLong() > ASTRO_CAPTURE_ACK_MAX_BYTES) return false
        return runCatching {
            val root = json.parseToJsonElement(body.decodeToString()) as? JsonObject
                ?: return@runCatching false
            val ok = (root["ok"] as? JsonPrimitive)?.let {
                !it.isString && it.content == "true"
            } ?: false
            val command = (root["cmd"] as? JsonPrimitive)?.let {
                !it.isString && it.content.toIntOrNull() == AstroCaptureContract.CAPTURE_CODE
            } ?: false
            ok && command
        }.getOrDefault(false)
    }
}

/** Result of the resident Astro WebUI link bridge, not of the camera. */
sealed interface AstroWebUiCaptureResult {
    /** The HTTP bridge accepted one 264 write; lifecycle/file evidence is still pending. */
    data class Queued(val mode: CameraReleaseMode, val command: Int) : AstroWebUiCaptureResult
    data class Stopped(val command: Int) : AstroWebUiCaptureResult
    data class Failed(val reason: String) : AstroWebUiCaptureResult
}

data class AstroWebUiCaptureHttpRequest(
    val host: String,
    val port: Int,
    val path: String,
    val contentType: String,
    val body: String,
)

fun interface AstroWebUiCaptureTransport {
    suspend fun post(request: AstroWebUiCaptureHttpRequest): AstroWebUiHttpResponse
}

/**
 * Source-aligned adapter for `/api/link/send`'s ordinary camera release.
 *
 * The source WebUI sends a form POST with `cmd=264`, the Benro Connect
 * `state/bulb/c` payload and `confirm=1`. The server returns after queuing the
 * write. This adapter therefore returns [AstroWebUiCaptureResult.Queued], never
 * a completed capture; the caller must correlate camera lifecycle and file
 * publication through [CaptureEvidenceGate].
 */
class AstroWebUiCaptureClient(
    private val transport: AstroWebUiCaptureTransport,
) {
    suspend fun dispatch(
        host: String,
        port: Int,
        mode: CameraReleaseMode,
        bulbSeconds: Int = 0,
    ): AstroWebUiCaptureResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiCaptureResult.Failed("Astro WebUI host is invalid")
        }
        val payload = when (mode) {
            CameraReleaseMode.MANUAL -> {
                if (bulbSeconds != 0) return AstroWebUiCaptureResult.Failed("M mode cannot carry a Bulb duration")
                AstroCaptureContract.CAPTURE_PAYLOAD
            }
            CameraReleaseMode.BULB -> {
                if (bulbSeconds <= 0) return AstroWebUiCaptureResult.Failed("Bulb duration must be positive")
                "state:1;bulb:$bulbSeconds;c:-1;"
            }
            CameraReleaseMode.OTHER,
            CameraReleaseMode.UNKNOWN,
            -> return AstroWebUiCaptureResult.Failed("Astro WebUI release mode is not qualified")
        }
        val response = transport.post(
            AstroWebUiCaptureHttpRequest(
                host = host,
                port = port,
                path = "/api/link/send",
                contentType = "application/x-www-form-urlencoded",
                body = "cmd=264&args=${formEncode(payload)}&confirm=1",
            ),
        )
        if (response.error != null) return AstroWebUiCaptureResult.Failed(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiCaptureResult.Failed("Astro WebUI link/send HTTP ${response.statusCode}")
        }
        if (!AstroWebUiCaptureAckParser.isPositive(response.body)) {
            return AstroWebUiCaptureResult.Failed("Astro WebUI did not acknowledge the 264 write")
        }
        return AstroWebUiCaptureResult.Queued(mode, AstroCaptureContract.CAPTURE_CODE)
    }

    suspend fun stop(host: String, port: Int): AstroWebUiCaptureResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiCaptureResult.Failed("Astro WebUI host is invalid")
        }
        val response = transport.post(
            AstroWebUiCaptureHttpRequest(
                host = host,
                port = port,
                path = "/api/link/send",
                contentType = "application/x-www-form-urlencoded",
                body = "cmd=264&args=${formEncode("state:0;bulb:0;c:-1;")}&confirm=1",
            ),
        )
        if (response.error != null) return AstroWebUiCaptureResult.Failed(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiCaptureResult.Failed("Astro WebUI link/send HTTP ${response.statusCode}")
        }
        if (!AstroWebUiCaptureAckParser.isPositive(response.body)) {
            return AstroWebUiCaptureResult.Failed("Astro WebUI did not acknowledge the 264 stop write")
        }
        return AstroWebUiCaptureResult.Stopped(AstroCaptureContract.CAPTURE_CODE)
    }

    private fun formEncode(value: String): String = buildString {
        value.forEach { character ->
            if (character.isLetterOrDigit() || character == '-' || character == '_' || character == '.' || character == '~') {
                append(character)
            } else {
                append('%')
                append(character.code.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
}

expect fun createAstroWebUiCaptureTransport(): AstroWebUiCaptureTransport

expect fun createAstroWebUiCaptureClient(): AstroWebUiCaptureClient

internal const val ASTRO_CAPTURE_ACK_MAX_BYTES: Long = 64L * 1024L
