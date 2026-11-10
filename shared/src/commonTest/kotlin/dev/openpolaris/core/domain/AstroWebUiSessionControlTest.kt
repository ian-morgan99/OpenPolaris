package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class AstroWebUiSessionControlTest {
    @Test
    fun enterAndLeaveUseSourceEndpointsAndAcceptPositiveAcks() = runTest {
        val requests = mutableListOf<AstroWebUiCaptureHttpRequest>()
        val client = DefaultAstroWebUiSessionControlClient(
            AstroWebUiCaptureTransport { request ->
                requests += request
                AstroWebUiHttpResponse(200, "application/json", """{"ok":true}""".encodeToByteArray())
            },
        )

        assertIs<AstroWebUiSessionControlResult.Accepted>(client.enter("192.168.0.1", 8080))
        assertIs<AstroWebUiSessionControlResult.Accepted>(client.leave("192.168.0.1", 8080))

        assertEquals(listOf("/api/astro/enter", "/api/astro/leave"), requests.map { it.path })
        assertEquals(listOf("", ""), requests.map { it.body })
    }

    @Test
    fun trackUsesSourceRateAndHalfSpeedFormFields() = runTest {
        val requests = mutableListOf<AstroWebUiCaptureHttpRequest>()
        val client = DefaultAstroWebUiSessionControlClient(
            AstroWebUiCaptureTransport { request ->
                requests += request
                AstroWebUiHttpResponse(200, "application/json", """{"ok":true}""".encodeToByteArray())
            },
        )

        val result = client.setTracking(
            "192.168.0.1",
            8080,
            AstroWebUiTrackRequest(
                on = true,
                rate = AstroWebUiTrackRate.LUNAR.index,
                halfSpeed = true,
            ),
        )

        assertIs<AstroWebUiSessionControlResult.Accepted>(result)
        assertEquals("/api/astro/track", requests.single().path)
        assertEquals("on=1&rate=2&half=1", requests.single().body)
    }

    @Test
    fun unsupportedRateAndRejectedAckFailClosed() = runTest {
        assertFailsWith<IllegalArgumentException> {
            AstroWebUiTrackRequest(on = true, rate = 1, halfSpeed = false)
        }

        val client = DefaultAstroWebUiSessionControlClient(
            AstroWebUiCaptureTransport {
                AstroWebUiHttpResponse(
                    409,
                    "application/json",
                    """{"ok":false,"error":"not in astro mode"}""".encodeToByteArray(),
                )
            },
        )

        val result = client.setTracking(
            "192.168.0.1",
            8080,
            AstroWebUiTrackRequest(on = false, rate = 0, halfSpeed = false),
        )

        val failure = assertIs<AstroWebUiSessionControlResult.Failure>(result)
        assertEquals("Astro WebUI stop Astro tracking HTTP 409", failure.reason)
    }

    @Test
    fun invalidHostNeverReachesTransport() = runTest {
        var called = false
        val client = DefaultAstroWebUiSessionControlClient(
            AstroWebUiCaptureTransport {
                called = true
                error("transport must not be called")
            },
        )

        val result = client.enter("bad host!", 8080)

        assertIs<AstroWebUiSessionControlResult.Failure>(result)
        assertFalse(called)
    }
}
