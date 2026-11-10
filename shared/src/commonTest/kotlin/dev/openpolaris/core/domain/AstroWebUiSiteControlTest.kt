package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AstroWebUiSiteControlTest {
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
    fun siteSaveUsesSourceWebUiFormAndReturnsTheResidentCoordinates() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(
                200,
                "application/json",
                """{"ok":true,"lat":51.5,"lon":-0.12}""".encodeToByteArray(),
            ),
        )

        val result = DefaultAstroWebUiSiteControlClient(transport).save(
            "192.168.0.1",
            8080,
            AstroWebUiSiteWriteRequest(51.5, -0.12),
        )

        val accepted = assertIs<AstroWebUiSiteWriteResult.Accepted>(result)
        assertEquals(51.5, accepted.site.latitudeDeg)
        assertEquals(-0.12, accepted.site.longitudeDeg)
        assertEquals("/api/site", transport.requests.single().path)
        assertEquals("application/x-www-form-urlencoded", transport.requests.single().contentType)
        assertEquals("lat=51.5&lon=-0.12", transport.requests.single().body)
    }

    @Test
    fun invalidRequestNeverReachesTransport() = runTest {
        assertFailsWith<IllegalArgumentException> {
            AstroWebUiSiteWriteRequest(90.1, 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            AstroWebUiSiteWriteRequest(0.0, -180.1)
        }
    }

    @Test
    fun unsafeHostIsRejectedBeforePost() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", """{"ok":true}""".encodeToByteArray()),
        )

        val result = DefaultAstroWebUiSiteControlClient(transport).save(
            "http://192.168.0.1",
            8080,
            AstroWebUiSiteWriteRequest(0.0, 0.0),
        )

        assertIs<AstroWebUiSiteWriteResult.Failure>(result)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun rejectedOrIncompleteAcknowledgementIsNotAccepted() = runTest {
        val rejected = RecordingTransport(
            AstroWebUiHttpResponse(400, "application/json", """{"ok":false,"error":"invalid site"}""".encodeToByteArray()),
        )
        val rejectedResult = DefaultAstroWebUiSiteControlClient(rejected).save(
            "192.168.0.1",
            8080,
            AstroWebUiSiteWriteRequest(0.0, 0.0),
        )
        assertIs<AstroWebUiSiteWriteResult.Failure>(rejectedResult)

        val incomplete = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", """{"ok":true}""".encodeToByteArray()),
        )
        val incompleteResult = DefaultAstroWebUiSiteControlClient(incomplete).save(
            "192.168.0.1",
            8080,
            AstroWebUiSiteWriteRequest(0.0, 0.0),
        )
        assertIs<AstroWebUiSiteWriteResult.Failure>(incompleteResult)
    }

    @Test
    fun returnedCoordinatesAreValidated() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(
                200,
                "application/json",
                """{"ok":true,"lat":91,"lon":0}""".encodeToByteArray(),
            ),
        )

        val result = DefaultAstroWebUiSiteControlClient(transport).save(
            "192.168.0.1",
            8080,
            AstroWebUiSiteWriteRequest(0.0, 0.0),
        )

        assertIs<AstroWebUiSiteWriteResult.Failure>(result)
    }
}
