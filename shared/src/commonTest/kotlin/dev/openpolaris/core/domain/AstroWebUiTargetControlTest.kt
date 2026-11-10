package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith

class AstroWebUiTargetControlTest {
    private class RecordingTransport(
        private val response: AstroWebUiHttpResponse,
    ) : AstroWebUiCaptureTransport {
        var request: AstroWebUiCaptureHttpRequest? = null

        override suspend fun post(request: AstroWebUiCaptureHttpRequest): AstroWebUiHttpResponse {
            this.request = request
            return response
        }
    }

    @Test
    fun previewUsesSourceGoToFormAndPositivePreviewEvidence() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(
                200,
                "application/json",
                """{"ok":true,"valid":true,"preview":true,"ra":120.5,"dec":-20.25}""".encodeToByteArray(),
            ),
        )
        val request = AstroWebUiGotoRequest(120.5, -20.25, "Test target")
        val result = DefaultAstroWebUiTargetClient(transport)
            .previewGoto("192.168.0.1", 8080, request)
        assertEquals(AstroWebUiTargetResult.GotoPreview(request), result)
        assertEquals("/api/astro/goto", transport.request?.path)
        assertEquals("ra=120.5&dec=-20.25&name=Test target", transport.request?.body)
    }

    @Test
    fun confirmedGotoAddsExplicitConfirm() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(
                200,
                "application/json",
                """{"ok":true,"slewing":true,"ra":120.5,"dec":-20.25,"name":"Test target"}""".encodeToByteArray(),
            ),
        )
        val request = AstroWebUiGotoRequest(120.5, -20.25, "Test target")
        assertEquals(
            AstroWebUiTargetResult.GotoAccepted(request),
            DefaultAstroWebUiTargetClient(transport).startGoto("192.168.0.1", 8080, request),
        )
        assertEquals("ra=120.5&dec=-20.25&name=Test target&confirm=1", transport.request?.body)
    }

    @Test
    fun compassAlignUsesSourceForm() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", """{"ok":true}""".encodeToByteArray()),
        )
        val request = AstroWebUiAlignRequest(271.25)
        assertEquals(
            AstroWebUiTargetResult.AlignmentAccepted(request),
            DefaultAstroWebUiTargetClient(transport).align("192.168.0.1", 8080, request),
        )
        assertEquals("/api/astro/align", transport.request?.path)
        assertEquals("compass=271.25", transport.request?.body)
    }

    @Test
    fun targetRangesAreFailFast() {
        assertFailsWith<IllegalArgumentException> { AstroWebUiGotoRequest(360.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { AstroWebUiGotoRequest(0.0, 91.0) }
        assertFailsWith<IllegalArgumentException> { AstroWebUiAlignRequest(361.0) }
    }

    @Test
    fun missingPreviewEvidenceIsRejected() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", """{"ok":true}""".encodeToByteArray()),
        )
        assertIs<AstroWebUiTargetResult.Failure>(
            DefaultAstroWebUiTargetClient(transport).previewGoto(
                "192.168.0.1",
                8080,
                AstroWebUiGotoRequest(1.0, 2.0),
            ),
        )
    }
}
