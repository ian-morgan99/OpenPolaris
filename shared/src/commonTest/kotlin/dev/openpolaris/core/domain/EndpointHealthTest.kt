package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EndpointHealthTest {
    private val profile = EndpointProfile(
        id = "direct-ap",
        label = "Direct Polaris AP",
        kind = EndpointKind.DIRECT_AP,
        address = EndpointAddress("192.168.0.1"),
    )

    @Test
    fun controlAndPreviewAreIndependentServices() {
        val health = EndpointHealth(
            profile = profile,
            phase = EndpointPhase.READY,
            control = EndpointService.REACHABLE,
            preview = EndpointService.UNREACHABLE,
            phaseSinceMs = 42L,
        )

        assertTrue(health.controlReady)
        assertFalse(health.previewReady)
        assertTrue(health.isUsable)
    }

    @Test
    fun failedEndpointNeverLooksUsableEvenIfControlWasPreviouslySeen() {
        val health = EndpointHealth(
            profile = profile,
            phase = EndpointPhase.FAILED,
            control = EndpointService.REACHABLE,
            preview = EndpointService.UNKNOWN,
            lastFailure = "control socket closed",
        )

        assertFalse(health.controlReady)
        assertFalse(health.isUsable)
        assertEquals("control socket closed", health.lastFailure)
    }

    @Test
    fun disconnectedFactoryStateHasNoEndpointOrReachability() {
        val health = EndpointHealth.disconnected()

        assertEquals(null, health.profile)
        assertFalse(health.controlReady)
        assertFalse(health.previewReady)
        assertFalse(health.isUsable)
    }

    @Test
    fun invalidAddressesFailBeforeTransportCanUseThem() {
        assertFailsWith<IllegalArgumentException> { EndpointAddress("", 9090, 8080) }
        assertFailsWith<IllegalArgumentException> { EndpointAddress("mount", 0, 8080) }
        assertFailsWith<IllegalArgumentException> { EndpointAddress("mount", 9090, 65536) }
    }

    @Test
    fun phaseAgeAndFailureMustBeTruthfulValues() {
        assertFailsWith<IllegalArgumentException> {
            EndpointHealth(profile, EndpointPhase.WAKING, phaseSinceMs = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            EndpointHealth(profile, EndpointPhase.FAILED, lastFailure = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            EndpointHealth(
                profile = null,
                phase = EndpointPhase.DISCONNECTED,
                control = EndpointService.REACHABLE,
            )
        }
    }

    @Test
    fun phaseAgeIsFreshnessOnlyAndNeverNegative() {
        val health = EndpointHealth(
            profile = profile,
            phase = EndpointPhase.READY,
            phaseSinceMs = 900L,
        )

        assertEquals(100L, health.phaseAgeMs(1_000L))
        assertEquals(0L, health.phaseAgeMs(800L))
        assertEquals(null, EndpointHealth.disconnected().phaseAgeMs(1_000L))
    }

    @Test
    fun profileKeepsRouteKindAndPortsTogether() {
        val bridge = EndpointProfile(
            id = "pc-bridge",
            label = "PC bridge",
            kind = EndpointKind.PC_BRIDGE,
            address = EndpointAddress("192.168.0.1", controlPort = 9091, previewPort = 8081),
        )

        assertEquals(EndpointKind.PC_BRIDGE, bridge.kind)
        assertEquals(9091, bridge.address.controlPort)
        assertEquals(8081, bridge.address.previewPort)
    }
}
