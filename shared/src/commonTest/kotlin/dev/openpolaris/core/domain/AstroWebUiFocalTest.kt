package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AstroWebUiFocalTest {
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
    fun parserPreservesOverrideExifAndConfigSources() {
        val result = AstroWebUiFocalParser.parse(
            """{"ok":true,"override":"1350.0000","exif_cache":"400.0","config":400.0}""",
        )
        val state = assertIs<AstroWebUiFocalResult.Success>(result).state
        assertEquals(1350.0, state.overrideMm)
        assertEquals(400.0, state.exifCacheMm)
        assertEquals(400.0, state.configMm)
    }

    @Test
    fun parserPreservesAutomaticSourcesAsUnknown() {
        val state = assertIs<AstroWebUiFocalResult.Success>(
            AstroWebUiFocalParser.parse(
                """{"ok":true,"override":null,"exif_cache":null,"config":400.0}""",
            ),
        ).state
        assertEquals(null, state.overrideMm)
        assertEquals(null, state.exifCacheMm)
    }

    @Test
    fun parserRejectsInvalidFocalRange() {
        assertIs<AstroWebUiFocalResult.Failure>(
            AstroWebUiFocalParser.parse(
                """{"ok":true,"override":"0","exif_cache":null,"config":400.0}""",
            ),
        )
    }

    @Test
    fun writesManualOverrideUsingSourceForm() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", """{"ok":true}""".encodeToByteArray()),
        )
        val client = DefaultAstroWebUiFocalClient(transport)
        assertEquals(
            AstroWebUiFocalResult.Accepted(1350.0),
            client.setOverride("192.168.0.1", 8080, 1350.0),
        )
        assertEquals("/api/focal", transport.request?.path)
        assertEquals("focal=1350.0", transport.request?.body)
    }

    @Test
    fun clearsOverrideWithAuto() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", """{"ok":true}""".encodeToByteArray()),
        )
        val result = DefaultAstroWebUiFocalClient(transport)
            .setOverride("192.168.0.1", 8080, null)
        assertEquals(AstroWebUiFocalResult.Accepted(null), result)
        assertEquals("focal=auto", transport.request?.body)
    }

    @Test
    fun rejectsOutOfRangeOverrideBeforeTransport() = runTest {
        val transport = RecordingTransport(
            AstroWebUiHttpResponse(200, "application/json", """{"ok":true}""".encodeToByteArray()),
        )
        val result = DefaultAstroWebUiFocalClient(transport)
            .setOverride("192.168.0.1", 8080, 100_001.0)
        assertIs<AstroWebUiFocalResult.Failure>(result)
        assertEquals(null, transport.request)
    }
}
