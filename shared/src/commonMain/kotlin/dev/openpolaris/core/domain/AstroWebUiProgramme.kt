package dev.openpolaris.core.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Read-only programme state exposed by the resident Astro WebUI. */
data class AstroWebUiProgramme(
    val kind: String,
    val elapsedSeconds: Double? = null,
    val total: Long? = null,
    val remaining: Long? = null,
    val unlimited: Boolean? = null,
    val paused: Boolean? = null,
    val preview: Boolean? = null,
    val stale: Boolean? = null,
    val taken: Long? = null,
    val columns: Int? = null,
    val rows: Int? = null,
    val perSpot: Int? = null,
    val intervalSeconds: Double? = null,
    val fps: Int? = null,
    val shots: Int? = null,
)

sealed interface AstroWebUiProgrammeResult {
    data class Success(val programme: AstroWebUiProgramme) : AstroWebUiProgrammeResult
    data class Failure(val reason: String) : AstroWebUiProgrammeResult
}

sealed interface AstroWebUiProgrammeState {
    data object Idle : AstroWebUiProgrammeState
    data object Loading : AstroWebUiProgrammeState
    data class Ready(val programme: AstroWebUiProgramme) : AstroWebUiProgrammeState
    data class Failed(val reason: String) : AstroWebUiProgrammeState
}

private object AstroWebUiProgrammeJson {
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}

@Serializable
private data class WireAstroWebUiProgramme(
    val kind: String,
    val elapsed_s: Double? = null,
    val total: Long? = null,
    val remaining: Long? = null,
    val unlimited: Boolean? = null,
    val paused: Boolean? = null,
    val preview: Boolean? = null,
    val stale: Boolean? = null,
    val taken: Long? = null,
    val cols: Int? = null,
    val rows: Int? = null,
    val per_spot: Int? = null,
    val interval_s: Double? = null,
    val fps: Int? = null,
    val shots: Int? = null,
)

object AstroWebUiProgrammeParser {
    fun parse(body: String): AstroWebUiProgrammeResult = runCatching {
        val wire = AstroWebUiProgrammeJson.parser.decodeFromString<WireAstroWebUiProgramme>(body)
        require(wire.kind.isNotBlank()) { "programme kind is blank" }
        if (wire.kind == "none") {
            return@runCatching AstroWebUiProgrammeResult.Success(AstroWebUiProgramme(kind = "none"))
        }
        wire.elapsed_s?.let { require(it.isFinite() && it >= 0.0) { "elapsed time is invalid" } }
        wire.total?.let { require(it >= -1) { "programme total is invalid" } }
        wire.remaining?.let { require(it >= -2) { "programme remaining count is invalid" } }
        wire.taken?.let { require(it >= 0) { "programme taken count is invalid" } }
        wire.cols?.let { require(it > 0) { "programme columns are invalid" } }
        wire.rows?.let { require(it > 0) { "programme rows are invalid" } }
        wire.per_spot?.let { require(it > 0) { "programme per-spot count is invalid" } }
        wire.interval_s?.let { require(it.isFinite() && it >= 0.0) { "programme interval is invalid" } }
        wire.fps?.let { require(it > 0) { "programme fps is invalid" } }
        wire.shots?.let { require(it > 0) { "programme shots are invalid" } }
        AstroWebUiProgrammeResult.Success(
            AstroWebUiProgramme(
                kind = wire.kind,
                elapsedSeconds = wire.elapsed_s,
                total = wire.total,
                remaining = wire.remaining,
                unlimited = wire.unlimited,
                paused = wire.paused,
                preview = wire.preview,
                stale = wire.stale,
                taken = wire.taken,
                columns = wire.cols,
                rows = wire.rows,
                perSpot = wire.per_spot,
                intervalSeconds = wire.interval_s,
                fps = wire.fps,
                shots = wire.shots,
            ),
        )
    }.getOrElse { error ->
        AstroWebUiProgrammeResult.Failure(
            "Astro WebUI programme status is malformed: ${error.message ?: "invalid JSON"}",
        )
    }
}

interface AstroWebUiProgrammeClient {
    suspend fun read(host: String, port: Int): AstroWebUiProgrammeResult
}

class DefaultAstroWebUiProgrammeClient : AstroWebUiProgrammeClient {
    override suspend fun read(host: String, port: Int): AstroWebUiProgrammeResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) {
            return AstroWebUiProgrammeResult.Failure("Astro WebUI host is invalid")
        }
        val response = astroWebUiHttpGet(host, port, "/api/prog", MAX_PROGRAMME_BYTES)
        if (response.error != null) return AstroWebUiProgrammeResult.Failure(response.error)
        if (response.statusCode !in 200..299) {
            return AstroWebUiProgrammeResult.Failure("Astro WebUI programme status HTTP ${response.statusCode}")
        }
        return AstroWebUiProgrammeParser.parse(response.body.decodeToString())
    }

    private companion object {
        const val MAX_PROGRAMME_BYTES = 64L * 1024L
    }
}

fun createAstroWebUiProgrammeClient(): AstroWebUiProgrammeClient = DefaultAstroWebUiProgrammeClient()
