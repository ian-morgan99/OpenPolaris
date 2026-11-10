package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class AstroWebUiAstroCaptureTest {
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
    fun startUsesSourceRouteAndExplicitConfirmation() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(
                statusCode = 200,
                contentType = "application/json",
                body = """{"ok":true,"prog":{"kind":"lapse","elapsed_s":0,"remaining":4}}""".encodeToByteArray(),
            ),
        )
        val client = DefaultAstroWebUiAstroCaptureClient(transport)

        val result = client.start(
            "192.168.0.1",
            8080,
            AstroWebUiAstroCaptureParameters(intervalSeconds = 8, shots = -1, bulbSeconds = 0.0),
        )

        assertIs<AstroWebUiAstroCaptureResult.Accepted>(result)
        assertEquals("/api/astro/capture", transport.request?.path)
        assertEquals("interval=8&shots=&bulb=0&confirm=1", transport.request?.body)
        assertEquals("lapse", result.programme?.kind)
        assertEquals(4L, result.programme?.remaining)
    }

    @Test
    fun rejectedOrMalformedAcknowledgementDoesNotClaimAStart() = runTest {
        val rejected = DefaultAstroWebUiAstroCaptureClient(
            RecordingTransport(
                AstroWebUiHttpResponse(
                    statusCode = 409,
                    contentType = "application/json",
                    body = """{"ok":false,"error":"already running"}""".encodeToByteArray(),
                ),
            ),
        ).start("192.168.0.1", 8080, AstroWebUiAstroCaptureParameters(8))
        assertIs<AstroWebUiAstroCaptureResult.Failure>(rejected)
        assertEquals("Astro WebUI Astro capture HTTP 409", rejected.reason)

        val malformed = DefaultAstroWebUiAstroCaptureClient(
            RecordingTransport(
                AstroWebUiHttpResponse(
                    statusCode = 200,
                    contentType = "application/json",
                    body = "not-json".encodeToByteArray(),
                ),
            ),
        ).start("192.168.0.1", 8080, AstroWebUiAstroCaptureParameters(8))
        assertIs<AstroWebUiAstroCaptureResult.Failure>(malformed)
        assertEquals(true, malformed.reason.contains("acknowledgement is malformed"))
    }

    @Test
    fun parametersFailClosed() {
        assertFailsWith<IllegalArgumentException> {
            AstroWebUiAstroCaptureParameters(intervalSeconds = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            AstroWebUiAstroCaptureParameters(intervalSeconds = 8, shots = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            AstroWebUiAstroCaptureParameters(intervalSeconds = 8, bulbSeconds = 8.0)
        }
    }
}
