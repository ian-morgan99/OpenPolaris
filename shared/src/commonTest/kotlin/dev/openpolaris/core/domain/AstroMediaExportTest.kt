package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AstroMediaExportTest {
    private class RecordingSink : AstroMediaExportSink {
        val calls = mutableListOf<Pair<AstroMediaExportRequest, ByteArray>>()
        override suspend fun export(
            request: AstroMediaExportRequest,
            bytes: ByteArray,
        ): AstroMediaExportResult {
            calls += request to bytes
            return AstroMediaExportResult.Stored("saved://${request.suggestedName}")
        }
    }

    private fun preview(
        path: String = "starskyStack/ASTRO_0001.jpg",
        kind: AstroMediaTransferKind = AstroMediaTransferKind.FULL_FILE,
        bytes: ByteArray = byteArrayOf(1, 2, 3),
    ) = AstroMediaPreview(path, kind, bytes, "image/jpeg")

    @Test
    fun fullFilePreparesAndExportsExactBytes() = runTest {
        val sink = RecordingSink()
        val controller = AstroMediaExportController(sink)
        assertTrue(controller.prepare(preview(), AstroMediaExportDestination.SAVE_COPY))
        val ready = assertIs<AstroMediaExportState.Ready>(controller.state.value)
        assertEquals("ASTRO_0001.jpg", ready.request.suggestedName)
        assertEquals(3, ready.bytesExpected)

        val complete = assertIs<AstroMediaExportState.Complete>(controller.export(byteArrayOf(1, 2, 3)))
        assertEquals("saved://ASTRO_0001.jpg", complete.location)
        assertEquals(AstroMediaExportDestination.SAVE_COPY, sink.calls.single().first.destination)
        assertTrue(sink.calls.single().second.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun thumbnailAndUnsafePathNeverReachSink() = runTest {
        val sink = RecordingSink()
        val controller = AstroMediaExportController(sink)
        assertFalse(controller.prepare(preview(kind = AstroMediaTransferKind.THUMBNAIL), AstroMediaExportDestination.SHARE))
        assertIs<AstroMediaExportState.Failed>(controller.state.value)
        assertFalse(controller.prepare(preview(path = "../escape.jpg"), AstroMediaExportDestination.SHARE))
        assertTrue(sink.calls.isEmpty())
    }

    @Test
    fun byteMismatchFailsClosedWithoutCallingSink() = runTest {
        val sink = RecordingSink()
        val controller = AstroMediaExportController(sink)
        assertTrue(controller.prepare(preview(), AstroMediaExportDestination.SHARE))
        val failed = assertIs<AstroMediaExportState.Failed>(controller.export(byteArrayOf(9)))
        assertEquals("full-file byte count does not match the prepared result", failed.reason)
        assertTrue(sink.calls.isEmpty())
    }
}
