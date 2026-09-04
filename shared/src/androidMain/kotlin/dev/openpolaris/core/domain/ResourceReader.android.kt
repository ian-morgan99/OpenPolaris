package dev.openpolaris.core.domain

import android.content.Context

/**
 * Application context is injected by the Android entry point
 * (typically `MainActivity.onCreate`) via [installResourceContext].
 * We keep the API on [ResourceReader] simple so the common code
 * does not need to know about Android Context.
 */
internal object ResourceContext {
    @Volatile
    var appContext: Context? = null
}

fun installResourceContext(context: Context) {
    ResourceContext.appContext = context.applicationContext
}

actual fun readResourceText(path: String): String? {
    val ctx = ResourceContext.appContext ?: return null
    return try {
        // KMP packages `commonMain/resources/` into the AAR's `assets/`
        // directory by default, so `context.assets.open` is the right
        // path on Android. Returns null if the file is missing.
        ctx.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (_: Exception) {
        null
    }
}

actual fun readResourceBytes(path: String): ByteArray? {
    val ctx = ResourceContext.appContext ?: return null
    return try {
        ctx.assets.open(path).use { it.readBytes() }
    } catch (_: Exception) {
        null
    }
}
