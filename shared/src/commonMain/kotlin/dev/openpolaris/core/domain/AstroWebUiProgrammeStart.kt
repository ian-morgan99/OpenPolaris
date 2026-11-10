package dev.openpolaris.core.domain

import kotlinx.serialization.Serializable

/**
 * Source-aligned Astro WebUI programme requests.
 *
 * These are deliberately separate from the local ObservationProgrammePlanner:
 * the resident WebUI owns the camera/mount execution and /api/prog remains the
 * only completion authority. A successful start means that the WebUI accepted
 * the request, not that a frame was captured.
 */
data class AstroWebUiLapseParameters(
    val intervalSeconds: Double,
    val shots: Int,
    val bulbSeconds: Double = 0.0,
    val fps: Int = 24,
)

data class AstroWebUiPanoramaParameters(
    val columns: Int,
    val rows: Int,
    val horizontalStepDegrees: Double,
    val verticalStepDegrees: Double,
    val startDirection: Int = 1,
    val framesPerPosition: Int = 1,
    val intervalSeconds: Int = 0,
    val imageStabilization: Boolean = false,
)

data class AstroWebUiHdrParameters(
    val spread: Int = 3,
    val imageStabilization: Boolean = false,
)

data class AstroWebUiFocusParameters(
    val shots: Int = 14,
    val imageStabilization: Boolean = false,
)

sealed interface AstroWebUiProgrammeStartState {
    data object Idle : AstroWebUiProgrammeStartState
    data object Loading : AstroWebUiProgrammeStartState
    data class Preview(val result: AstroWebUiProgrammeStartResult.Preview) : AstroWebUiProgrammeStartState
    data class Accepted(val kind: AstroWebUiProgrammeStartResult.Kind) : AstroWebUiProgrammeStartState
    data class Failed(val reason: String) : AstroWebUiProgrammeStartState
}

sealed interface AstroWebUiProgrammeStartResult {
    /**
     * A non-committing validation or preview response. [frame] is the exact
     * source-generated wire frame when the WebUI exposes one; it is an
     * inference for panorama and must be shown as such.
     */
    data class Preview(
        val kind: Kind,
        val frame: String? = null,
        val shots: Int? = null,
        val inferred: Boolean = false,
    ) : AstroWebUiProgrammeStartResult

    /** The WebUI accepted a start; /api/prog must be polled for progress. */
    data class Accepted(val kind: Kind) : AstroWebUiProgrammeStartResult

    data class Failure(val reason: String) : AstroWebUiProgrammeStartResult

    enum class Kind {
        LAPSE,
        PANORAMA,
        HDR,
        FOCUS,
    }
}

interface AstroWebUiProgrammeStartClient {
    suspend fun startLapse(
        host: String,
        port: Int,
        parameters: AstroWebUiLapseParameters,
    ): AstroWebUiProgrammeStartResult

    suspend fun previewPanorama(
        host: String,
        port: Int,
        parameters: AstroWebUiPanoramaParameters,
    ): AstroWebUiProgrammeStartResult

    suspend fun startPanorama(
        host: String,
        port: Int,
        parameters: AstroWebUiPanoramaParameters,
    ): AstroWebUiProgrammeStartResult

    suspend fun previewHdr(
        host: String,
        port: Int,
        parameters: AstroWebUiHdrParameters,
    ): AstroWebUiProgrammeStartResult

    suspend fun startHdr(
        host: String,
        port: Int,
        parameters: AstroWebUiHdrParameters,
    ): AstroWebUiProgrammeStartResult

    suspend fun validateFocus(
        host: String,
        port: Int,
        parameters: AstroWebUiFocusParameters,
    ): AstroWebUiProgrammeStartResult

    suspend fun previewFocus(
        host: String,
        port: Int,
        parameters: AstroWebUiFocusParameters,
    ): AstroWebUiProgrammeStartResult

    suspend fun startFocus(
        host: String,
        port: Int,
        parameters: AstroWebUiFocusParameters,
    ): AstroWebUiProgrammeStartResult
}

@Serializable
private data class AstroWebUiProgrammeWriteAck(
    val ok: Boolean,
    val valid: Boolean? = null,
    val preview: Boolean? = null,
    val inferred: Boolean? = null,
    val shots: Int? = null,
    val frame: String? = null,
    val error: String? = null,
)

private object AstroWebUiProgrammeWriteJson {
    val parser = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

class DefaultAstroWebUiProgrammeStartClient(
    private val transport: AstroWebUiCaptureTransport,
) : AstroWebUiProgrammeStartClient {
    override suspend fun startLapse(
        host: String,
        port: Int,
        parameters: AstroWebUiLapseParameters,
    ): AstroWebUiProgrammeStartResult {
        val error = validateLapse(parameters)
        if (error != null) return AstroWebUiProgrammeStartResult.Failure(error)
        return submit(
            host = host,
            port = port,
            path = "/api/prog/lapse",
            body = form(
                "interval" to number(parameters.intervalSeconds),
                "shots" to parameters.shots.toString(),
                "bulb" to number(parameters.bulbSeconds),
                "fps" to parameters.fps.toString(),
                "confirm" to "1",
            ),
            kind = AstroWebUiProgrammeStartResult.Kind.LAPSE,
            action = "timelapse start",
            mode = SubmissionMode.START,
        )
    }

    override suspend fun previewPanorama(
        host: String,
        port: Int,
        parameters: AstroWebUiPanoramaParameters,
    ): AstroWebUiProgrammeStartResult {
        val error = validatePanorama(parameters)
        if (error != null) return AstroWebUiProgrammeStartResult.Failure(error)
        return submit(
            host = host,
            port = port,
            path = "/api/prog/pano",
            body = panoramaForm(parameters),
            kind = AstroWebUiProgrammeStartResult.Kind.PANORAMA,
            action = "panorama preview",
            mode = SubmissionMode.PREVIEW,
            requirePreview = true,
        )
    }

    override suspend fun startPanorama(
        host: String,
        port: Int,
        parameters: AstroWebUiPanoramaParameters,
    ): AstroWebUiProgrammeStartResult {
        val error = validatePanorama(parameters)
        if (error != null) return AstroWebUiProgrammeStartResult.Failure(error)
        return submit(
            host = host,
            port = port,
            path = "/api/prog/pano",
            body = panoramaForm(parameters) + "&confirm=1",
            kind = AstroWebUiProgrammeStartResult.Kind.PANORAMA,
            action = "panorama start",
            mode = SubmissionMode.START,
        )
    }

    override suspend fun previewHdr(
        host: String,
        port: Int,
        parameters: AstroWebUiHdrParameters,
    ): AstroWebUiProgrammeStartResult {
        val error = validateHdr(parameters)
        if (error != null) return AstroWebUiProgrammeStartResult.Failure(error)
        return submit(
            host = host,
            port = port,
            path = "/api/prog/hdr",
            body = form(
                "spread" to parameters.spread.toString(),
                "isp" to bool(parameters.imageStabilization),
            ),
            kind = AstroWebUiProgrammeStartResult.Kind.HDR,
            action = "HDR preview",
            mode = SubmissionMode.PREVIEW,
            requirePreview = true,
        )
    }

    override suspend fun startHdr(
        host: String,
        port: Int,
        parameters: AstroWebUiHdrParameters,
    ): AstroWebUiProgrammeStartResult {
        val error = validateHdr(parameters)
        if (error != null) return AstroWebUiProgrammeStartResult.Failure(error)
        return submit(
            host = host,
            port = port,
            path = "/api/prog/hdr",
            body = form(
                "spread" to parameters.spread.toString(),
                "isp" to bool(parameters.imageStabilization),
                "confirm" to "1",
            ),
            kind = AstroWebUiProgrammeStartResult.Kind.HDR,
            action = "HDR start",
            mode = SubmissionMode.START,
        )
    }

    override suspend fun validateFocus(
        host: String,
        port: Int,
        parameters: AstroWebUiFocusParameters,
    ): AstroWebUiProgrammeStartResult {
        val error = validateFocus(parameters)
        if (error != null) return AstroWebUiProgrammeStartResult.Failure(error)
        return submit(
            host = host,
            port = port,
            path = "/api/prog/focus",
            body = form(
                "shots" to parameters.shots.toString(),
                "isp" to bool(parameters.imageStabilization),
            ),
            kind = AstroWebUiProgrammeStartResult.Kind.FOCUS,
            action = "focus validation",
            mode = SubmissionMode.VALIDATE,
        )
    }

    override suspend fun previewFocus(
        host: String,
        port: Int,
        parameters: AstroWebUiFocusParameters,
    ): AstroWebUiProgrammeStartResult {
        val error = validateFocus(parameters)
        if (error != null) return AstroWebUiProgrammeStartResult.Failure(error)
        return submit(
            host = host,
            port = port,
            path = "/api/prog/focus",
            body = form(
                "shots" to parameters.shots.toString(),
                "isp" to bool(parameters.imageStabilization),
                "preview" to "1",
            ),
            kind = AstroWebUiProgrammeStartResult.Kind.FOCUS,
            action = "focus preview",
            mode = SubmissionMode.PREVIEW,
        )
    }

    override suspend fun startFocus(
        host: String,
        port: Int,
        parameters: AstroWebUiFocusParameters,
    ): AstroWebUiProgrammeStartResult {
        val error = validateFocus(parameters)
        if (error != null) return AstroWebUiProgrammeStartResult.Failure(error)
        return submit(
            host = host,
            port = port,
            path = "/api/prog/focus",
            body = form(
                "shots" to parameters.shots.toString(),
                "isp" to bool(parameters.imageStabilization),
                "confirm" to "1",
            ),
            kind = AstroWebUiProgrammeStartResult.Kind.FOCUS,
            action = "focus start",
            mode = SubmissionMode.START,
        )
    }

    private enum class SubmissionMode {
        VALIDATE,
        PREVIEW,
        START,
    }

    private suspend fun submit(
        host: String,
        port: Int,
        path: String,
        body: String,
        kind: AstroWebUiProgrammeStartResult.Kind,
        action: String,
        mode: SubmissionMode,
        requirePreview: Boolean = false,
    ): AstroWebUiProgrammeStartResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiProgrammeStartResult.Failure("Astro WebUI host is invalid")
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
        if (response.error != null) return AstroWebUiProgrammeStartResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiProgrammeStartResult.Failure(
                "Astro WebUI " + action + " HTTP " + response.statusCode,
            )
        }
        val ack = runCatching {
            AstroWebUiProgrammeWriteJson.parser.decodeFromString<AstroWebUiProgrammeWriteAck>(
                response.body.decodeToString(),
            )
        }.getOrElse { error ->
            return AstroWebUiProgrammeStartResult.Failure(
                "Astro WebUI " + action + " acknowledgement is malformed: " +
                    (error.message ?: "invalid JSON"),
            )
        }
        if (!ack.ok) {
            return AstroWebUiProgrammeStartResult.Failure(
                ack.error?.takeIf { it.isNotBlank() } ?: "Astro WebUI rejected " + action,
            )
        }
        return when (mode) {
            SubmissionMode.START -> AstroWebUiProgrammeStartResult.Accepted(kind)
            SubmissionMode.VALIDATE -> {
                if (ack.valid != true) {
                    AstroWebUiProgrammeStartResult.Failure(
                        "Astro WebUI " + action + " did not return valid=true",
                    )
                } else {
                    AstroWebUiProgrammeStartResult.Preview(kind)
                }
            }
            SubmissionMode.PREVIEW -> {
                if (ack.valid == false || (requirePreview && ack.preview != true)) {
                    AstroWebUiProgrammeStartResult.Failure(
                        ack.error?.takeIf { it.isNotBlank() }
                            ?: "Astro WebUI " + action + " did not return a valid preview",
                    )
                } else {
                    AstroWebUiProgrammeStartResult.Preview(
                        kind = kind,
                        frame = ack.frame,
                        shots = ack.shots,
                        inferred = ack.inferred == true,
                    )
                }
            }
        }
    }

    private fun panoramaForm(parameters: AstroWebUiPanoramaParameters): String = form(
        "cols" to parameters.columns.toString(),
        "rows" to parameters.rows.toString(),
        "hangle" to number(parameters.horizontalStepDegrees),
        "vangle" to number(parameters.verticalStepDegrees),
        "startdir" to parameters.startDirection.toString(),
        "perspot" to parameters.framesPerPosition.toString(),
        "interval" to parameters.intervalSeconds.toString(),
        "isp" to bool(parameters.imageStabilization),
    )

    private fun form(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (key, value) -> key + "=" + encode(value) }

    private fun bool(value: Boolean): String = if (value) "1" else "0"

    private fun number(value: Double): String =
        if (value == kotlin.math.floor(value)) value.toLong().toString() else value.toString()

    private fun encode(value: String): String = buildString {
        value.forEach { character ->
            if (character.isLetterOrDigit() || character == '-' || character == '_' || character == '.' || character == '~') {
                append(character)
            } else {
                append('%')
                append(character.code.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }

    private fun validateLapse(parameters: AstroWebUiLapseParameters): String? {
        if (!parameters.intervalSeconds.isFinite() || parameters.intervalSeconds <= 0.0 || parameters.intervalSeconds > 3600.0) {
            return "interval must be between 0 and 3600 seconds"
        }
        if (parameters.shots != -1 && parameters.shots !in 1..100_000) {
            return "shots must be 1..100000, or unlimited"
        }
        if (!parameters.bulbSeconds.isFinite() || parameters.bulbSeconds < 0.0 || parameters.bulbSeconds > 3600.0) {
            return "bulb must be 0..3600 seconds"
        }
        if (parameters.bulbSeconds > 0.0 && parameters.bulbSeconds >= parameters.intervalSeconds) {
            return "bulb exposure is longer than the interval; raise the interval"
        }
        if (parameters.fps !in 1..240) return "fps must be 1..240"
        return null
    }

    private fun validatePanorama(parameters: AstroWebUiPanoramaParameters): String? {
        if (parameters.columns !in 1..360) return "columns must be 1..360"
        if (parameters.rows !in 1..180) return "rows must be 1..180"
        if (!parameters.horizontalStepDegrees.isFinite() || parameters.horizontalStepDegrees <= 0.0 || parameters.horizontalStepDegrees > 90.0) {
            return "horizontal step must be between 0 and 90 degrees"
        }
        if (!parameters.verticalStepDegrees.isFinite() || parameters.verticalStepDegrees < 0.0 || parameters.verticalStepDegrees > 90.0) {
            return "vertical step must be 0..90 degrees"
        }
        if (parameters.startDirection !in 0..4) return "start corner must be 0..4"
        if (parameters.framesPerPosition !in 1..99) return "frames per position must be 1..99"
        if (parameters.intervalSeconds !in 0..20) return "per-position interval must be 0..20 seconds"
        if (parameters.horizontalStepDegrees * parameters.columns > 360.0 + 1e-6) {
            return "horizontal sweep exceeds 360 degrees"
        }
        if (parameters.verticalStepDegrees * parameters.rows > 180.0 + 1e-6) {
            return "vertical sweep exceeds 180 degrees"
        }
        return null
    }

    private fun validateHdr(parameters: AstroWebUiHdrParameters): String? =
        if (parameters.spread !in 1..30) "bracket spread must be 1..30 shutter steps" else null

    private fun validateFocus(parameters: AstroWebUiFocusParameters): String? =
        if (parameters.shots !in 2..200) "shots must be between 2 and 200" else null
}

fun createAstroWebUiProgrammeStartClient(): AstroWebUiProgrammeStartClient =
    DefaultAstroWebUiProgrammeStartClient(createAstroWebUiCaptureTransport())
