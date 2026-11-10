package dev.openpolaris.core.domain

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android save-document bridge for the bounded support text. The Activity owns
 * the launcher; this registry owns one pending text export only.
 */
object SupportBundleExportRegistry {
    private data class Pending(
        val request: SupportBundleExportRequest,
        val callback: (SupportBundleExportResult) -> Unit,
    )

    @Volatile private var appContext: Context? = null
    @Volatile private var saveLauncher: ActivityResultLauncher<String>? = null
    private var pending: Pending? = null

    @Synchronized
    fun install(context: Context, save: ActivityResultLauncher<String>) {
        appContext = context.applicationContext
        saveLauncher = save
    }

    @Synchronized
    fun clear() {
        pending?.callback(SupportBundleExportResult.Failed("Android export host was recreated"))
        pending = null
        appContext = null
        saveLauncher = null
    }

    @Synchronized
    fun cancel(callback: (SupportBundleExportResult) -> Unit) {
        if (pending?.callback === callback) pending = null
    }

    fun begin(
        request: SupportBundleExportRequest,
        callback: (SupportBundleExportResult) -> Unit,
    ): Boolean {
        val launcher: ActivityResultLauncher<String>
        synchronized(this) {
            if (pending != null) return false
            launcher = saveLauncher ?: return false
            pending = Pending(request, callback)
        }
        return runCatching {
            launcher.launch(request.suggestedName)
            true
        }.getOrElse {
            complete(SupportBundleExportResult.Failed("could not open Android save chooser"))
            false
        }
    }

    fun completeSave(uri: Uri?) {
        val current = synchronized(this) { pending ?: return }
        if (uri == null) {
            complete(SupportBundleExportResult.Failed("save cancelled"))
            return
        }
        val context = appContext
        val result = if (context == null) {
            SupportBundleExportResult.Failed("Android export host is unavailable")
        } else {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(current.request.text.encodeToByteArray())
                } ?: error("selected destination could not be opened")
                SupportBundleExportResult.Stored(uri.toString())
            }.getOrElse { error ->
                SupportBundleExportResult.Failed(
                    "save failed: " +
                        (error.message ?: error::class.simpleName ?: "unknown error"),
                )
            }
        }
        complete(result)
    }

    @Synchronized
    private fun complete(result: SupportBundleExportResult) {
        val current = pending ?: return
        pending = null
        current.callback(result)
    }
}

actual fun createSupportBundleExportSink(): SupportBundleExportSink =
    SupportBundleExportSink { request ->
        suspendCancellableCoroutine { continuation ->
            val callback: (SupportBundleExportResult) -> Unit = { result ->
                if (continuation.isActive) continuation.resume(result)
            }
            if (!SupportBundleExportRegistry.begin(request, callback)) {
                continuation.resume(
                    SupportBundleExportResult.Failed("Android support export is unavailable or already active"),
                )
            }
            continuation.invokeOnCancellation { SupportBundleExportRegistry.cancel(callback) }
        }
    }
