package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AstroPrepareChecklistTest {
    private fun input(
        connected: Boolean = true,
        camera: Boolean? = true,
        storage: Boolean? = true,
        level: Boolean? = true,
        tilt: Boolean? = true,
        limits: Boolean? = true,
    ) = AstroPrepareInput(
        controlReady = connected,
        mountConnected = connected,
        cameraAttached = camera,
        storageReady = storage,
        levelWithinTolerance = level,
        tiltCompensationEnabled = tilt,
        limitsEnabled = limits,
    )

    @Test
    fun allPositiveEvidenceIsReady() {
        val checks = AstroPrepareChecklist.evaluate(input())

        assertTrue(checks.all { it.state == AstroPrepareCheckState.READY })
    }

    @Test
    fun unknownEvidenceDoesNotBecomeReady() {
        val checks = AstroPrepareChecklist.evaluate(input(camera = null, storage = null, level = null, tilt = null, limits = null))

        assertEquals(AstroPrepareCheckState.UNKNOWN, checks.first { it.id == AstroPrepareCheckId.CAMERA }.state)
        assertEquals(AstroPrepareCheckState.UNKNOWN, checks.first { it.id == AstroPrepareCheckId.STORAGE }.state)
        assertEquals(AstroPrepareCheckState.UNKNOWN, checks.first { it.id == AstroPrepareCheckId.LEVEL }.state)
        assertEquals(AstroPrepareCheckState.UNKNOWN, checks.first { it.id == AstroPrepareCheckId.TILT_COMPENSATION }.state)
        assertEquals(AstroPrepareCheckState.UNKNOWN, checks.first { it.id == AstroPrepareCheckId.LIMITS }.state)
    }

    @Test
    fun disconnectedChecksRemainPendingAndDoNotPretendReadiness() {
        val checks = AstroPrepareChecklist.evaluate(input(connected = false))

        assertEquals(AstroPrepareCheckState.BLOCKED, checks.first().state)
        assertTrue(checks.drop(1).all { it.state == AstroPrepareCheckState.PENDING })
    }

    @Test
    fun disabledLimitsAreVisibleAsOptionalNotReady() {
        val limits = AstroPrepareChecklist.evaluate(input(limits = false)).last()

        assertEquals(AstroPrepareCheckState.OPTIONAL, limits.state)
        assertTrue(limits.summary.contains("clearance"))
    }
}
