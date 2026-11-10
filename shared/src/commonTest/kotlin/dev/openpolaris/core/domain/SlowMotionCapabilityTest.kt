package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlowMotionCapabilityTest {
    @Test
    fun defaultBuildRecordIsNotLiveReadyAndExplainsEveryMissingGate() {
        val capability = SlowMotionCapabilityRecord.Unqualified

        assertFalse(capability.liveReady)
        assertContains(capability.blockers, "exact firmware identity")
        assertContains(capability.blockers, "positive acknowledgement")
        assertContains(capability.blockers, "physical STOP")
        assertContains(capability.blockers, "hardware-limit boundary")
    }

    @Test
    fun completeNamedRecordIsLiveReadyOnlyWhenEveryGateIsTrue() {
        val capability = SlowMotionCapabilityRecord(
            firmwareIdentity = "fw-sha256:abc",
            gimbalIdentity = "gimbal:payload-1",
            sourceAppIdentity = "benro-connect:build-1",
            routeQualified = true,
            axisSignsQualified = true,
            rateEnvelopeQualified = true,
            acknowledgementQualified = true,
            watchdogQualified = true,
            stopQualified = true,
            disconnectRecoveryQualified = true,
            limitsQualified = true,
        )

        assertTrue(capability.liveReady)
        assertTrue(capability.blockers.isEmpty())
    }

    @Test
    fun oneMissingGateKeepsTheWholeTransportUnavailable() {
        val capability = SlowMotionCapabilityRecord(
            firmwareIdentity = "fw-sha256:abc",
            gimbalIdentity = "gimbal:payload-1",
            sourceAppIdentity = "benro-connect:build-1",
            routeQualified = true,
            axisSignsQualified = true,
            rateEnvelopeQualified = true,
            acknowledgementQualified = true,
            watchdogQualified = true,
            stopQualified = true,
            disconnectRecoveryQualified = true,
            limitsQualified = false,
        )

        assertFalse(capability.liveReady)
        assertContains(capability.blockers, "hardware-limit boundary")
    }
}
