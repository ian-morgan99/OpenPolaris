package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.ResponseParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MountStateTest {

    private fun frame(payload: String) = ResponseParser().parseFrame("1&284&2&$payload")!!

    @Test
    fun parsesFullStatusFrame() {
        val s = MountState.fromFrame284(frame("mode:2;battery:87;charge:0;track:1;halfSpeed:1;ahrs:1;"))
        assertEquals(MountMode.ASTRO, s.mode)
        assertEquals(87, s.batteryPercent)
        assertFalse(s.charging)
        assertEquals(true, s.tracking)
        assertTrue(s.ahrsEnabled)
    }

    @Test
    fun toleratesMissingFields() {
        val s = MountState.fromFrame284(frame("mode:0;"))
        assertEquals(MountMode.PANORAMA, s.mode)
        assertNull(s.batteryPercent)
        assertNull(s.tracking)
        assertFalse(s.halfSpeed)
    }

    @Test
    fun rejectsOutOfRangeBattery() {
        val s = MountState.fromFrame284(frame("battery:250;"))
        assertNull(s.batteryPercent)
    }

    @Test
    fun unknownModeStaysUnknown() {
        val s = MountState.fromFrame284(frame("mode:99;"))
        assertEquals(MountMode.UNKNOWN, s.mode)
    }

    @Test
    fun parsesPositionFrame() {
        val f = ResponseParser().parseFrame("1&517&2&yaw:123.5;pitch:-12.25;roll:0.5;")!!
        val p = GimbalPosition.fromFrame517(f)!!
        assertEquals(123.5f, p.yaw)
        assertEquals(-12.25f, p.pitch)
        assertEquals(0.5f, p.roll)
    }
}
