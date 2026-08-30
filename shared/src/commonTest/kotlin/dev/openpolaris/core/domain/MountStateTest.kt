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

    @Test
    fun cameraInfoSingleFieldPopulatesOnlyThatField() {
        val f = ResponseParser().parseFrame("1&258&2&iso:0;ret:0;")!!
        val info = CameraInfo.fromFrame(258, f)
        assertEquals(0, info.iso)
        assertNull(info.wb)
        assertNull(info.fNum)
        assertNull(info.ev)
        assertNull(info.focus)
        assertNull(info.imgSize)
        assertNull(info.imgFmt)
        assertNull(info.color)
        assertNull(info.shutter)
        assertNull(info.captureMode)
    }

    @Test
    fun cameraInfoMergesAllTenFramesIntoFullState() {
        var info: CameraInfo = CameraInfo()
        info = CameraInfo.fromFrame(258, ResponseParser().parseFrame("1&258&2&iso:5;ret:0;")!!, info)
        info = CameraInfo.fromFrame(260, ResponseParser().parseFrame("1&260&2&wb:3;ret:0;")!!, info)
        info = CameraInfo.fromFrame(262, ResponseParser().parseFrame("1&262&2&fNum:7;ret:0;")!!, info)
        info = CameraInfo.fromFrame(264, ResponseParser().parseFrame("1&264&2&ev:2;ret:0;")!!, info)
        info = CameraInfo.fromFrame(268, ResponseParser().parseFrame("1&268&2&focus:0;ret:0;")!!, info)
        info = CameraInfo.fromFrame(270, ResponseParser().parseFrame("1&270&2&imgSize:2;ret:0;")!!, info)
        info = CameraInfo.fromFrame(272, ResponseParser().parseFrame("1&272&2&imgFmt:1;ret:0;")!!, info)
        info = CameraInfo.fromFrame(274, ResponseParser().parseFrame("1&274&2&color:3;ret:0;")!!, info)
        info = CameraInfo.fromFrame(276, ResponseParser().parseFrame("1&276&2&shutter:4;ret:0;")!!, info)
        info = CameraInfo.fromFrame(278, ResponseParser().parseFrame("1&278&2&captureMode:1;ret:0;")!!, info)
        assertEquals(5, info.iso)
        assertEquals(3, info.wb)
        assertEquals(7, info.fNum)
        assertEquals(2, info.ev)
        assertEquals(0, info.focus)
        assertEquals(2, info.imgSize)
        assertEquals(1, info.imgFmt)
        assertEquals(3, info.color)
        assertEquals(4, info.shutter)
        assertEquals(1, info.captureMode)
    }

    @Test
    fun cameraInfoUnknownCodePassesThrough() {
        val seed = CameraInfo(iso = 5, wb = 3)
        val f = ResponseParser().parseFrame("1&999&2&anything:42;ret:0;")!!
        val info = CameraInfo.fromFrame(999, f, seed)
        assertEquals(seed, info)
    }

    @Test
    fun cameraInfoSkipsStateAndCaptureCodes() {
        // 266 = CAM_GET_STATE (goes to CaptureState pipeline, not CameraInfo)
        // 267 = CAM_CAPTURE (action, not a parameter)
        val seed = CameraInfo(iso = 5)
        val fState = ResponseParser().parseFrame("1&266&2&state:2;ret:0;")!!
        val fCap = ResponseParser().parseFrame("1&267&2&ret:0;")!!
        val afterState = CameraInfo.fromFrame(266, fState, seed)
        val afterCap = CameraInfo.fromFrame(267, fCap, afterState)
        assertEquals(seed, afterState)
        assertEquals(seed, afterCap)
    }

    @Test
    fun cameraInfoMissingKeyLeavesFieldNull() {
        val f = ResponseParser().parseFrame("1&258&2&ret:0;")!!
        val info = CameraInfo.fromFrame(258, f)
        assertNull(info.iso)
    }
}
