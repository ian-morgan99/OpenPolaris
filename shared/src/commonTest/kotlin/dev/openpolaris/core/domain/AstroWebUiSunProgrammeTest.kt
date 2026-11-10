package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AstroWebUiSunProgrammeTest {
    private class RecordingTransport(
        private val response: AstroWebUiHttpResponse,
    ) : AstroWebUiCaptureTransport {
        val requests = mutableListOf<AstroWebUiCaptureHttpRequest>()

        override suspend fun post(request: AstroWebUiCaptureHttpRequest): AstroWebUiHttpResponse {
            requests += request
            return response
        }
    }

    private val parameters = AstroWebUiSunParameters(
        sunset = false,
        intervalSeconds = 10.0,
        startEpochSeconds = 1_700_000_000L,
        endEpochSeconds = 1_700_003_600L,
        nowEpochSeconds = 1_700_000_100L,
    )

    @Test
    fun previewUsesSourceFieldsAndRequiresPositivePreviewEvidence() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(
                200,
                "application/json",
                """{"ok":true,"valid":true,"preview":true,"frame":"@277;sunset:0;"}""".encodeToByteArray(),
            ),
        )

        val result = DefaultAstroWebUiSunProgrammeClient(transport).preview(
            "192.168.0.1",
            8080,
            parameters,
        )

        val preview = assertIs<AstroWebUiSunResult.Preview>(result)
        assertEquals("@277;sunset:0;", preview.frame)
        assertEquals("/api/prog/sun", transport.requests.single().path)
        assertEquals(
            "sunset=0&interval=10&start=1700000000&end=1700003600&now=1700000100",
            transport.requests.single().body,
        )
    }

    @Test
    fun startAddsExplicitConfirmAndReturnsAcceptedOnlyForPositiveAck() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", """{"ok":true,"prog":{}}""".encodeToByteArray()),
        )

        val result = DefaultAstroWebUiSunProgrammeClient(transport).start(
            "192.168.0.1",
            8080,
            parameters.copy(sunset = true),
        )

        assertIs<AstroWebUiSunResult.Accepted>(result)
        assertEquals(
            "sunset=1&interval=10&start=1700000000&end=1700003600&now=1700000100&confirm=1",
            transport.requests.single().body,
        )
    }

    @Test
    fun invalidWindowAndUnsafeHostNeverReachTransport() = runTest {
        assertFailsWith<IllegalArgumentException> {
            parameters.copy(endEpochSeconds = parameters.startEpochSeconds)
        }
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", """{"ok":true}""".encodeToByteArray()),
        )
        val result = DefaultAstroWebUiSunProgrammeClient(transport).preview(
            "http://192.168.0.1",
            8080,
            parameters,
        )
        assertIs<AstroWebUiSunResult.Failure>(result)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun malformedOrNegativePreviewIsNotAccepted() = runTest {
        val malformed = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", "not-json".encodeToByteArray()),
        )
        assertIs<AstroWebUiSunResult.Failure>(
            DefaultAstroWebUiSunProgrammeClient(malformed).preview("192.168.0.1", 8080, parameters),
        )

        val negative = RecordingTransport(
            AstroWebUiHttpResponse(
                200,
                "application/json",
                """{"ok":true,"valid":false,"preview":true,"error":"outside window"}""".encodeToByteArray(),
            ),
        )
        assertIs<AstroWebUiSunResult.Failure>(
            DefaultAstroWebUiSunProgrammeClient(negative).preview("192.168.0.1", 8080, parameters),
        )
    }
}
