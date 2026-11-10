package dev.openpolaris.core.domain

/** Media class used by the Gallery UI without making a transfer claim. */
enum class MediaKind(val label: String) {
    IMAGE("Image"),
    VIDEO("Video"),
    OTHER("Other"),
}

/**
 * Safe, mount-local media policy shared by future thumbnail/full-file adapters.
 * It deliberately accepts only the known SD root and rejects traversal, URL
 * schemes, and platform path separators before any transfer implementation can
 * consume a path.
 */
object MediaPolicy {
    private const val MOUNT_MEDIA_ROOT = "/app/sd/"

    fun kind(name: String): MediaKind = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "dng", "nef", "cr2", "cr3", "arw", "tif", "tiff" -> MediaKind.IMAGE
        "mp4", "mov", "avi", "mkv" -> MediaKind.VIDEO
        else -> MediaKind.OTHER
    }

    fun isSafeMountPath(path: String): Boolean {
        val value = path.trim()
        if (!value.startsWith(MOUNT_MEDIA_ROOT)) return false
        if (value.indexOf('\\') >= 0 || value.indexOf('\u0000') >= 0) return false
        val relative = value.removePrefix(MOUNT_MEDIA_ROOT)
        if (relative.isBlank()) return false
        return relative.split('/').all { segment ->
            segment.isNotEmpty() && segment != "." && segment != ".." &&
                !segment.contains(':') && !segment.contains('?') && !segment.contains('#')
        }
    }

    /** A basename safe to hand to a platform save/share surface. */
    fun isSafeExportName(name: String): Boolean {
        val value = name.trim()
        return value.isNotEmpty() && value != "." && value != ".." &&
            !value.contains('/') && !value.contains('\\') &&
            !value.any { it.code == 0 } && !value.contains(':') &&
            !value.contains('?') && !value.contains('#')
    }

    /** Returns the mount-local basename only when the source path is safe. */
    fun exportName(path: String): String? =
        path.trim().takeIf(::isSafeMountPath)
            ?.substringAfterLast('/')
            ?.takeIf(::isSafeExportName)
}
