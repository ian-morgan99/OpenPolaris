package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AstroWebUiGuideTest {
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
    fun parserPreservesBoundedGuideStateAndPoints() {
        val result = AstroWebUiGuideParser.parse(
            """{"ok":true,"running":true,"threshold":60,"interval":30,"last_drift":12.3,"points":[{"t":"14:02:11","d":61.2,"c":true},{"t":"14:02:41","d":12.3,"c":false}],"log":"within tolerance"}""",
        )
        val state = assertIs<AstroWebUiGuideResult.Success>(result).state
        assertEquals(true, state.running)
        assertEquals(60.0, state.thresholdArcsec)
        assertEquals(30.0, state.intervalSeconds)
        assertEquals(2, state.points.size)
        assertEquals(12.3, state.lastDriftArcsec)
        assertEquals("within tolerance", state.log)
    }

    @Test
    fun parserTreatsSentinelLastDriftAsUnknown() {
        val result = AstroWebUiGuideParser.parse(
            """{"ok":true,"running":false,"threshold":60,"interval":30,"last_drift":-1,"points":[],"log":""}""",
        )
        assertEquals(
            null,
            assertIs<AstroWebUiGuideResult.Success>(result).state.lastDriftArcsec,
        )
    }

    @Test
    fun malformedGuideStateFailsClosed() {
        val result = AstroWebUiGuideParser.parse(
            """{"ok":true,"running":false,"threshold":60,"interval":30,"last_drift":0,"points":[{"t":"x","d":-1,"c":false}],"log":""}""",
        )
        assertIs<AstroWebUiGuideResult.Failure>(result)
    }

    @Test
    fun setRunningUsesSourceFormAndRequiresPositiveAck() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", """{"ok":true}""".encodeToByteArray()),
        )
        val client = DefaultAstroWebUiGuideClient(transport)
        assertEquals(
            AstroWebUiGuideResult.Accepted(true),
            client.setRunning("192.168.0.1", 8080, true),
        )
        assertEquals("/api/guide", transport.request?.path)
        assertEquals("on=1", transport.request?.body)
    }

    @Test
    fun rejectedGuideWriteIsNotAccepted() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", """{"ok":false,"error":"nothing solved yet"}""".encodeToByteArray()),
        )
        val result = DefaultAstroWebUiGuideClient(transport).setRunning("192.168.0.1", 8080, true)
        assertIs<AstroWebUiGuideResult.Failure>(result)
    }
}
