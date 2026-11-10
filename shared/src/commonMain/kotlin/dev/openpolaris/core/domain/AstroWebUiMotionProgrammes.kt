package dev.openpolaris.core.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class AstroWebUiPlcKeyRequest(val timeSeconds: Double) {
    init {
        require(timeSeconds.isFinite() && timeSeconds >= 0.0 && timeSeconds <= 86_400.0)
    }
}

data class AstroWebUiPlcParameters(
    val photoIntervalSeconds: Double,
    val photoCount: Int = -1,
    val hold: Boolean = false,
) {
    init {
        require(photoIntervalSeconds.isFinite() && photoIntervalSeconds >= 0.0 && photoIntervalSeconds <= 3600.0)
        require(photoCount == -1 || photoCount in 1..100_000)
    }
}

data class AstroWebUiPathWaypointRequest(val framesOnLeg: Int) {
    init {
        require(framesOnLeg == -1 || framesOnLeg in 1..100_000)
    }
}

data class AstroWebUiPathLapseParameters(val intervalSeconds: Double) {
    init {
        require(intervalSeconds.isFinite() && intervalSeconds > 0.0 && intervalSeconds <= 3600.0)
    }
}

data class AstroWebUiGrailNode(val minutesFromStart: Int, val exposureCompensationEv: Double) {
    init {
        require(minutesFromStart in 0..1440)
        require(exposureCompensationEv.isFinite() && exposureCompensationEv in -5.0..5.0)
    }
}

data class AstroWebUiGrailParameters(
    val enabled: Boolean,
    val curve: List<AstroWebUiGrailNode>,
    val priority: List<Int>? = null,
    val isoRange: LongRange? = null,
    val apertureRange: ClosedFloatingPointRange<Double>? = null,
    val shutterIndices: List<Int>? = null,
) {
    init {
        require(curve.isNotEmpty() && curve.first().minutesFromStart == 0)
        require(curve.zipWithNext().all { (a, b) -> b.minutesFromStart > a.minutesFromStart })
        require(priority == null || priority.size == 3 && priority.toSet() == setOf(0, 1, 2))
        require(isoRange == null || isoRange.first > 0L && isoRange.last >= isoRange.first)
        require(apertureRange == null || apertureRange.start > 0.0 && apertureRange.endInclusive >= apertureRange.start)
        require(shutterIndices == null || shutterIndices.size == 4 && shutterIndices.all { it >= 0 })
    }
}

sealed interface AstroWebUiMotionProgrammeResult {
    data class Count(val count: Int) : AstroWebUiMotionProgrammeResult
    data class Preview(val frame: String, val count: Int? = null, val inferred: Boolean = false) : AstroWebUiMotionProgrammeResult
    data object Accepted : AstroWebUiMotionProgrammeResult
    data class Disabled(val enabled: Boolean) : AstroWebUiMotionProgrammeResult
    data class Status(val enabled: Boolean, val brightness: String?) : AstroWebUiMotionProgrammeResult
    data class Failure(val reason: String) : AstroWebUiMotionProgrammeResult
}

sealed interface AstroWebUiMotionProgrammeState {
    data object Idle : AstroWebUiMotionProgrammeState
    data class Loading(val action: String) : AstroWebUiMotionProgrammeState
    data class Count(val action: String, val count: Int) : AstroWebUiMotionProgrammeState
    data class Preview(
        val action: String,
        val frame: String,
        val count: Int?,
        val inferred: Boolean,
    ) : AstroWebUiMotionProgrammeState
    data class Accepted(val action: String) : AstroWebUiMotionProgrammeState
    data class Disabled(val enabled: Boolean) : AstroWebUiMotionProgrammeState
    data class Status(val enabled: Boolean, val brightness: String?) : AstroWebUiMotionProgrammeState
    data class Failed(val reason: String) : AstroWebUiMotionProgrammeState
}

interface AstroWebUiMotionProgrammeClient {
    suspend fun addPlcKey(host: String, port: Int, request: AstroWebUiPlcKeyRequest): AstroWebUiMotionProgrammeResult
    suspend fun clearPlcKeys(host: String, port: Int): AstroWebUiMotionProgrammeResult
    suspend fun previewPlc(host: String, port: Int, parameters: AstroWebUiPlcParameters): AstroWebUiMotionProgrammeResult
    suspend fun startPlc(host: String, port: Int, parameters: AstroWebUiPlcParameters): AstroWebUiMotionProgrammeResult
    suspend fun addPathWaypoint(host: String, port: Int, request: AstroWebUiPathWaypointRequest): AstroWebUiMotionProgrammeResult
    suspend fun clearPathWaypoints(host: String, port: Int): AstroWebUiMotionProgrammeResult
    suspend fun previewPathLapse(host: String, port: Int, parameters: AstroWebUiPathLapseParameters): AstroWebUiMotionProgrammeResult
    suspend fun startPathLapse(host: String, port: Int, parameters: AstroWebUiPathLapseParameters): AstroWebUiMotionProgrammeResult
    suspend fun previewGrail(host: String, port: Int, parameters: AstroWebUiGrailParameters): AstroWebUiMotionProgrammeResult
    suspend fun applyGrail(host: String, port: Int, parameters: AstroWebUiGrailParameters): AstroWebUiMotionProgrammeResult
    suspend fun disableGrail(host: String, port: Int): AstroWebUiMotionProgrammeResult
    suspend fun grailStatus(host: String, port: Int): AstroWebUiMotionProgrammeResult
}

@Serializable
private data class WireMotionProgrammeAck(
    val ok: Boolean = true,
    val valid: Boolean? = null,
    val preview: Boolean? = null,
    val inferred: Boolean? = null,
    val frame: String? = null,
    val keys: Int? = null,
    val waypoints: Int? = null,
    val nodes: Int? = null,
    val enabled: Boolean? = null,
    val brightness: String? = null,
    val error: String? = null,
)

private object AstroWebUiMotionProgrammeJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

class DefaultAstroWebUiMotionProgrammeClient(
    private val transport: AstroWebUiCaptureTransport,
) : AstroWebUiMotionProgrammeClient {
    override suspend fun addPlcKey(host: String, port: Int, request: AstroWebUiPlcKeyRequest) =
        submit(host, port, "/api/prog/plc/key", form("t" to number(request.timeSeconds)), AckMode.COUNT_KEYS)

    override suspend fun clearPlcKeys(host: String, port: Int) =
        submit(host, port, "/api/prog/plc/clear", "", AckMode.COUNT_KEYS)

    override suspend fun previewPlc(host: String, port: Int, parameters: AstroWebUiPlcParameters) =
        submit(host, port, "/api/prog/plc", plcForm(parameters), AckMode.PREVIEW)

    override suspend fun startPlc(host: String, port: Int, parameters: AstroWebUiPlcParameters) =
        submit(host, port, "/api/prog/plc", plcForm(parameters) + "&confirm=1", AckMode.START)

    override suspend fun addPathWaypoint(host: String, port: Int, request: AstroWebUiPathWaypointRequest) =
        submit(host, port, "/api/prog/pathlapse/wp", form("count" to request.framesOnLeg.toString()), AckMode.COUNT_WAYPOINTS)

    override suspend fun clearPathWaypoints(host: String, port: Int) =
        submit(host, port, "/api/prog/pathlapse/clear", "", AckMode.COUNT_WAYPOINTS)

    override suspend fun previewPathLapse(host: String, port: Int, parameters: AstroWebUiPathLapseParameters) =
        submit(host, port, "/api/prog/pathlapse", form("interval" to number(parameters.intervalSeconds)), AckMode.PREVIEW)

    override suspend fun startPathLapse(host: String, port: Int, parameters: AstroWebUiPathLapseParameters) =
        submit(host, port, "/api/prog/pathlapse", form("interval" to number(parameters.intervalSeconds)) + "&confirm=1", AckMode.START)

    override suspend fun previewGrail(host: String, port: Int, parameters: AstroWebUiGrailParameters) =
        submit(host, port, "/api/prog/grail", grailForm(parameters), AckMode.PREVIEW)

    override suspend fun applyGrail(host: String, port: Int, parameters: AstroWebUiGrailParameters) =
        submit(host, port, "/api/prog/grail", grailForm(parameters) + "&confirm=1", AckMode.APPLY)

    override suspend fun disableGrail(host: String, port: Int) =
        submit(host, port, "/api/prog/grail/off", "", AckMode.DISABLE)

    override suspend fun grailStatus(host: String, port: Int) =
        submit(host, port, "/api/prog/grail/status", "", AckMode.STATUS)

    private enum class AckMode { COUNT_KEYS, COUNT_WAYPOINTS, PREVIEW, START, APPLY, DISABLE, STATUS }

    private suspend fun submit(
        host: String,
        port: Int,
        path: String,
        body: String,
        mode: AckMode,
    ): AstroWebUiMotionProgrammeResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) return AstroWebUiMotionProgrammeResult.Failure("Astro WebUI host is invalid")
        val response = transport.post(
            AstroWebUiCaptureHttpRequest(host, port, path, "application/x-www-form-urlencoded", body),
        )
        response.error?.let { return AstroWebUiMotionProgrammeResult.Failure(it) }
        if (response.statusCode !in 200..299) {
            return AstroWebUiMotionProgrammeResult.Failure("Astro WebUI $path HTTP ${response.statusCode}")
        }
        val ack = runCatching {
            AstroWebUiMotionProgrammeJson.parser.decodeFromString<WireMotionProgrammeAck>(response.body.decodeToString())
        }.getOrElse { error ->
            return AstroWebUiMotionProgrammeResult.Failure("Astro WebUI $path acknowledgement is malformed: ${error.message ?: "invalid JSON"}")
        }
        if (!ack.ok && mode != AckMode.STATUS) {
            return AstroWebUiMotionProgrammeResult.Failure(ack.error?.takeIf { it.isNotBlank() } ?: "Astro WebUI rejected $path")
        }
        return when (mode) {
            AckMode.COUNT_KEYS -> AstroWebUiMotionProgrammeResult.Count(ack.keys ?: return missing("keys", path))
            AckMode.COUNT_WAYPOINTS -> AstroWebUiMotionProgrammeResult.Count(ack.waypoints ?: return missing("waypoints", path))
            AckMode.PREVIEW -> {
                if (ack.valid != true || ack.preview != true) return missingPreview(path, ack.error)
                AstroWebUiMotionProgrammeResult.Preview(
                    frame = ack.frame ?: return missing("frame", path),
                    count = ack.keys ?: ack.waypoints ?: ack.nodes,
                    inferred = ack.inferred == true,
                )
            }
            AckMode.START -> AstroWebUiMotionProgrammeResult.Accepted
            AckMode.APPLY -> AstroWebUiMotionProgrammeResult.Disabled(ack.enabled ?: return missing("enabled", path))
            AckMode.DISABLE -> AstroWebUiMotionProgrammeResult.Disabled(false)
            AckMode.STATUS -> AstroWebUiMotionProgrammeResult.Status(ack.enabled ?: false, ack.brightness)
        }
    }

    private fun missing(field: String, path: String): AstroWebUiMotionProgrammeResult =
        AstroWebUiMotionProgrammeResult.Failure("Astro WebUI $path acknowledgement omitted $field")

    private fun missingPreview(path: String, error: String?): AstroWebUiMotionProgrammeResult =
        AstroWebUiMotionProgrammeResult.Failure(error?.takeIf { it.isNotBlank() } ?: "Astro WebUI $path did not return a valid preview")

    private fun plcForm(parameters: AstroWebUiPlcParameters): String = form(
        "photo_interval" to number(parameters.photoIntervalSeconds),
        "photo_count" to parameters.photoCount.toString(),
        "hold" to bool(parameters.hold),
    )

    private fun grailForm(parameters: AstroWebUiGrailParameters): String = buildList {
        add("enable" to bool(parameters.enabled))
        add("curve" to parameters.curve.joinToString(",") { it.minutesFromStart.toString() + "/" + number(it.exposureCompensationEv) })
        parameters.priority?.let { add("priority" to it.joinToString(",")) }
        parameters.isoRange?.let { add("iso_en" to "1"); add("iso_lo" to it.first.toString()); add("iso_hi" to it.last.toString()) }
        parameters.apertureRange?.let { add("f_en" to "1"); add("f_lo" to number(it.start)); add("f_hi" to number(it.endInclusive)) }
        parameters.shutterIndices?.let { add("s_en" to "1"); add("shutter" to it.joinToString(",")) }
    }.joinToString("&") { (key, value) -> key + "=" + encode(value) }

    private fun form(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (key, value) -> key + "=" + encode(value) }

    private fun bool(value: Boolean): String = if (value) "1" else "0"

    private fun number(value: Double): String =
        if (value == kotlin.math.floor(value)) value.toLong().toString() else value.toString()

    private fun encode(value: String): String = buildString {
        value.forEach { character ->
            if (character.isLetterOrDigit() || character == '-' || character == '_' || character == '.' || character == '~') append(character)
            else append('%').append(character.code.toString(16).uppercase().padStart(2, '0'))
        }
    }
}

fun createAstroWebUiMotionProgrammeClient(): AstroWebUiMotionProgrammeClient =
    DefaultAstroWebUiMotionProgrammeClient(createAstroWebUiCaptureTransport())
