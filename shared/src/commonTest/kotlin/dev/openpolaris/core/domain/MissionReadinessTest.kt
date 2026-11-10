package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MissionReadinessTest {
    private fun input(
        control: EndpointService = EndpointService.REACHABLE,
        mountConnected: Boolean = true,
        cameraAttached: Boolean? = true,
        preview: EndpointService = EndpointService.REACHABLE,
        storageReady: Boolean? = true,
        alignmentReady: Boolean? = true,
        tracking: Boolean? = true,
        captureInProgress: Boolean = false,
        activeMotionOwner: MotionOwner? = null,
    ) = MissionReadinessInput(
        control = control,
        mountConnected = mountConnected,
        cameraAttached = cameraAttached,
        preview = preview,
        storageReady = storageReady,
        alignmentReady = alignmentReady,
        tracking = tracking,
        captureInProgress = captureInProgress,
        activeMotionOwner = activeMotionOwner,
    )

    @Test
    fun readySnapshotHasStableAstroMissionOrder() {
        val cards = MissionReadiness.evaluate(input())

        assertEquals(
            listOf("connection", "mount", "camera", "preview", "storage", "alignment", "tracking", "capture", "motion"),
            cards.map { it.id },
        )
        assertTrue(cards.all { it.level == ReadinessLevel.READY })
    }

    @Test
    fun missingCameraExplainsBothCameraAndCaptureBlockers() {
        val cards = MissionReadiness.evaluate(input(cameraAttached = false, preview = EndpointService.UNREACHABLE))
        val byId = cards.associateBy { it.id }

        assertEquals(ReadinessLevel.BLOCKED, byId.getValue("camera").level)
        assertEquals("No camera detected", byId.getValue("camera").summary)
        assertEquals(ReadinessLevel.BLOCKED, byId.getValue("capture").level)
        assertEquals("Attach a camera before capture", byId.getValue("capture").summary)
        assertEquals(ReadinessLevel.ATTENTION, byId.getValue("preview").level)
    }

    @Test
    fun unknownIsNotCollapsedIntoBlocked() {
        val cards = MissionReadiness.evaluate(input(cameraAttached = null, preview = EndpointService.UNKNOWN, tracking = null))
        val byId = cards.associateBy { it.id }

        assertEquals(ReadinessLevel.UNKNOWN, byId.getValue("camera").level)
        assertEquals(ReadinessLevel.UNKNOWN, byId.getValue("preview").level)
        assertEquals(ReadinessLevel.UNKNOWN, byId.getValue("tracking").level)
    }

    @Test
    fun storageAndAlignmentRemainSeparateReadinessBoundaries() {
        val cards = MissionReadiness.evaluate(input(storageReady = null, alignmentReady = false))
        val byId = cards.associateBy { it.id }

        assertEquals(ReadinessLevel.UNKNOWN, byId.getValue("storage").level)
        assertEquals(ReadinessLevel.ATTENTION, byId.getValue("alignment").level)
        assertEquals("Open alignment", byId.getValue("alignment").actionLabel)
    }

    @Test
    fun activeMotionOwnerIsVisibleAndCaptureCanRemainReady() {
        val cards = MissionReadiness.evaluate(
            input(activeMotionOwner = MotionOwner.SLOW_MOVE, captureInProgress = false),
        )
        val byId = cards.associateBy { it.id }

        assertEquals(ReadinessLevel.ATTENTION, byId.getValue("motion").level)
        assertEquals("Owned by SLOW_MOVE", byId.getValue("motion").summary)
        assertEquals(ReadinessLevel.READY, byId.getValue("capture").level)
    }

    @Test
    fun captureInProgressTakesPriorityOverReadiness() {
        val card = MissionReadiness.evaluate(input(captureInProgress = true)).first { it.id == "capture" }

        assertEquals(ReadinessLevel.ATTENTION, card.level)
        assertEquals("Capture in progress", card.summary)
    }

    @Test
    fun disconnectedMountProvidesActionableConnectionAndMotionReasons() {
        val cards = MissionReadiness.evaluate(
            input(control = EndpointService.UNREACHABLE, mountConnected = false, cameraAttached = null, preview = EndpointService.UNKNOWN),
        )
        val byId = cards.associateBy { it.id }

        assertEquals("Connect to mount", byId.getValue("connection").actionLabel)
        assertEquals("Open connection", byId.getValue("motion").actionLabel)
    }
}
