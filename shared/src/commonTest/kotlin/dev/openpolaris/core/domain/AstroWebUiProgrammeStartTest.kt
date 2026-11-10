package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class AstroWebUiProgrammeStartTest {
    private fun client(
        response: String = """{"ok":true,"valid":true,"preview":true,"inferred":true,"shots":6,"frame":"1&271&2&step:1;#"}""",
        requests: MutableList<AstroWebUiCaptureHttpRequest> = mutableListOf(),
    ): Pair<AstroWebUiProgrammeStartClient, MutableList<AstroWebUiCaptureHttpRequest>> =
        DefaultAstroWebUiProgrammeStartClient(
            AstroWebUiCaptureTransport { request ->
                requests += request
                AstroWebUiHttpResponse(200, "application/json", response.encodeToByteArray())
            },
        ) to requests

    @Test
    fun panoramaPreviewUsesSourceFieldsAndPreservesInferredFrame() = runTest {
        val (client, requests) = client()

        val result = client.previewPanorama(
            "192.168.0.1",
            8080,
            AstroWebUiPanoramaParameters(
                columns = 2,
                rows = 3,
                horizontalStepDegrees = 30.0,
                verticalStepDegrees = 10.0,
                startDirection = 1,
                framesPerPosition = 1,
                intervalSeconds = 2,
                imageStabilization = true,
            ),
        )

        val preview = assertIs<AstroWebUiProgrammeStartResult.Preview>(result)
        assertEquals(true, preview.inferred)
        assertEquals("/api/prog/pano", requests.single().path)
        assertEquals(
            "cols=2&rows=3&hangle=30&vangle=10&startdir=1&perspot=1&interval=2&isp=1",
            requests.single().body,
        )
    }

    @Test
    fun lapseStartCarriesTheSourceConfirmGuard() = runTest {
        val (client, requests) = client("""{"ok":true,"prog":{"kind":"timelapse"}}""")

        val result = client.startLapse(
            "192.168.0.1",
            8080,
            AstroWebUiLapseParameters(intervalSeconds = 5.0, shots = -1, bulbSeconds = 0.0, fps = 24),
        )

        assertIs<AstroWebUiProgrammeStartResult.Accepted>(result)
        assertEquals("/api/prog/lapse", requests.single().path)
        assertEquals("interval=5&shots=-1&bulb=0&fps=24&confirm=1", requests.single().body)
    }

    @Test
    fun hdrPreviewCarriesNoConfirmAndReturnsTheResolvedFrame() = runTest {
        val (client, requests) = client(
            """{"ok":true,"valid":true,"preview":true,"frame":"1&280&2&shutters: 1/60 / 1/8 / 1/2"}""",
        )

        val result = client.previewHdr("192.168.0.1", 8080, AstroWebUiHdrParameters(spread = 3))

        val preview = assertIs<AstroWebUiProgrammeStartResult.Preview>(result)
        assertEquals("1&280&2&shutters: 1/60 / 1/8 / 1/2", preview.frame)
        assertEquals("spread=3&isp=0", requests.single().body)
    }

    @Test
    fun focusValidationUsesNonCommittingSourceRoute() = runTest {
        val (client, requests) = client("""{"ok":true,"valid":true}""")

        val result = client.validateFocus("192.168.0.1", 8080, AstroWebUiFocusParameters(shots = 14))

        assertIs<AstroWebUiProgrammeStartResult.Preview>(result)
        assertEquals("/api/prog/focus", requests.single().path)
        assertEquals("shots=14&isp=0", requests.single().body)
    }

    @Test
    fun invalidLapseIsRejectedBeforeTransport() = runTest {
        var called = false
        val client = DefaultAstroWebUiProgrammeStartClient(
            AstroWebUiCaptureTransport {
                called = true
                error("transport must not be reached")
            },
        )

        val result = client.startLapse(
            "192.168.0.1",
            8080,
            AstroWebUiLapseParameters(intervalSeconds = 10.0, shots = 2, bulbSeconds = 10.0),
        )

        assertIs<AstroWebUiProgrammeStartResult.Failure>(result)
        assertEquals(false, called)
    }

    @Test
    fun rejectedPreviewAckIsNotAccepted() = runTest {
        val (client, _) = client("""{"ok":false,"valid":false,"error":"camera unavailable"}""")

        val result = client.previewPanorama(
            "192.168.0.1",
            8080,
            AstroWebUiPanoramaParameters(columns = 1, rows = 1, horizontalStepDegrees = 30.0, verticalStepDegrees = 0.0),
        )

        val failure = assertIs<AstroWebUiProgrammeStartResult.Failure>(result)
        assertEquals("camera unavailable", failure.reason)
    }
}
