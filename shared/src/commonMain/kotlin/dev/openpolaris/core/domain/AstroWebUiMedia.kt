package dev.openpolaris.core.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class AstroMediaItem(
    val path: String,
    val category: String,
    val sizeBytes: Long,
    val modifiedEpochSeconds: Long,
)

enum class AstroMediaTransferKind {
    THUMBNAIL,
    FULL_FILE,
}

sealed interface AstroMediaListResult {
    data class Success(val reportedCount: Int, val items: List<AstroMediaItem>) : AstroMediaListResult
    data class Failure(val reason: String) : AstroMediaListResult
}

sealed interface AstroMediaFetchResult {
    data class Success(val bytes: ByteArray, val contentType: String) : AstroMediaFetchResult
    data class Failure(val reason: String) : AstroMediaFetchResult
}

sealed interface AstroMediaState {
    data object Idle : AstroMediaState
    data object LoadingList : AstroMediaState
    data class Ready(val reportedCount: Int) : AstroMediaState
    data class LoadingItem(val path: String, val kind: AstroMediaTransferKind) : AstroMediaState
    data class Failed(val reason: String) : AstroMediaState
}

data class AstroMediaPreview(
    val path: String,
    val kind: AstroMediaTransferKind,
    val bytes: ByteArray,
    val contentType: String,
)

object AstroMediaPolicy {
    const val maxPathLength = 512
    const val maxListBytes = 2L * 1024L * 1024L
    const val maxThumbnailBytes = 4L * 1024L * 1024L
    const val maxFullFileBytes = 64L * 1024L * 1024L

    private val categories = setOf("normal", "starskyStack", "HDR", "panorama", "focusStack", "sun", "Lapse")

    fun isSafeHost(host: String, port: Int): Boolean =
        host.isNotBlank() &&
            host.length <= 255 &&
            host.all { it.isLetterOrDigit() || it == '.' || it == ':' || it == '-' || it == '[' || it == ']' } &&
            port in 1..65535

    fun isSafePath(path: String): Boolean {
        if (path.isEmpty() || path.length > maxPathLength || path.startsWith('/') || path.contains("..")) return false
        if (!path.endsWith(".jpg", ignoreCase = true) && !path.endsWith(".jpeg", ignoreCase = true)) return false
        val segments = path.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return false
        if (segments.first() !in categories) return false
        return segments.all { segment -> segment.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' } }
    }

    fun categoryFor(path: String): String? = path.substringBefore('/').takeIf { it in categories }

    fun labelFor(category: String): String = when (category) {
        "normal" -> "Photos"
        "starskyStack" -> "Astro"
        "HDR" -> "HDR"
        "panorama" -> "Panorama"
        "focusStack" -> "Focus stack"
        "sun" -> "Solar"
        "Lapse" -> "Timelapse"
        else -> category
    }
}

private object AstroMediaJson {
    val parser = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }
}

@Serializable
private data class WireMediaList(
    val ok: Boolean,
    val count: Int,
    val items: List<WireMediaItem>,
)

@Serializable
private data class WireMediaItem(
    val path: String,
    val cat: String,
    val size: Long,
    val mtime: Long,
)

object AstroWebUiMediaParser {
    fun parseList(body: String): AstroMediaListResult = runCatching {
        val wire = AstroMediaJson.parser.decodeFromString<WireMediaList>(body)
        require(wire.ok) { "Astro WebUI reported an unsuccessful media list" }
        require(wire.count >= 0) { "Astro WebUI returned a negative media count" }
        require(wire.items.size <= 400) { "Astro WebUI returned too many media items" }
        val items = wire.items.map { item ->
            require(item.cat == AstroMediaPolicy.categoryFor(item.path)) { "media category does not match path" }
            require(AstroMediaPolicy.isSafePath(item.path)) { "media path is unsafe: ${item.path}" }
            require(item.size >= 0) { "media size is negative" }
            require(item.mtime >= 0) { "media mtime is negative" }
            AstroMediaItem(item.path, item.cat, item.size, item.mtime)
        }
        AstroMediaListResult.Success(wire.count, items)
    }.getOrElse { error ->
        AstroMediaListResult.Failure("Astro WebUI media list is malformed: ${error.message ?: "invalid JSON"}")
    }
}

data class AstroWebUiHttpResponse(
    val statusCode: Int,
    val contentType: String,
    val body: ByteArray,
    val error: String? = null,
)

interface AstroWebUiMediaClient {
    suspend fun list(host: String, port: Int): AstroMediaListResult
    suspend fun fetch(host: String, port: Int, path: String, kind: AstroMediaTransferKind): AstroMediaFetchResult
}

class DefaultAstroWebUiMediaClient : AstroWebUiMediaClient {
    override suspend fun list(host: String, port: Int): AstroMediaListResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) return AstroMediaListResult.Failure("Astro WebUI host is invalid")
        val response = astroWebUiHttpGet(host, port, "/api/media/list", AstroMediaPolicy.maxListBytes)
        if (response.error != null) return AstroMediaListResult.Failure(response.error)
        if (response.statusCode !in 200..299) return AstroMediaListResult.Failure("Astro WebUI media list HTTP ${response.statusCode}")
        return AstroWebUiMediaParser.parseList(response.body.decodeToString())
    }

    override suspend fun fetch(host: String, port: Int, path: String, kind: AstroMediaTransferKind): AstroMediaFetchResult {
        if (!AstroMediaPolicy.isSafeHost(host, port)) return AstroMediaFetchResult.Failure("Astro WebUI host is invalid")
        if (!AstroMediaPolicy.isSafePath(path)) return AstroMediaFetchResult.Failure("Astro WebUI media path is unsafe")
        val endpoint = when (kind) {
            AstroMediaTransferKind.THUMBNAIL -> "/api/media/thumb"
            AstroMediaTransferKind.FULL_FILE -> "/api/media/full"
        }
        val encodedPath = encodeQueryComponent(path)
        val response = astroWebUiHttpGet(
            host,
            port,
            "$endpoint?path=$encodedPath",
            when (kind) {
                AstroMediaTransferKind.THUMBNAIL -> AstroMediaPolicy.maxThumbnailBytes
                AstroMediaTransferKind.FULL_FILE -> AstroMediaPolicy.maxFullFileBytes
            },
        )
        if (response.error != null) return AstroMediaFetchResult.Failure(response.error)
        if (response.statusCode !in 200..299) return AstroMediaFetchResult.Failure("Astro WebUI ${kind.name.lowercase()} HTTP ${response.statusCode}")
        if (response.body.isEmpty()) return AstroMediaFetchResult.Failure("Astro WebUI returned an empty media response")
        return AstroMediaFetchResult.Success(response.body, response.contentType.ifBlank { "image/jpeg" })
    }

    private fun encodeQueryComponent(value: String): String = buildString {
        value.forEach { character ->
            if (character.isLetterOrDigit() || character == '.' || character == '_' || character == '-') {
                append(character)
            } else {
                append('%')
                append(character.code.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
}

expect suspend fun astroWebUiHttpGet(host: String, port: Int, path: String, maxBytes: Long): AstroWebUiHttpResponse

expect fun createAstroWebUiMediaClient(): AstroWebUiMediaClient
