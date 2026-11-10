package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AstroWebUiAutofocusTest {
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
    fun parserPreservesVcurveProgressAndBestFocus() {
        val result = AstroWebUiAutofocusParser.parse(
            """{"ok":true,"status":"running","progress":{"state":"running","step":4,"steps":12,"best":{"step":3,"score":98.2,"hfr":1.7},"samples":[{"step":0,"score":10,"hfr":4.2},{"step":3,"score":98.2,"hfr":1.7}]}}""",
        )
        val state = assertIs<AstroWebUiAutofocusResult.Success>(result).state
        assertEquals("running", state.status)
        assertEquals(4, state.progress.step)
        assertEquals(12, state.progress.steps)
        assertEquals(3, state.progress.best?.step)
        assertEquals(2, state.progress.samples.size)
    }

    @Test
    fun parserRejectsInvalidProgress() {
        val result = AstroWebUiAutofocusParser.parse(
            """{"ok":true,"status":"running","progress":{"state":"running","step":13,"steps":12,"samples":[]}}""",
        )
        assertIs<AstroWebUiAutofocusResult.Failure>(result)
        assertFailsWith<IllegalArgumentException> {
            AstroWebUiAutofocusParameters(steps = 2)
        }
    }

    @Test
    fun startAndStopUseExplicitSourceForms() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", """{"ok":true,"started":true}""".encodeToByteArray()),
        )
        val client = DefaultAstroWebUiAutofocusClient(transport)
        assertEquals(
            AstroWebUiAutofocusResult.Accepted("start"),
            client.start("192.168.0.1", 8080, AstroWebUiAutofocusParameters(12, 1, 0.8, 3)),
        )
        assertEquals("/api/astro/autofocus", transport.request?.path)
        assertEquals("confirm=1&steps=12&adj=1&settle=0.8&backlash=3", transport.request?.body)
    }

    @Test
    fun rejectedStartIsNotAccepted() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(409, "application/json", """{"ok":false,"error":"autofocus already running"}""".encodeToByteArray()),
        )
        val result = DefaultAstroWebUiAutofocusClient(transport).start(
            "192.168.0.1",
            8080,
            AstroWebUiAutofocusParameters(),
        )
        assertIs<AstroWebUiAutofocusResult.Failure>(result)
    }
}
