package dev.openpolaris.core.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The two Astro WebUI-style media products, kept separate for retry policy. */
enum class MediaTransferKind {
    THUMBNAIL,
    FULL_FILE,
}

data class MediaTransferRequest(
    val fileId: Int,
    val kind: MediaTransferKind,
    /** A future 772/781 adapter may retry only when the request is idempotent. */
    val idempotent: Boolean = true,
) {
    init { require(fileId >= 0) { "media file id must be non-negative" } }
}

data class MediaTransferHandle(
    val request: MediaTransferRequest,
    val token: Long,
)

sealed interface MediaTransferState {
    data object Idle : MediaTransferState
    data class Requested(val handle: MediaTransferHandle) : MediaTransferState
    data class Receiving(val handle: MediaTransferHandle, val bytesReceived: Long) : MediaTransferState
    data class Complete(val handle: MediaTransferHandle, val bytesReceived: Long) : MediaTransferState
    data class Failed(val handle: MediaTransferHandle, val reason: String, val retryAllowed: Boolean) : MediaTransferState
    /** A lost/ambiguous transfer is never retried automatically. */
    data class OutcomeUnknown(val handle: MediaTransferHandle, val reason: String) : MediaTransferState
}

/**
 * Pure transfer lifecycle for future 772/773/774 and 781/782 adapters.
 * It stores counters, not file bytes, and never opens a socket or writes a
 * local path. The adapter must supply an explicit handle to every event so a
 * late chunk from an older request cannot complete a newer export.
 */
class MediaTransferController(
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    init { require(maxBytes > 0L) }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 512L * 1024L * 1024L
    }

    private val mutableState = MutableStateFlow<MediaTransferState>(MediaTransferState.Idle)
    val state: StateFlow<MediaTransferState> = mutableState.asStateFlow()
    private var nextToken = 1L

    /** Begin an explicit user request; a second active request is rejected. */
    fun begin(request: MediaTransferRequest): MediaTransferHandle? {
        if (mutableState.value.isActive()) return null
        val handle = MediaTransferHandle(request, nextToken++)
        mutableState.value = MediaTransferState.Requested(handle)
        return handle
    }

    fun acceptChunk(handle: MediaTransferHandle, bytes: Long): Boolean {
        if (bytes < 0L) return false
        val current = mutableState.value
        val currentHandle = current.activeHandle() ?: return false
        if (currentHandle != handle) return false
        val received = current.receivedBytes()
        if (bytes > maxBytes - received) {
            mutableState.value = MediaTransferState.Failed(handle, "transfer exceeds ${maxBytes} bytes", retryAllowed = false)
            return false
        }
        mutableState.value = MediaTransferState.Receiving(handle, received + bytes)
        return true
    }

    fun complete(handle: MediaTransferHandle): Boolean {
        val current = mutableState.value
        if (current.activeHandle() != handle) return false
        mutableState.value = MediaTransferState.Complete(handle, current.receivedBytes())
        return true
    }

    fun fail(handle: MediaTransferHandle, reason: String, retryAllowed: Boolean = handle.request.idempotent): Boolean {
        if (mutableState.value.activeHandle() != handle) return false
        mutableState.value = MediaTransferState.Failed(handle, reason, retryAllowed)
        return true
    }

    fun markUnknown(handle: MediaTransferHandle, reason: String): Boolean {
        if (mutableState.value.activeHandle() != handle) return false
        mutableState.value = MediaTransferState.OutcomeUnknown(handle, reason)
        return true
    }

    /** Explicit operator retry only; unknown outcomes require recovery first. */
    fun retry(): MediaTransferHandle? {
        val failed = mutableState.value as? MediaTransferState.Failed ?: return null
        if (!failed.retryAllowed) return null
        return begin(failed.handle.request)
    }

    fun reset() { mutableState.value = MediaTransferState.Idle }

    private fun MediaTransferState.isActive(): Boolean = activeHandle() != null

    private fun MediaTransferState.activeHandle(): MediaTransferHandle? = when (this) {
        MediaTransferState.Idle -> null
        is MediaTransferState.Requested -> handle
        is MediaTransferState.Receiving -> handle
        is MediaTransferState.Complete -> null
        is MediaTransferState.Failed -> null
        is MediaTransferState.OutcomeUnknown -> null
    }

    private fun MediaTransferState.receivedBytes(): Long = when (this) {
        MediaTransferState.Idle -> 0L
        is MediaTransferState.Requested -> 0L
        is MediaTransferState.Receiving -> bytesReceived
        is MediaTransferState.Complete -> bytesReceived
        is MediaTransferState.Failed -> 0L
        is MediaTransferState.OutcomeUnknown -> 0L
    }
}
