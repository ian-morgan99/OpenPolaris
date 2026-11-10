package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureEvidenceGateTest {
    @Test
    fun lifecycleAndFileMustBothFollowTheRequest() {
        val gate = CaptureEvidenceGate()
        gate.begin()
        assertFalse(gate.observePublication("/app/sd/normal/early.dng", 1, 1L))
        assertEquals(CaptureEvidenceGate.State.REQUESTED, gate.state)
        assertTrue(gate.observeLifecycle(1))
        assertTrue(gate.observePublication(" /app/sd/normal/frame.dng ", 1, 2L))
        assertEquals(CaptureEvidenceGate.State.CONFIRMED, gate.state)
        assertTrue(gate.isConfirmed)
        assertEquals("/app/sd/normal/frame.dng", gate.publication?.path)
    }

    @Test
    fun idleLifecycleAndEmptyOrDuplicateFilesAreNotEvidence() {
        val gate = CaptureEvidenceGate()
        gate.begin()
        assertFalse(gate.observeLifecycle(0))
        assertFalse(gate.observePublication("", null, null))
        assertTrue(gate.observeLifecycle(2))
        assertTrue(gate.observePublication("/app/sd/normal/frame.dng", 1, null))
        assertFalse(gate.observePublication("/app/sd/normal/second.dng", 1, 1L))
        assertEquals(1, gate.publication?.type)
    }

    @Test
    fun resetAndTerminalFailureClearStalePublication() {
        val gate = CaptureEvidenceGate()
        gate.begin()
        gate.observeLifecycle(1)
        gate.observePublication("/app/sd/normal/frame.dng", 1, 1L)
        gate.unknown("timeout")
        assertFalse(gate.isConfirmed)
        gate.resetForNext()
        assertEquals(CaptureEvidenceGate.State.REQUESTED, gate.state)
        assertNull(gate.publication)
        assertFalse(gate.isConfirmed)
    }
}
