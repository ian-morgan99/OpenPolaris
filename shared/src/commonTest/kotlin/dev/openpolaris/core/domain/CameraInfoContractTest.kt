package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.ResponseParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CameraInfoContractTest {
    @Test
    fun `parses live Benro V index and R option list`() {
        val frame = requireNotNull(
            ResponseParser().parseFrame(
                "275@RD:0;V:5;R:f/1.4,f/1.6,f/1.8,f/2,;#",
            ),
        )

        val info = parseBenroInfo(275, "fNum", frame)

        assertEquals(0, info.ready)
        assertEquals(5, info.index)
        assertEquals(listOf("f/1.4", "f/1.6", "f/1.8", "f/2"), info.options)
        assertEquals(CameraController.InfoStatus.VALID, info.status)
    }

    @Test
    fun `empty white balance response remains unavailable`() {
        val frame = requireNotNull(
            ResponseParser().parseFrame("266@RD:0;V:;R:;#"),
        )

        val info = parseBenroInfo(266, "wb", frame)

        assertNull(info.index)
        assertEquals(emptyList(), info.options)
        assertEquals(CameraController.InfoStatus.EMPTY_OR_UNAVAILABLE, info.status)
    }

    @Test
    fun `missing EV value remains unavailable`() {
        val frame = requireNotNull(
            ResponseParser().parseFrame("267@RD:0;#"),
        )

        val info = parseBenroInfo(267, "ev", frame)

        assertNull(info.index)
        assertEquals(emptyList(), info.options)
        assertEquals(CameraController.InfoStatus.EMPTY_OR_UNAVAILABLE, info.status)
    }

    @Test
    fun `empty live option response remains unavailable`() {
        val frame = requireNotNull(
            ResponseParser().parseFrame("265@RD:0;V:;R:;#"),
        )

        val info = parseBenroInfo(265, "iso", frame)

        assertNull(info.index)
        assertEquals(emptyList(), info.options)
        assertEquals(CameraController.InfoStatus.EMPTY_OR_UNAVAILABLE, info.status)
    }
}
