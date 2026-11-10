package dev.openpolaris.core.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Desktop save is native; desktop share stays explicit until a host share API exists. */
actual fun createAstroMediaExportSink(): AstroMediaExportSink = AstroMediaExportSink { request, bytes ->
    when (request.destination) {
        AstroMediaExportDestination.SHARE -> AstroMediaExportResult.Failed(
            "desktop sharing is not available; use Save copy",
        )
        AstroMediaExportDestination.SAVE_COPY -> withContext(Dispatchers.IO) {
            val parent = Frame().apply {
                isUndecorated = true
                isVisible = false
            }
            val dialog = FileDialog(parent, "Save Astro media", FileDialog.SAVE).apply {
                directory = System.getProperty("user.home")
                file = request.suggestedName
            }
            try {
                dialog.isVisible = true
                val directory = dialog.directory
                val name = dialog.file
                if (directory == null || name == null) {
                    AstroMediaExportResult.Failed("save cancelled")
                } else {
                    val output = File(directory, name).canonicalFile
                    output.outputStream().use { it.write(bytes) }
                    AstroMediaExportResult.Stored(output.absolutePath)
                }
            } catch (error: Exception) {
                AstroMediaExportResult.Failed(
                    "desktop save failed: ${error.message ?: error::class.simpleName ?: "unknown error"}",
                )
            } finally {
                dialog.dispose()
                parent.dispose()
            }
        }
    }
}
