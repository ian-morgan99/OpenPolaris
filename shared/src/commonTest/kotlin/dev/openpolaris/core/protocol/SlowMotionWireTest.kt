package dev.openpolaris.core.protocol

import dev.openpolaris.core.domain.RateAxis
import dev.openpolaris.core.domain.SlowMotionPoint
import dev.openpolaris.core.domain.SlowScheduleCommand
import dev.openpolaris.core.domain.SlowScheduleKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SlowMotionWireTest {
    private fun command(
        axis: RateAxis,
        kind: SlowScheduleKind,
        key: Int,
        state: Int,
        level: Int,
    ) = SlowScheduleCommand(
        axis = axis,
        kind = kind,
        point = SlowMotionPoint(rate = 0.01, key = key, state = state, level = level),
    )

    @Test
    fun `maps all axes to type 3 source-aligned frames`() {
        val expected = mapOf(
            RateAxis.M1_PAN to "1&532&3&key:0;state:1;level:1;#",
            RateAxis.M2_TILT to "1&533&3&key:1;state:2;level:4;#",
            RateAxis.M3_ROTATION to "1&534&3&key:0;state:1;level:5;#",
        )

        expected.forEach { (axis, wire) ->
            val frame = SlowMotionWire.encode(
                command(
                    axis,
                    SlowScheduleKind.SLOW,
                    key = if (axis == RateAxis.M2_TILT) 1 else 0,
                    state = if (axis == RateAxis.M2_TILT) 2 else 1,
                    level = when (axis) {
                        RateAxis.M1_PAN -> 1
                        RateAxis.M2_TILT -> 4
                        RateAxis.M3_ROTATION -> 5
                    },
                ),
            )
            assertEquals(wire, frame.bytes.decodeToString())
            assertEquals(3, frame.subtype)
        }
    }

    @Test
    fun `stop retains direction and level but changes only state`() {
        val frame = SlowMotionWire.encode(
            command(RateAxis.M3_ROTATION, SlowScheduleKind.STOP, key = 1, state = 2, level = 3),
        )

        assertEquals("key:1;state:0;level:3;", frame.payload)
        assertEquals("1&534&3&key:1;state:0;level:3;#", frame.bytes.decodeToString())
    }

    @Test
    fun `stop without retained point is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            SlowMotionWire.encode(
                SlowScheduleCommand(RateAxis.M1_PAN, SlowScheduleKind.STOP, point = null),
            )
        }
    }
}
