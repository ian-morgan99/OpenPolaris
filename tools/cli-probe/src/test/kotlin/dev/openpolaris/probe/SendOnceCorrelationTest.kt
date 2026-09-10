package dev.openpolaris.probe

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class SendOnceCorrelationTest {
    @Test
    fun `waits through unsolicited pushes for requested response code`() {
        val input = ByteArrayInputStream(
            "284@mode:1;state:0;#517@yaw:1;pitch:2;roll:3;#265@RD:0;V:5;R:100,200,;#266@RD:0;V:2;R:auto,daylight,;#"
                .toByteArray(),
        )

        assertEquals(
            listOf(
                "284@mode:1;state:0;#",
                "517@yaw:1;pitch:2;roll:3;#",
                "265@RD:0;V:5;R:100,200,;#",
            ),
            readFramesUntilCode(input, requestedCode = 265, totalTimeoutMs = 1_000),
        )
    }
}
