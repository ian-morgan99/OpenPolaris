package dev.openpolaris.core.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual fun createSupportBundleExportSink(): SupportBundleExportSink =
    SupportBundleExportSink { request ->
        withContext(Dispatchers.IO) {
            val parent = Frame().apply {
                isUndecorated = true
                isVisible = false
            }
            val dialog = FileDialog(parent, "Save OpenPolaris support bundle", FileDialog.SAVE).apply {
                directory = System.getProperty("user.home")
                file = request.suggestedName
            }
            try {
                dialog.isVisible = true
                val directory = dialog.directory
                val name = dialog.file
                if (directory == null || name == null) {
                    SupportBundleExportResult.Failed("save cancelled")
                } else {
                    val output = File(directory, name).canonicalFile
                    output.writeText(request.text)
                    SupportBundleExportResult.Stored(output.absolutePath)
                }
            } catch (error: Exception) {
                SupportBundleExportResult.Failed(
                    "desktop save failed: " +
                        (error.message ?: error::class.simpleName ?: "unknown error"),
                )
            } finally {
                dialog.dispose()
                parent.dispose()
            }
        }
    }
