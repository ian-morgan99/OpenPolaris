package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObservationProgrammeCaptureBridgeTest {
    private val step = ObservationProgrammePlanner.Step(1, "Frame 1/1", 0L)

    @Test
    fun acceptedTriggerAdvancesOnlyWithMatchingConfirmedEvidence() = runTest {
        val evidence = CaptureEvidenceGate()
        val publication = CaptureEvidenceGate.Publication("/app/sd/normal/frame.dng", 1, 10L)
        val bridge = ObservationProgrammeCaptureBridge(
            evidence = evidence,
            trigger = { ObservationProgrammeCaptureBridge.TriggerResult.Accepted },
            awaitConfirmation = {
                evidence.observeLifecycle(1)
                evidence.observePublication(publication.path, publication.type, publication.sizeBytes)
                publication
            },
        )

        assertEquals(
            ObservationProgrammeExecutor.StepResult.Confirmed,
            bridge.execute(step),
        )
        assertTrue(evidence.isConfirmed)
    }

    @Test
    fun acceptedTriggerWithMissingEvidenceIsOutcomeUnknownAndNotSuccess() = runTest {
        val evidence = CaptureEvidenceGate()
        val bridge = ObservationProgrammeCaptureBridge(
            evidence = evidence,
            trigger = { ObservationProgrammeCaptureBridge.TriggerResult.Accepted },
            awaitConfirmation = { null },
        )

        val result = bridge.execute(step)
        assertTrue(result is ObservationProgrammeExecutor.StepResult.OutcomeUnknown)
        assertEquals(CaptureEvidenceGate.State.UNKNOWN, evidence.state)
    }

    @Test
    fun triggerRejectionDoesNotInvokeConfirmation() = runTest {
        val evidence = CaptureEvidenceGate()
        var confirmationCalls = 0
        val bridge = ObservationProgrammeCaptureBridge(
            evidence = evidence,
            trigger = { ObservationProgrammeCaptureBridge.TriggerResult.Rejected("camera unavailable") },
            awaitConfirmation = {
                confirmationCalls += 1
                null
            },
        )

        assertEquals(
            ObservationProgrammeExecutor.StepResult.Rejected("camera unavailable"),
            bridge.execute(step),
        )
        assertEquals(0, confirmationCalls)
        assertEquals(CaptureEvidenceGate.State.FAILED, evidence.state)
    }

    @Test
    fun mismatchedPublicationCannotCompleteTheStep() = runTest {
        val evidence = CaptureEvidenceGate()
        val actual = CaptureEvidenceGate.Publication("/app/sd/normal/actual.dng", 1, 10L)
        val reported = CaptureEvidenceGate.Publication("/app/sd/normal/stale.dng", 1, 10L)
        val bridge = ObservationProgrammeCaptureBridge(
            evidence = evidence,
            trigger = { ObservationProgrammeCaptureBridge.TriggerResult.Accepted },
            awaitConfirmation = {
                evidence.observeLifecycle(1)
                evidence.observePublication(actual.path, actual.type, actual.sizeBytes)
                reported
            },
        )

        assertTrue(bridge.execute(step) is ObservationProgrammeExecutor.StepResult.OutcomeUnknown)
        assertEquals(CaptureEvidenceGate.State.UNKNOWN, evidence.state)
    }
}
