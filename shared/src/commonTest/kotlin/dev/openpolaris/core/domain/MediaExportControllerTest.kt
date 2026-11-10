package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MediaExportControllerTest {
    private class RecordingSink : MediaExportSink {
        val calls = mutableListOf<Pair<MediaExportRequest, ByteArray>>()

        override suspend fun export(request: MediaExportRequest, bytes: ByteArray): MediaExportResult {
            calls += request to bytes
            return MediaExportResult.Stored("host://saved/" + request.suggestedName)
        }
    }

    private fun completeTransfer(fileId: Int, bytes: Long = 4): MediaTransferState.Complete {
        val handle = MediaTransferHandle(MediaTransferRequest(fileId, MediaTransferKind.FULL_FILE), 9L)
        return MediaTransferState.Complete(handle, bytes)
    }

    @Test
    fun matchingCompleteTransferIsExportedToExplicitSink() = runTest {
        val sink = RecordingSink()
        val controller = MediaExportController(sink)
        val request = MediaExportRequest(7, "/app/sd/normal/SP_0007.dng", MediaExportDestination.SAVE_COPY)

        assertTrue(controller.prepare(request, completeTransfer(7)))
        val result = controller.export(byteArrayOf(1, 2, 3, 4))
        val complete = assertIs<MediaExportState.Complete>(result)
        assertEquals("SP_0007.dng", complete.request.suggestedName)
        assertEquals(1, sink.calls.size)
        assertEquals(MediaExportDestination.SAVE_COPY, sink.calls.single().first.destination)
    }

    @Test
    fun unsafeMountPathNeverReachesSink() = runTest {
        val sink = RecordingSink()
        val controller = MediaExportController(sink)
        val request = MediaExportRequest(8, "/app/sd/../etc/passwd", MediaExportDestination.SHARE)

        assertFalse(controller.prepare(request, completeTransfer(8)))
        assertIs<MediaExportState.Failed>(controller.state.value)
        assertTrue(sink.calls.isEmpty())
    }

    @Test
    fun incompleteOrMismatchedTransferIsRejectedBeforeExport() = runTest {
        val sink = RecordingSink()
        val controller = MediaExportController(sink)
        val request = MediaExportRequest(9, "/app/sd/normal/SP_0009.jpg", MediaExportDestination.SHARE)

        assertFalse(controller.prepare(request, MediaTransferState.OutcomeUnknown(
            MediaTransferHandle(MediaTransferRequest(9, MediaTransferKind.FULL_FILE), 10L),
            "missing end",
        )))
        assertFalse(controller.prepare(request, completeTransfer(10)))
        assertTrue(sink.calls.isEmpty())
    }

    @Test
    fun byteMismatchFailsClosedWithoutCallingSink() = runTest {
        val sink = RecordingSink()
        val controller = MediaExportController(sink)
        val request = MediaExportRequest(11, "/app/sd/normal/SP_0011.jpg", MediaExportDestination.SAVE_COPY)

        assertTrue(controller.prepare(request, completeTransfer(11)))
        val result = controller.export(byteArrayOf(1, 2, 3))
        assertIs<MediaExportState.Failed>(result)
        assertTrue(sink.calls.isEmpty())
    }

    @Test
    fun sinkExceptionBecomesTerminalFailureWithoutRetry() = runTest {
        val sink = MediaExportSink { _, _ -> error("chooser closed") }
        val controller = MediaExportController(sink)
        val request = MediaExportRequest(13, "/app/sd/normal/SP_0013.jpg", MediaExportDestination.SHARE)

        assertTrue(controller.prepare(request, completeTransfer(13)))
        val result = controller.export(byteArrayOf(1, 2, 3, 4))
        val failed = assertIs<MediaExportState.Failed>(result)
        assertTrue(failed.reason.contains("chooser closed"))
        assertIs<MediaExportState.Failed>(controller.state.value)
    }

    @Test
    fun unsafeSuggestedNameIsRejectedEvenWhenSourceIsSafe() {
        val controller = MediaExportController(RecordingSink())
        val request = MediaExportRequest(
            12,
            "/app/sd/normal/SP_0012.jpg",
            MediaExportDestination.SAVE_COPY,
            "../escape.jpg",
        )

        assertFalse(controller.prepare(request, completeTransfer(12)))
        assertIs<MediaExportState.Failed>(controller.state.value)
    }
}
