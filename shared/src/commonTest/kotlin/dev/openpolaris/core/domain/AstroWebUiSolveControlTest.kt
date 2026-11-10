package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AstroWebUiSolveControlTest {
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
    fun startUsesSourceModeAndApplyQuery() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", "{\"started\":true}".encodeToByteArray()),
        )
        val request = AstroWebUiSolveRequest(AstroWebUiSolveMode.LATEST, apply = true)
        assertEquals(
            AstroWebUiSolveControlResult.Started(request),
            DefaultAstroWebUiSolveControlClient(transport).start("192.168.0.1", 8080, request),
        )
        assertEquals("/api/solve?mode=latest&apply=1", transport.request?.path)
        assertEquals("", transport.request?.body)
    }

    @Test
    fun busySolveIsNotReportedAsStarted() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(
                200,
                "application/json",
                "{\"started\":false,\"reason\":\"a solve is already running\",\"elapsed_sec\":12}".encodeToByteArray(),
            ),
        )
        val result = DefaultAstroWebUiSolveControlClient(transport)
            .start("192.168.0.1", 8080, AstroWebUiSolveRequest(AstroWebUiSolveMode.CAPTURE))
        assertEquals(
            AstroWebUiSolveControlResult.AlreadyRunning("a solve is already running", 12),
            result,
        )
    }

    @Test
    fun applyAndCancelUseExplicitRoutes() = runTest {
        val applyTransport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", "{\"ok\":true,\"age_sec\":4,\"result\":\"aligned\"}".encodeToByteArray()),
        )
        assertEquals(
            AstroWebUiSolveControlResult.Applied(4, "aligned"),
            DefaultAstroWebUiSolveControlClient(applyTransport).apply("192.168.0.1", 8080),
        )
        assertEquals("/api/apply", applyTransport.request?.path)

        val cancelTransport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", "{\"cancelled\":true}".encodeToByteArray()),
        )
        assertEquals(
            AstroWebUiSolveControlResult.Cancelled,
            DefaultAstroWebUiSolveControlClient(cancelTransport).cancel("192.168.0.1", 8080),
        )
        assertEquals("/api/cancel", cancelTransport.request?.path)
    }

    @Test
    fun rejectedApplyAndMissingStartedFailClosed() = runTest {
        val applyTransport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", "{\"ok\":false,\"error\":\"stale\"}".encodeToByteArray()),
        )
        assertIs<AstroWebUiSolveControlResult.Failure>(
            DefaultAstroWebUiSolveControlClient(applyTransport).apply("192.168.0.1", 8080),
        )
        val startTransport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", "{\"ok\":true}".encodeToByteArray()),
        )
        assertIs<AstroWebUiSolveControlResult.Failure>(
            DefaultAstroWebUiSolveControlClient(startTransport).start(
                "192.168.0.1",
                8080,
                AstroWebUiSolveRequest(AstroWebUiSolveMode.WAIT),
            ),
        )
    }
}
