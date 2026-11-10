package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AstroMissionFlowTest {
    private fun input(
        control: Boolean = true,
        mount: Boolean = true,
        camera: Boolean? = true,
        storage: Boolean? = true,
        alignment: Boolean? = true,
        tracking: Boolean? = true,
        target: Boolean = true,
        focus: Boolean? = true,
        guide: Boolean? = false,
        captureReady: Boolean = true,
        captureComplete: Boolean = true,
        review: Boolean = true,
    ) = AstroMissionInput(
        control, mount, camera, storage, alignment, tracking,
        target, focus, guide, captureReady, captureComplete, review,
    )

    @Test
    fun disconnectedMissionStartsAtConnectionAndDoesNotPretendLaterStepsAreReady() {
        val statuses = AstroMissionFlow.evaluate(input(control = false, mount = false))
        assertEquals(AstroMissionStageState.CURRENT, statuses.first().state)
        assertEquals("Open connection", statuses.first().actionLabel)
        assertTrue(statuses.drop(1).all { it.state == AstroMissionStageState.PENDING })
    }

    @Test
    fun cameraOrStorageFailureBlocksPreparation() {
        val cameraMissing = AstroMissionFlow.evaluate(input(camera = false))
        assertEquals(AstroMissionStageState.BLOCKED, cameraMissing[1].state)
        assertTrue(cameraMissing[1].summary.contains("camera", ignoreCase = true))

        val storageMissing = AstroMissionFlow.evaluate(input(storage = false))
        assertEquals(AstroMissionStageState.BLOCKED, storageMissing[1].state)
        assertTrue(storageMissing[1].summary.contains("storage", ignoreCase = true))
    }

    @Test
    fun firstIncompletePrerequisiteIsCurrent() {
        val statuses = AstroMissionFlow.evaluate(input(alignment = false, tracking = false, target = false))
        assertEquals(AstroMissionStageState.COMPLETE, statuses[1].state)
        assertEquals(AstroMissionStageState.CURRENT, statuses[2].state)
        assertEquals(AstroMissionStageState.PENDING, statuses[3].state)
        assertEquals("Open alignment", statuses[2].actionLabel)
    }

    @Test
    fun targetFocusAndCaptureProgressAreSeparate() {
        val targetPending = AstroMissionFlow.evaluate(input(target = false, focus = false, captureReady = false, captureComplete = false))
        assertEquals(AstroMissionStageState.CURRENT, targetPending[4].state)
        assertEquals(AstroMissionStageState.PENDING, targetPending[5].state)

        val capturePending = AstroMissionFlow.evaluate(input(focus = false, captureReady = true, captureComplete = false, review = false))
        assertEquals(AstroMissionStageState.CURRENT, capturePending[5].state)
        assertEquals(AstroMissionStageState.CURRENT, capturePending[7].state)
        assertEquals(AstroMissionStageState.PENDING, capturePending[8].state)
    }

    @Test
    fun guidingIsOptionalAndReviewNeedsConfirmedCapture() {
        val statuses = AstroMissionFlow.evaluate(input(guide = false, review = false))
        assertEquals(AstroMissionStageState.OPTIONAL, statuses[6].state)
        assertEquals(AstroMissionStageState.COMPLETE, statuses[7].state)
        assertEquals(AstroMissionStageState.CURRENT, statuses[8].state)
        assertEquals("Open gallery", statuses[8].actionLabel)
    }

    @Test
    fun unknownReadinessDoesNotBecomeComplete() {
        val statuses = AstroMissionFlow.evaluate(input(alignment = null, tracking = null))
        assertEquals(AstroMissionStageState.UNKNOWN, statuses[2].state)
        assertEquals(AstroMissionStageState.PENDING, statuses[3].state)
    }
}
