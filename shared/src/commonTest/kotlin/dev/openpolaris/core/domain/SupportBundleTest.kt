package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SupportBundleTest {
    private fun snapshot(
        timeline: List<SupportBundleTimelineEntry> = emptyList(),
        publication: SupportBundlePublication? = null,
    ) = SupportBundleSnapshot(
        appBuild = "test-build",
        generatedAtEpochMs = 1234L,
        endpointKind = "HOME_LAN",
        controlPort = 9090,
        previewPort = 8080,
        routePhase = "READY",
        controlService = "REACHABLE",
        previewService = "UNKNOWN",
        lastFailure = "bad host\npassword=secret",
        mountConnected = false,
        firmware = "fw-test",
        hardware = "gimbal-test",
        camera = "No camera",
        storage = "Not checked",
        capture = "Outcome unknown: timeout",
        slowMotionLiveReady = false,
        slowMotionBlockers = listOf("physical STOP", "watchdog/lease expiry"),
        rawFrameLoggingEnabled = false,
        captureTimeline = timeline,
        publication = publication,
    )

    @Test
    fun documentRedactsHostPathsAndControlCharacters() {
        val text = SupportBundleDocument.render(
            snapshot(
                timeline = listOf(SupportBundleTimelineEntry("FAILED", "line one\nline two")),
                publication = SupportBundlePublication(
                    basename = "IMG_0001.JPG",
                    type = 773,
                    sizeBytes = 42L,
                ),
            ),
        )

        assertTrue("endpoint_host=<redacted>" in text)
        assertTrue("raw_logs=not included by this bounded export" in text)
        assertTrue("capture_timeline_0_detail=line one line two" in text)
        assertTrue("publication_basename=IMG_0001.JPG" in text)
        assertTrue("password=<redacted>" in text)
        assertFalse("endpoint_host=192.168.0.1" in text)
        assertTrue(text.endsWith("\n"))
    }

    @Test
    fun controllerStoresOnlyAfterExplicitSinkSuccess() = runTest {
        var calls = 0
        val controller = SupportBundleExportController(
            SupportBundleExportSink {
                calls += 1
                SupportBundleExportResult.Stored("file:///support.txt")
            },
        )

        assertTrue(controller.prepare(snapshot()))
        val result = controller.export()

        val complete = assertIs<SupportBundleExportState.Complete>(result)
        assertEquals("file:///support.txt", complete.location)
        assertEquals(1, calls)
        assertEquals("openpolaris-support-bundle.txt", complete.request.suggestedName)
    }

    @Test
    fun failedSaveIsTerminalUntilUserPreparesAgain() = runTest {
        var calls = 0
        val controller = SupportBundleExportController(
            SupportBundleExportSink {
                calls += 1
                SupportBundleExportResult.Failed("cancelled")
            },
        )

        controller.prepare(snapshot())
        val first = assertIs<SupportBundleExportState.Failed>(controller.export())
        assertEquals("cancelled", first.reason)
        assertIs<SupportBundleExportState.Failed>(controller.export())
        assertEquals(1, calls)
        assertTrue(controller.prepare(snapshot(publication = SupportBundlePublication("IMG.JPG", null, null))))
    }

    @Test
    fun unqualifiedSlowMotionEvidenceIsIncluded() {
        val text = SupportBundleDocument.render(
            snapshot(),
        )
        assertTrue("slow_motion_live_ready=false" in text)
        assertTrue("slow_motion_blockers=physical STOP | watchdog/lease expiry" in text)
    }
}
