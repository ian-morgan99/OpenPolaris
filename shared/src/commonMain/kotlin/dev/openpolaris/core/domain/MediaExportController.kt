package dev.openpolaris.core.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MediaExportDestination {
    SAVE_COPY,
    SHARE,
}

data class MediaExportRequest(
    val fileId: Int,
    val sourcePath: String,
    val destination: MediaExportDestination,
    val suggestedName: String? = null,
) {
    init {
        require(fileId >= 0) { "media file id must be non-negative" }
    }
}

sealed interface MediaExportResult {
    data class Stored(val location: String? = null) : MediaExportResult
    data class Failed(val reason: String) : MediaExportResult
}

/** Platform boundary for a user-approved save/share operation. */
fun interface MediaExportSink {
    suspend fun export(request: MediaExportRequest, bytes: ByteArray): MediaExportResult
}

sealed interface MediaExportState {
    data object Idle : MediaExportState
    data class Ready(
        val request: MediaExportRequest,
        val transfer: MediaTransferHandle,
        val bytesExpected: Long,
    ) : MediaExportState
    data class Exporting(
        val request: MediaExportRequest,
        val bytesExpected: Long,
    ) : MediaExportState
    data class Complete(
        val request: MediaExportRequest,
        val location: String?,
        val bytesStored: Long,
    ) : MediaExportState
    data class Failed(
        val request: MediaExportRequest?,
        val reason: String,
    ) : MediaExportState
}

/**
 * Binds a verified media transfer to an explicit user export action.
 * It stores no bytes and performs no filesystem or network I/O.
 */
class MediaExportController(
    private val sink: MediaExportSink,
) {
    private val mutableState = MutableStateFlow<MediaExportState>(MediaExportState.Idle)
    val state: StateFlow<MediaExportState> = mutableState.asStateFlow()

    fun prepare(request: MediaExportRequest, transfer: MediaTransferState): Boolean {
        val name = request.suggestedName ?: MediaPolicy.exportName(request.sourcePath)
        if (!MediaPolicy.isSafeMountPath(request.sourcePath)) {
            return fail(request, "mount media path is not safe")
        }
        if (name == null || !MediaPolicy.isSafeExportName(name)) {
            return fail(request, "export filename is not safe")
        }
        val complete = transfer as? MediaTransferState.Complete
            ?: return fail(request, "media transfer is not complete")
        if (complete.handle.request.fileId != request.fileId) {
            return fail(request, "transfer file id does not match export request")
        }
        if (complete.bytesReceived <= 0L) {
            return fail(request, "empty media cannot be exported")
        }
        mutableState.value = MediaExportState.Ready(
            request = request.copy(suggestedName = name),
            transfer = complete.handle,
            bytesExpected = complete.bytesReceived,
        )
        return true
    }

    suspend fun export(bytes: ByteArray): MediaExportState {
        val ready = mutableState.value as? MediaExportState.Ready
            ?: return mutableState.value
        if (bytes.size.toLong() != ready.bytesExpected) {
            fail(ready.request, "transfer byte count does not match export bytes")
            return mutableState.value
        }
        mutableState.value = MediaExportState.Exporting(ready.request, ready.bytesExpected)
        return try {
            when (val result = sink.export(ready.request, bytes)) {
                is MediaExportResult.Stored -> MediaExportState.Complete(
                    ready.request,
                    result.location,
                    bytes.size.toLong(),
                ).also { mutableState.value = it }
                is MediaExportResult.Failed -> MediaExportState.Failed(
                    ready.request,
                    result.reason,
                ).also { mutableState.value = it }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            MediaExportState.Failed(
                ready.request,
                "export sink failed: " + (error.message ?: error::class.simpleName ?: "unknown error"),
            ).also { mutableState.value = it }
        }
    }

    fun reset() { mutableState.value = MediaExportState.Idle }

    private fun fail(request: MediaExportRequest?, reason: String): Boolean {
        mutableState.value = MediaExportState.Failed(request, reason)
        return false
    }
}
