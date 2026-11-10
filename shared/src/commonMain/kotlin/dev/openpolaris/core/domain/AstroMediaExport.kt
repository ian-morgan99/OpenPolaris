package dev.openpolaris.core.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User-visible destinations for a verified Astro WebUI full-file result. */
enum class AstroMediaExportDestination {
    SAVE_COPY,
    SHARE,
}

data class AstroMediaExportRequest(
    val sourcePath: String,
    val contentType: String,
    val suggestedName: String,
    val destination: AstroMediaExportDestination,
)

sealed interface AstroMediaExportResult {
    data class Stored(val location: String? = null) : AstroMediaExportResult
    data class Failed(val reason: String) : AstroMediaExportResult
}

/** Platform boundary for a user-approved save/share operation. */
fun interface AstroMediaExportSink {
    suspend fun export(request: AstroMediaExportRequest, bytes: ByteArray): AstroMediaExportResult
}

sealed interface AstroMediaExportState {
    data object Idle : AstroMediaExportState
    data class Ready(
        val request: AstroMediaExportRequest,
        val bytesExpected: Long,
    ) : AstroMediaExportState
    data class Exporting(
        val request: AstroMediaExportRequest,
        val bytesExpected: Long,
    ) : AstroMediaExportState
    data class Complete(
        val request: AstroMediaExportRequest,
        val location: String?,
        val bytesStored: Long,
    ) : AstroMediaExportState
    data class Failed(
        val request: AstroMediaExportRequest?,
        val reason: String,
    ) : AstroMediaExportState
}

/**
 * Binds an explicit full-file preview to a user export action. The controller
 * never exports thumbnails, unsafe paths, empty bytes, or a byte array that
 * differs from the prepared full-file result. It performs no platform I/O.
 */
class AstroMediaExportController(
    private val sink: AstroMediaExportSink,
) {
    private val mutableState = MutableStateFlow<AstroMediaExportState>(AstroMediaExportState.Idle)
    val state: StateFlow<AstroMediaExportState> = mutableState.asStateFlow()

    fun prepare(
        preview: AstroMediaPreview,
        destination: AstroMediaExportDestination,
    ): Boolean {
        if (preview.kind != AstroMediaTransferKind.FULL_FILE) {
            return fail(null, "only a full Astro WebUI file can be exported")
        }
        if (!AstroMediaPolicy.isSafePath(preview.path)) {
            return fail(null, "Astro WebUI media path is unsafe")
        }
        if (preview.bytes.isEmpty()) {
            return fail(null, "empty Astro WebUI media cannot be exported")
        }
        val suggestedName = AstroMediaPolicy.exportName(preview.path)
            ?: return fail(null, "Astro WebUI export filename is unsafe")
        mutableState.value = AstroMediaExportState.Ready(
            request = AstroMediaExportRequest(
                sourcePath = preview.path,
                contentType = preview.contentType.ifBlank { "image/jpeg" },
                suggestedName = suggestedName,
                destination = destination,
            ),
            bytesExpected = preview.bytes.size.toLong(),
        )
        return true
    }

    suspend fun export(bytes: ByteArray): AstroMediaExportState {
        val ready = mutableState.value as? AstroMediaExportState.Ready
            ?: return mutableState.value
        if (bytes.size.toLong() != ready.bytesExpected || bytes.isEmpty()) {
            fail(ready.request, "full-file byte count does not match the prepared result")
            return mutableState.value
        }
        mutableState.value = AstroMediaExportState.Exporting(ready.request, ready.bytesExpected)
        return try {
            when (val result = sink.export(ready.request, bytes)) {
                is AstroMediaExportResult.Stored -> AstroMediaExportState.Complete(
                    request = ready.request,
                    location = result.location,
                    bytesStored = bytes.size.toLong(),
                ).also { mutableState.value = it }
                is AstroMediaExportResult.Failed -> AstroMediaExportState.Failed(
                    ready.request,
                    result.reason,
                ).also { mutableState.value = it }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AstroMediaExportState.Failed(
                ready.request,
                "export sink failed: " + (error.message ?: error::class.simpleName ?: "unknown error"),
            ).also { mutableState.value = it }
        }
    }

    fun reset() {
        mutableState.value = AstroMediaExportState.Idle
    }

    private fun fail(request: AstroMediaExportRequest?, reason: String): Boolean {
        mutableState.value = AstroMediaExportState.Failed(request, reason)
        return false
    }
}

expect fun createAstroMediaExportSink(): AstroMediaExportSink
