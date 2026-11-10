package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class BatteryAwarenessTest {
    @Test
    fun unknownLevelRemainsExplicit() {
        val value = BatteryAwareness.from(null, charging = false)

        assertEquals(BatterySeverity.UNKNOWN, value.severity)
        assertEquals("Battery —", value.compactLabel)
        assertEquals("Battery level unknown; unknown field status", value.contentDescription)
    }

    @Test
    fun criticalAndLowThresholdsAreDistinct() {
        assertEquals(BatterySeverity.CRITICAL, BatteryAwareness.from(10, false).severity)
        assertEquals(BatterySeverity.LOW, BatteryAwareness.from(11, false).severity)
        assertEquals(BatterySeverity.LOW, BatteryAwareness.from(20, false).severity)
        assertEquals(BatterySeverity.NORMAL, BatteryAwareness.from(21, false).severity)
    }

    @Test
    fun chargingIsAnnouncedWithoutHidingLowLevel() {
        val value = BatteryAwareness.from(8, charging = true)

        assertEquals(BatterySeverity.CRITICAL, value.severity)
        assertEquals("Battery 8% and charging; critical field status", value.contentDescription)
    }

    @Test
    fun invalidReadbackFailsClosedToUnknown() {
        assertEquals(BatterySeverity.UNKNOWN, BatteryAwareness.from(-1, false).severity)
        assertEquals(BatterySeverity.UNKNOWN, BatteryAwareness.from(101, false).severity)
    }
}
