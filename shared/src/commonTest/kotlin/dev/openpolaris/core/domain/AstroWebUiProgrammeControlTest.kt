package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class AstroWebUiProgrammeControlTest {
    @Test
    fun cancelUsesSourceEndpointAndAcceptsPositiveAck() = runTest {
        val requests = mutableListOf<AstroWebUiCaptureHttpRequest>()
        val client = DefaultAstroWebUiProgrammeControlClient(
            AstroWebUiCaptureTransport { request ->
                requests += request
                AstroWebUiHttpResponse(200, "application/json", """{"ok":true}""".encodeToByteArray())
            },
        )

        val result = client.cancel("192.168.0.1", 8080)

        assertIs<AstroWebUiProgrammeControlResult.Accepted>(result)
        assertEquals("/api/prog/cancel", requests.single().path)
        assertEquals("", requests.single().body)
    }

    @Test
    fun panoramaPauseUsesBooleanFormValue() = runTest {
        val requests = mutableListOf<AstroWebUiCaptureHttpRequest>()
        val client = DefaultAstroWebUiProgrammeControlClient(
            AstroWebUiCaptureTransport { request ->
                requests += request
                AstroWebUiHttpResponse(200, "application/json", """{"ok":true}""".encodeToByteArray())
            },
        )

        client.setPanoramaPaused("192.168.0.1", 8080, true)

        assertEquals("/api/prog/pano/pause", requests.single().path)
        assertEquals("paused=1", requests.single().body)
    }

    @Test
    fun rejectedAckIsNotReportedAsAccepted() = runTest {
        val client = DefaultAstroWebUiProgrammeControlClient(
            AstroWebUiCaptureTransport {
                AstroWebUiHttpResponse(
                    409,
                    "application/json",
                    """{"ok":false,"error":"no panorama running"}""".encodeToByteArray(),
                )
            },
        )

        val result = client.setPanoramaPaused("192.168.0.1", 8080, true)

        val failure = assertIs<AstroWebUiProgrammeControlResult.Failure>(result)
        assertEquals("Astro WebUI panorama pause HTTP 409", failure.reason)
    }

    @Test
    fun invalidHostNeverReachesTransport() = runTest {
        var called = false
        val client = DefaultAstroWebUiProgrammeControlClient(
            AstroWebUiCaptureTransport {
                called = true
                error("transport must not be called")
            },
        )

        val result = client.cancel("bad host!", 8080)

        assertIs<AstroWebUiProgrammeControlResult.Failure>(result)
        assertEquals(false, called)
    }
}
