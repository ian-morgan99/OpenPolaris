package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AstroWebUiKeepWifiTest {
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
    fun parserPreservesKeepWifiState() {
        val state = assertIs<AstroWebUiKeepWifiResult.Success>(
            AstroWebUiKeepWifiParser.parse(
                """{"ok":true,"enabled":true,"running":true,"aligned":false,"forced":true}""",
            ),
        ).state
        assertEquals(true, state.enabled)
        assertEquals(true, state.running)
        assertEquals(false, state.aligned)
        assertEquals(true, state.forced)
    }

    @Test
    fun parserRejectsStringBoolean() {
        assertIs<AstroWebUiKeepWifiResult.Failure>(
            AstroWebUiKeepWifiParser.parse(
                """{"ok":true,"enabled":"true","running":true,"aligned":true,"forced":false}""",
            ),
        )
    }

    @Test
    fun setUsesSourceFormsForNormalForceAndOff() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", """{"ok":true}""".encodeToByteArray()),
        )
        val client = DefaultAstroWebUiKeepWifiClient(transport)
        assertEquals(
            AstroWebUiKeepWifiResult.Accepted(true, false),
            client.set("192.168.0.1", 8080, enabled = true, forced = false),
        )
        assertEquals("on=1", transport.request?.body)
        assertEquals(
            AstroWebUiKeepWifiResult.Accepted(true, true),
            client.set("192.168.0.1", 8080, enabled = true, forced = true),
        )
        assertEquals("on=force", transport.request?.body)
        assertEquals(
            AstroWebUiKeepWifiResult.Accepted(false, false),
            client.set("192.168.0.1", 8080, enabled = false, forced = true),
        )
        assertEquals("on=0", transport.request?.body)
    }
}
