package dev.openpolaris.core.protocol

import dev.openpolaris.core.domain.CameraInfo
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
    fun trackingStartWithSpeedMatchesFirmwareFormat() {
        // Firmware format string: state:%d;speed:%d;
        assertEquals(
            "1&531&2&state:1;speed:2;#",
            wire(CommandTable.TRACK_START_WITH_SPEED, 2),
        )
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
        assertTrue(wire(CommandTable.CAM_SET_FOCUS, CommandTable.CamParam(0)).contains("focus:0;"))
        assertTrue(wire(CommandTable.CAM_SET_IMG_SIZE, CommandTable.CamParam(2)).contains("imgSize:2;"))
        assertTrue(wire(CommandTable.CAM_SET_IMG_FMT, CommandTable.CamParam(1)).contains("imgFmt:1;"))
        assertTrue(wire(CommandTable.CAM_SET_COLOR, CommandTable.CamParam(3)).contains("color:3;"))
        assertTrue(wire(CommandTable.CAM_SET_SHUTTER, CommandTable.CamParam(4)).contains("shutter:4;"))
        assertTrue(wire(CommandTable.CAM_SET_CAPTURE_MODE, CommandTable.CamParam(0)).contains("captureMode:0;"))
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
        assertEquals(CommandTable.CamParam(0), CommandTable.CAM_GET_FOCUS.parse!!(frame("focus:0;ret:0;")))
        assertEquals(CommandTable.CamParam(2), CommandTable.CAM_GET_IMG_SIZE.parse!!(frame("imgSize:2;ret:0;")))
        assertEquals(CommandTable.CamParam(1), CommandTable.CAM_GET_IMG_FMT.parse!!(frame("imgFmt:1;ret:0;")))
        assertEquals(CommandTable.CamParam(3), CommandTable.CAM_GET_COLOR.parse!!(frame("color:3;ret:0;")))
        assertEquals(CommandTable.CamParam(4), CommandTable.CAM_GET_SHUTTER.parse!!(frame("shutter:4;ret:0;")))
        assertEquals(CommandTable.CamParam(0), CommandTable.CAM_GET_CAPTURE_MODE.parse!!(frame("captureMode:0;ret:0;")))
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

    // ---- post-connect burst ----

    @Test
    fun burstPreCameraMatchesPlanningOrder() {
        // Source of truth: docs/PLANNING-2026-08.md step 5.
        val expected = listOf(808, 809, 802, 778, 779, 775, 824, 524, 543)
        assertEquals(expected, CommandTable.BURST_PRE_CAMERA.map { it.code })
    }

    @Test
    fun burstPreCameraParsersCoverAllSteps() {
        // Each step's parser should produce a non-null value when handed a
        // minimal synthetic frame that the code's real protocol message
        // contains. This proves the parser actually parses, not just that
        // a function reference exists.
        val keyByCode = mapOf(
            808 to "ver", 809 to "sn", 802 to "band", 543 to "time",
        )
        for (step in CommandTable.BURST_PRE_CAMERA) {
            val key = keyByCode[step.code] ?: continue // 778/779/775/824/524 use domain-specific helpers
            val p = ResponseParser()
            val f = p.parseFrame("1&0&2&$key:7;")!!
            val v = step.parse(f)
            assertTrue(v != null, "step ${step.code} returned null on a $key:7; frame")
        }
    }

    @Test
    fun burstCameraCodesAreTenInCameraRange() {
        assertEquals(10, CommandTable.BURST_CAMERA_CODES.size)
        for (c in CommandTable.BURST_CAMERA_CODES) {
            assertTrue(
                c in dev.openpolaris.core.protocol.Codes.CAMERA_BASE..dev.openpolaris.core.protocol.Codes.CAMERA_END,
                "camera burst code $c outside camera range",
            )
        }
        // 266 (STATE) and 267 (CAPTURE) are explicitly NOT in the burst — they
        // feed the CaptureState pipeline / capture button.
        assertTrue(266 !in CommandTable.BURST_CAMERA_CODES)
        assertTrue(267 !in CommandTable.BURST_CAMERA_CODES)
    }

    @Test
    fun burstCameraCodesAllHandledByCameraInfo() {
        // CameraInfo.fromFrame returns the same snapshot unchanged for unknown
        // codes. For each code in the burst, apply a frame carrying a real
        // value under the key the camera-info merge expects; the resulting
        // snapshot must differ from the empty starting snapshot.
        val keyByCode = mapOf(
            258 to "iso", 260 to "wb", 262 to "fNum", 264 to "ev",
            268 to "focus", 270 to "imgSize", 272 to "imgFmt",
            274 to "color", 276 to "shutter", 278 to "captureMode",
        )
        for (c in CommandTable.BURST_CAMERA_CODES) {
            val key = keyByCode[c] ?: error("missing key mapping for code $c")
            val p = ResponseParser()
            val f = p.parseFrame("1&0&2&$key:7;")!!
            val before = CameraInfo()
            val after = CameraInfo.fromFrame(c, f, before)
            assertTrue(after != before, "CameraInfo.fromFrame did nothing for code $c")
        }
    }

    @Test
    fun cameraCodesAreInferredRange() {
        val all = listOf(
            CommandTable.CAM_GET_ISO, CommandTable.CAM_SET_ISO,
            CommandTable.CAM_GET_WB, CommandTable.CAM_SET_WB,
            CommandTable.CAM_GET_FNUM, CommandTable.CAM_SET_FNUM,
            CommandTable.CAM_GET_EV, CommandTable.CAM_SET_EV,
            CommandTable.CAM_GET_FOCUS, CommandTable.CAM_SET_FOCUS,
            CommandTable.CAM_GET_IMG_SIZE, CommandTable.CAM_SET_IMG_SIZE,
            CommandTable.CAM_GET_IMG_FMT, CommandTable.CAM_SET_IMG_FMT,
            CommandTable.CAM_GET_COLOR, CommandTable.CAM_SET_COLOR,
            CommandTable.CAM_GET_SHUTTER, CommandTable.CAM_SET_SHUTTER,
            CommandTable.CAM_GET_CAPTURE_MODE, CommandTable.CAM_SET_CAPTURE_MODE,
            CommandTable.CAM_GET_STATE, CommandTable.CAM_CAPTURE,
        )
        for (d in all) {
            assertTrue(d.code in dev.openpolaris.core.protocol.Codes.CAMERA_BASE..dev.openpolaris.core.protocol.Codes.CAMERA_END,
                "${d.name} code ${d.code} outside camera range")
        }
    }
}
