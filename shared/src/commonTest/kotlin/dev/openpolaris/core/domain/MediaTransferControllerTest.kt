package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaTransferControllerTest {
    @Test
    fun explicitChunksCompleteAndExposeOnlyCounters() {
        val controller = MediaTransferController(maxBytes = 100)
        val handle = requireNotNull(controller.begin(MediaTransferRequest(7, MediaTransferKind.THUMBNAIL)))
        assertTrue(controller.acceptChunk(handle, 12))
        assertTrue(controller.acceptChunk(handle, 8))
        assertTrue(controller.complete(handle))
        val state = assertIs<MediaTransferState.Complete>(controller.state.value)
        assertEquals(20, state.bytesReceived)
    }

    @Test
    fun staleHandleCannotAlterNewRequest() {
        val controller = MediaTransferController()
        val old = requireNotNull(controller.begin(MediaTransferRequest(1, MediaTransferKind.FULL_FILE)))
        controller.reset()
        val current = requireNotNull(controller.begin(MediaTransferRequest(2, MediaTransferKind.FULL_FILE)))
        assertTrue(!controller.acceptChunk(old, 10))
        assertIs<MediaTransferState.Requested>(controller.state.value)
        assertEquals(current, (controller.state.value as MediaTransferState.Requested).handle)
    }

    @Test
    fun oversizedTransferFailsClosedAndCannotRetry() {
        val controller = MediaTransferController(maxBytes = 10)
        val handle = requireNotNull(controller.begin(MediaTransferRequest(3, MediaTransferKind.FULL_FILE)))
        assertTrue(!controller.acceptChunk(handle, 11))
        val state = assertIs<MediaTransferState.Failed>(controller.state.value)
        assertTrue(!state.retryAllowed)
        assertNull(controller.retry())
    }

    @Test
    fun unknownOutcomeDoesNotAutomaticallyRetry() {
        val controller = MediaTransferController()
        val handle = requireNotNull(controller.begin(MediaTransferRequest(4, MediaTransferKind.FULL_FILE)))
        assertTrue(controller.markUnknown(handle, "missing end event"))
        assertIs<MediaTransferState.OutcomeUnknown>(controller.state.value)
        assertNull(controller.retry())
    }

    @Test
    fun explicitRetryIsOnlyAllowedForFailedIdempotentRequest() {
        val controller = MediaTransferController()
        val handle = requireNotNull(controller.begin(MediaTransferRequest(5, MediaTransferKind.THUMBNAIL)))
        assertTrue(controller.fail(handle, "temporary transport error"))
        val retry = requireNotNull(controller.retry())
        assertTrue(retry.token > handle.token)
        assertIs<MediaTransferState.Requested>(controller.state.value)
    }
}
