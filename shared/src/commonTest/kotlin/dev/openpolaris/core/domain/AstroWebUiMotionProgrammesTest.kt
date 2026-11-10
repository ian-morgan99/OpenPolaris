package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AstroWebUiMotionProgrammesTest {
    private class RecordingTransport(
        private val responses: ArrayDeque<AstroWebUiHttpResponse>,
    ) : AstroWebUiCaptureTransport {
        val requests = mutableListOf<AstroWebUiCaptureHttpRequest>()

        override suspend fun post(request: AstroWebUiCaptureHttpRequest): AstroWebUiHttpResponse {
            requests += request
            return responses.removeFirst()
        }
    }

    @Test
    fun plcKeyAndPreviewUseTheSourceRoutesAndForms() = runTest {
        val transport = RecordingTransport(
            ArrayDeque(
                listOf(
                    AstroWebUiHttpResponse(200, "application/json", """{"ok":true,"keys":1}""".encodeToByteArray()),
                    AstroWebUiHttpResponse(200, "application/json", """{"ok":true,"valid":true,"preview":true,"keys":2,"frame":"@272;"}""".encodeToByteArray()),
                ),
            ),
        )
        val client = DefaultAstroWebUiMotionProgrammeClient(transport)
        assertEquals(
            AstroWebUiMotionProgrammeResult.Count(1),
            client.addPlcKey("192.168.0.1", 8080, AstroWebUiPlcKeyRequest(12.5)),
        )
        val preview = assertIs<AstroWebUiMotionProgrammeResult.Preview>(
            client.previewPlc("192.168.0.1", 8080, AstroWebUiPlcParameters(5.0, 10, hold = true)),
        )
        assertEquals(2, preview.count)
        assertEquals("/api/prog/plc/key", transport.requests[0].path)
        assertEquals("t=12.5", transport.requests[0].body)
        assertEquals("photo_interval=5&photo_count=10&hold=1", transport.requests[1].body)
    }

    @Test
    fun pathLapseStartAddsConfirmAndHolyGrailPreviewIsInferred() = runTest {
        val transport = RecordingTransport(
            ArrayDeque(
                listOf(
                    AstroWebUiHttpResponse(200, "application/json", """{"ok":true,"prog":{}}""".encodeToByteArray()),
                    AstroWebUiHttpResponse(200, "application/json", """{"ok":true,"valid":true,"preview":true,"inferred":true,"nodes":2,"frame":"@305;"}""".encodeToByteArray()),
                ),
            ),
        )
        val client = DefaultAstroWebUiMotionProgrammeClient(transport)
        assertIs<AstroWebUiMotionProgrammeResult.Accepted>(
            client.startPathLapse("192.168.0.1", 8080, AstroWebUiPathLapseParameters(4.0)),
        )
        val preview = assertIs<AstroWebUiMotionProgrammeResult.Preview>(
            client.previewGrail(
                "192.168.0.1",
                8080,
                AstroWebUiGrailParameters(
                    enabled = true,
                    curve = listOf(AstroWebUiGrailNode(0, 0.0), AstroWebUiGrailNode(60, 1.0)),
                    priority = listOf(0, 1, 2),
                    isoRange = 100L..6400L,
                    apertureRange = 2.8..8.0,
                    shutterIndices = listOf(0, 1, 2, 3),
                ),
            ),
        )
        assertEquals(true, preview.inferred)
        assertEquals("interval=4&confirm=1", transport.requests[0].body)
        assertEquals(
            "enable=1&curve=0%2F0%2C60%2F1&priority=0%2C1%2C2&iso_en=1&iso_lo=100&iso_hi=6400&f_en=1&f_lo=2.8&f_hi=8&s_en=1&shutter=0%2C1%2C2%2C3",
            transport.requests[1].body,
        )
    }

    @Test
    fun validationRejectsMalformedCurveAndInvalidCountsBeforeTransport() = runTest {
        assertFailsWith<IllegalArgumentException> {
            AstroWebUiGrailParameters(
                enabled = true,
                curve = listOf(AstroWebUiGrailNode(10, 0.0)),
            )
        }
        assertFailsWith<IllegalArgumentException> { AstroWebUiPlcParameters(1.0, 0) }
        assertFailsWith<IllegalArgumentException> { AstroWebUiPathWaypointRequest(0) }
    }

    @Test
    fun grailDisableAndStatusPreserveExplicitState() = runTest {
        val transport = RecordingTransport(
            ArrayDeque(
                listOf(
                    AstroWebUiHttpResponse(200, "application/json", """{"ok":true,"enabled":false}""".encodeToByteArray()),
                    AstroWebUiHttpResponse(200, "application/json", """{"enabled":true,"brightness":"brightness:2.0;"}""".encodeToByteArray()),
                ),
            ),
        )
        val client = DefaultAstroWebUiMotionProgrammeClient(transport)
        assertEquals(
            AstroWebUiMotionProgrammeResult.Disabled(false),
            client.disableGrail("192.168.0.1", 8080),
        )
        assertEquals(
            AstroWebUiMotionProgrammeResult.Status(true, "brightness:2.0;"),
            client.grailStatus("192.168.0.1", 8080),
        )
        assertEquals("/api/prog/grail/off", transport.requests[0].path)
        assertEquals("/api/prog/grail/status", transport.requests[1].path)
    }
}
