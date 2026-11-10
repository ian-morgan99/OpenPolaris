package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AstroWebUiCaptureTest {
    private class RecordingTransport(
        private val response: AstroWebUiHttpResponse,
    ) : AstroWebUiCaptureTransport {
        val requests = mutableListOf<AstroWebUiCaptureHttpRequest>()

        override suspend fun post(request: AstroWebUiCaptureHttpRequest): AstroWebUiHttpResponse {
            requests += request
            return response
        }
    }

    @Test
    fun manualModeUsesSourceWebUiFormAndReturnsQueuedNotComplete() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", "{\"ok\":true,\"cmd\":264}".encodeToByteArray()),
        )
        val result = AstroWebUiCaptureClient(transport).dispatch("192.168.0.1", 8080, CameraReleaseMode.MANUAL)

        assertIs<AstroWebUiCaptureResult.Queued>(result)
        assertEquals(
            "cmd=264&args=state%3A1%3Bbulb%3A0%3Bc%3A-1%3B&confirm=1",
            transport.requests.single().body,
        )
        assertEquals("/api/link/send", transport.requests.single().path)
        assertEquals("application/x-www-form-urlencoded", transport.requests.single().contentType)
    }

    @Test
    fun bulbAndStopContractRemainExplicitlySeparateFromManual() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", "{\"ok\":true,\"cmd\":264}".encodeToByteArray()),
        )
        val client = AstroWebUiCaptureClient(transport)
        assertIs<AstroWebUiCaptureResult.Queued>(client.dispatch("192.168.0.1", 8080, CameraReleaseMode.BULB, 12))
        assertEquals(
            "cmd=264&args=state%3A1%3Bbulb%3A12%3Bc%3A-1%3B&confirm=1",
            transport.requests.single().body,
        )
    }

    @Test
    fun bulbStopUsesTheExplicitStateZeroEdge() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", "{\"ok\":true,\"cmd\":264}".encodeToByteArray()),
        )
        val result = AstroWebUiCaptureClient(transport).stop("192.168.0.1", 8080)
        assertIs<AstroWebUiCaptureResult.Stopped>(result)
        assertEquals(
            "cmd=264&args=state%3A0%3Bbulb%3A0%3Bc%3A-1%3B&confirm=1",
            transport.requests.single().body,
        )
    }

    @Test
    fun invalidModesAndDurationsNeverReachTransport() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", "{\"ok\":true,\"cmd\":264}".encodeToByteArray()),
        )
        val client = AstroWebUiCaptureClient(transport)
        assertIs<AstroWebUiCaptureResult.Failed>(client.dispatch("192.168.0.1", 8080, CameraReleaseMode.BULB, 0))
        assertIs<AstroWebUiCaptureResult.Failed>(client.dispatch("192.168.0.1", 8080, CameraReleaseMode.OTHER))
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun acknowledgementRequiresTypedBooleanAndExactCommand() = runTest {
        val malformedBodies = listOf(
            "{\"ok\":\"true\",\"cmd\":264}",
            "{\"ok\":true,\"cmd\":2640}",
            "{\"ok\":true}",
            "not-json",
        )
        malformedBodies.forEach { body ->
            val transport = RecordingTransport(
                AstroWebUiHttpResponse(200, "application/json", body.encodeToByteArray()),
            )
            val result = AstroWebUiCaptureClient(transport).dispatch(
                "192.168.0.1",
                8080,
                CameraReleaseMode.MANUAL,
            )
            assertIs<AstroWebUiCaptureResult.Failed>(result)
        }
    }

    @Test
    fun oversizedAcknowledgementIsRejectedBeforeItCanQueueCapture() = runTest {
        val body = "{\"ok\":true,\"cmd\":264}" +
            " ".repeat(ASTRO_CAPTURE_ACK_MAX_BYTES.toInt())
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", body.encodeToByteArray()),
        )
        val result = AstroWebUiCaptureClient(transport).dispatch(
            "192.168.0.1",
            8080,
            CameraReleaseMode.MANUAL,
        )
        assertIs<AstroWebUiCaptureResult.Failed>(result)
    }

    @Test
    fun bridgeFailureIsNotRetriedOrReportedAsCaptureCompletion() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(503, "application/json", "{\"ok\":false}".encodeToByteArray()),
        )
        val result = AstroWebUiCaptureClient(transport).dispatch("192.168.0.1", 8080, CameraReleaseMode.MANUAL)
        assertIs<AstroWebUiCaptureResult.Failed>(result)
        assertEquals(1, transport.requests.size)
    }
}
