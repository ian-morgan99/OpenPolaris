package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

class AstroCaptureContractTest {
    @Test
    fun ordinaryStillUsesSourceCompatibleSubtypeAndContinuationValue() {
        assertContentEquals(
            "1&264&2&state:1;bulb:0;c:-1;#".encodeToByteArray(),
            AstroCaptureContract.ordinaryStillFrame(),
        )
    }

    @Test
    fun acceptanceAndProgrammeProgressNeverClaimCaptureCompletion() {
        assertFalse(AstroCaptureContract.httpAcceptanceIsCaptureComplete())
        assertFalse(AstroCaptureContract.programmeProgressIsCaptureComplete())
    }

    @Test
    fun captureEvidenceStillRequiresLifecycleAndPublication() {
        val gate = CaptureEvidenceGate()
        gate.begin()
        assertFalse(gate.isConfirmed)
        gate.observeLifecycle(1)
        assertFalse(gate.isConfirmed)
        gate.observePublication("normal/20261110_120000.jpg", type = 0, sizeBytes = 1234)
        assert(gate.isConfirmed)
    }
}
