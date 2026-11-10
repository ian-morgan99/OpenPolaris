package dev.openpolaris.core.domain

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Activity-result bridge for media export. The Android host owns the
 * registered launchers; this registry owns only one bounded export at a time
 * and never exposes a file:// URI to another app.
 */
object AstroMediaExportRegistry {
    private data class Pending(
        val request: AstroMediaExportRequest,
        val bytes: ByteArray,
        val callback: (AstroMediaExportResult) -> Unit,
        val temporaryFile: File? = null,
    )

    @Volatile private var appContext: Context? = null
    @Volatile private var saveLauncher: ActivityResultLauncher<String>? = null
    @Volatile private var shareLauncher: ActivityResultLauncher<Intent>? = null
    private var pending: Pending? = null

    @Synchronized
    fun install(
        context: Context,
        save: ActivityResultLauncher<String>,
        share: ActivityResultLauncher<Intent>,
    ) {
        appContext = context.applicationContext
        saveLauncher = save
        shareLauncher = share
    }

    @Synchronized
    fun clear() {
        pending?.callback(AstroMediaExportResult.Failed("Android export host was recreated"))
        pending?.temporaryFile?.delete()
        pending = null
        appContext = null
        saveLauncher = null
        shareLauncher = null
    }

    @Synchronized
    fun cancel(callback: (AstroMediaExportResult) -> Unit) {
        if (pending?.callback === callback) {
            pending?.temporaryFile?.delete()
            pending = null
        }
    }

    fun begin(
        request: AstroMediaExportRequest,
        bytes: ByteArray,
        callback: (AstroMediaExportResult) -> Unit,
    ): Boolean {
        val context: Context
        val save: ActivityResultLauncher<String>?
        val share: ActivityResultLauncher<Intent>?
        var shareIntent: Intent? = null
        synchronized(this) {
            if (pending != null) return false
            context = appContext ?: return false
            when (request.destination) {
                AstroMediaExportDestination.SAVE_COPY -> {
                    save = saveLauncher ?: return false
                    share = null
                    pending = Pending(request, bytes, callback)
                }
                AstroMediaExportDestination.SHARE -> {
                    save = null
                    share = shareLauncher ?: return false
                    val directory = File(context.cacheDir, "astro_share")
                    if (!directory.mkdirs() && !directory.isDirectory) return false
                    val file = File(directory, request.suggestedName)
                    try {
                        file.outputStream().use { it.write(bytes) }
                    } catch (_: Exception) {
                        return false
                    }
                    val uri = runCatching {
                        FileProvider.getUriForFile(
                            context,
                            context.packageName + ".fileprovider",
                            file,
                        )
                    }.getOrElse {
                        file.delete()
                        return false
                    }
                    shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = request.contentType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }.let { Intent.createChooser(it, "Share Astro image") }
                    pending = Pending(request, bytes, callback, file)
                }
            }
        }
        return runCatching {
            when (request.destination) {
                AstroMediaExportDestination.SAVE_COPY -> save!!.launch(request.suggestedName)
                AstroMediaExportDestination.SHARE -> share!!.launch(requireNotNull(shareIntent))
            }
            true
        }.getOrElse {
            complete(AstroMediaExportResult.Failed("could not open Android export chooser"))
            false
        }
    }

    fun completeSave(uri: Uri?) {
        val current: Pending = synchronized(this) { pending ?: return }
        if (uri == null) {
            complete(AstroMediaExportResult.Failed("save cancelled"))
            return
        }
        val context = appContext
        val result = if (context == null) {
            AstroMediaExportResult.Failed("Android export host is unavailable")
        } else {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(current.bytes) }
                    ?: error("selected destination could not be opened")
                AstroMediaExportResult.Stored(uri.toString())
            }.getOrElse { error ->
                AstroMediaExportResult.Failed(
                    "save failed: ${error.message ?: error::class.simpleName ?: "unknown error"}",
                )
            }
        }
        complete(result)
    }

    fun completeShare(resultCode: Int) {
        complete(
            if (resultCode == Activity.RESULT_OK) {
                AstroMediaExportResult.Stored()
            } else {
                AstroMediaExportResult.Failed("share cancelled or not confirmed")
            },
        )
    }

    @Synchronized
    private fun complete(result: AstroMediaExportResult) {
        val current = pending ?: return
        pending = null
        current.temporaryFile?.delete()
        current.callback(result)
    }
}

actual fun createAstroMediaExportSink(): AstroMediaExportSink = AstroMediaExportSink { request, bytes ->
    suspendCancellableCoroutine { continuation ->
        val callback: (AstroMediaExportResult) -> Unit = { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        val started = AstroMediaExportRegistry.begin(request, bytes, callback)
        if (!started) continuation.resume(AstroMediaExportResult.Failed("Android export is unavailable or already active"))
        continuation.invokeOnCancellation { AstroMediaExportRegistry.cancel(callback) }
    }
}
