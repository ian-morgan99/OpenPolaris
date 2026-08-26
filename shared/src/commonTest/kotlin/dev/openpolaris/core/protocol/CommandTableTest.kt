package dev.openpolaris.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandTableTest {

    private fun wire(d: CommandTable.Descriptor<*>, arg: Any? = Unit): String {
        val payload = (d.payload as (Any?) -> String)(arg)
        val b = CommandBuilder(d.code)
        if (payload != EMPTY_CONTENT) b.putRaw(payload)
        return decodeAscii(b.build())
    }

    @Test
    fun halfSpeedPayloadIsInverted() {
        assertTrue(wire(CommandTable.TRACK_HALF_SPEED, true).contains("halfSpeed:0;"))
        assertTrue(wire(CommandTable.TRACK_HALF_SPEED, false).contains("halfSpeed:1;"))
    }

    @Test
    fun gotoPayloadHasFourDecimals() {
        val w = wire(CommandTable.GOTO_AZ_ALT, 123.45678 to -12.5)
        assertEquals("1&519&2&az:123.4568;alt:-12.5000;#", w)
    }

    @Test
    fun trackingStartStopShareCode531() {
        assertEquals(531, CommandTable.TRACK_START.code)
        assertEquals(531, CommandTable.TRACK_STOP.code)
        assertTrue(wire(CommandTable.TRACK_START).contains("state:1;"))
        assertTrue(wire(CommandTable.TRACK_STOP).contains("state:0;"))
    }

    @Test
    fun allDescriptorsHaveCodesAndNames() {
        for ((code, descs) in CommandTable.ALL) {
            assertTrue(code in 100..1000, "suspicious code $code")
            for (d in descs) assertTrue(d.name.isNotBlank())
        }
    }

    // ---- camera (payload formats are ground truth from polestar_app strings) ----

    @Test
    fun cameraSetPayloadsUseIndexKeys() {
        assertTrue(wire(CommandTable.CAM_SET_ISO, CommandTable.CamParam(3)).contains("iso:3;"))
        assertTrue(wire(CommandTable.CAM_SET_WB, CommandTable.CamParam(1)).contains("wb:1;"))
        assertTrue(wire(CommandTable.CAM_SET_FNUM, CommandTable.CamParam(5)).contains("fNum:5;"))
        assertTrue(wire(CommandTable.CAM_SET_EV, CommandTable.CamParam(2)).contains("ev:2;"))
    }

    @Test
    fun cameraGetParsersReadResponseKeys() {
        fun frame(payload: String): ResponseParser.Frame {
            val p = ResponseParser()
            return p.parseFrame("1&0&2&$payload")!!
        }
        assertEquals(CommandTable.CamParam(4), CommandTable.CAM_GET_ISO.parse!!(frame("iso:4;ret:0;")))
        assertEquals(CommandTable.CamParam(7), CommandTable.CAM_GET_WB.parse!!(frame("wb:7;ret:0;")))
        assertEquals(CommandTable.CamParam(2), CommandTable.CAM_GET_FNUM.parse!!(frame("fNum:2;ret:0;")))
        assertEquals(CommandTable.CamParam(9), CommandTable.CAM_GET_EV.parse!!(frame("ev:9;ret:0;")))
    }

    @Test
    fun captureStateParserToleratesMissingFields() {
        val parse = CommandTable.CAM_GET_STATE.parse!!
        val f = ResponseParser().parseFrame("1&0&2&state:1;bulb:0;c:5;")!!
        assertEquals(CommandTable.CaptureState(1, 0, 5), parse(f))
        val f2 = ResponseParser().parseFrame("1&0&2&state:0;")!!
        assertEquals(CommandTable.CaptureState(0, 0, 0), parse(f2))
        val f3 = ResponseParser().parseFrame("1&0&2&junk;")!!
        assertEquals(null, parse(f3))
    }

    @Test
    fun cameraCodesAreInferredRange() {
        for (d in listOf(CommandTable.CAM_GET_ISO, CommandTable.CAM_SET_ISO,
                CommandTable.CAM_GET_WB, CommandTable.CAM_SET_WB,
                CommandTable.CAM_GET_FNUM, CommandTable.CAM_SET_FNUM,
                CommandTable.CAM_GET_EV, CommandTable.CAM_SET_EV,
                CommandTable.CAM_GET_STATE, CommandTable.CAM_CAPTURE)) {
            assertTrue(d.code in dev.openpolaris.core.protocol.Codes.CAMERA_BASE..dev.openpolaris.core.protocol.Codes.CAMERA_END,
                "${d.name} code ${d.code} outside camera range")
        }
    }
}
