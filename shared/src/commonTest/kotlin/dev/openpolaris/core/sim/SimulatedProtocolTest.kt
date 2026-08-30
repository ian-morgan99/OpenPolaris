package dev.openpolaris.core.sim

import dev.openpolaris.core.domain.TaskList
import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.ResponseParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimulatedProtocolTest {
    private val sim = SimulatedProtocol()

    private fun StringMap(vararg pairs: Pair<String, Any>) =
        pairs.associate { it.first to it.second.toString() }

    @Test
    fun statusPollReturnsModePayload() {
        val resp = sim.handle(Codes.PUSH_MODE_STATE, emptyMap())
        assertNotNull(resp)
        assertTrue(resp.isNotEmpty(), "expected non-empty response")
        val first = String(resp[0])
        assertTrue(first.endsWith("#"), "frame must end with #: $first")
    }

    @Test
    fun gimbalPosReturnsFloats() {
        val resp = sim.handle(Codes.GET_GIMBAL_POS, emptyMap())
        assertNotNull(resp)
        val first = String(resp[0])
        assertTrue(first.endsWith("#"))
    }

    @Test
    fun trackHalfSpeedInversion() {
        // halfSpeed:0 means ON per RE quirk
        val setResp = sim.handle(Codes.SET_TRACK_HALF_SPEED, StringMap("halfSpeed" to 0))
        assertNotNull(setResp)
    }

    @Test
    fun gotoAzAltEchoesValues() {
        val resp = sim.handle(
            Codes.SET_GOTO_AU_STATE,
            StringMap("az" to 123.45678, "alt" to -12.5)
        )
        assertNotNull(resp)
        val first = String(resp[0])
        assertTrue(first.endsWith("#"))
    }

    @Test
    fun cameraIsoGetAndSet() {
        val setResp = sim.handle(Codes.CAM_SET_ISO, StringMap("iso" to 0))
        assertNotNull(setResp)
        val getResp = sim.handle(Codes.CAM_GET_ISO, emptyMap())
        assertNotNull(getResp)
    }

    @Test
    fun cameraFocusGetAndSet() {
        assertNotNull(sim.handle(Codes.CAM_SET_FOCUS, StringMap("focus" to 0)))
        assertNotNull(sim.handle(Codes.CAM_GET_FOCUS, emptyMap()))
    }

    @Test
    fun cameraImgSizeGetAndSet() {
        assertNotNull(sim.handle(Codes.CAM_SET_IMG_SIZE, StringMap("imgSize" to 2)))
        assertNotNull(sim.handle(Codes.CAM_GET_IMG_SIZE, emptyMap()))
    }

    @Test
    fun cameraImgFmtGetAndSet() {
        assertNotNull(sim.handle(Codes.CAM_SET_IMG_FMT, StringMap("imgFmt" to 1)))
        assertNotNull(sim.handle(Codes.CAM_GET_IMG_FMT, emptyMap()))
    }

    @Test
    fun cameraColorGetAndSet() {
        assertNotNull(sim.handle(Codes.CAM_SET_COLOR, StringMap("color" to 3)))
        assertNotNull(sim.handle(Codes.CAM_GET_COLOR, emptyMap()))
    }

    @Test
    fun cameraShutterGetAndSet() {
        assertNotNull(sim.handle(Codes.CAM_SET_SHUTTER, StringMap("shutter" to 4)))
        assertNotNull(sim.handle(Codes.CAM_GET_SHUTTER, emptyMap()))
    }

    @Test
    fun cameraCaptureModeGetAndSet() {
        assertNotNull(sim.handle(Codes.CAM_SET_CAPTURE_MODE, StringMap("captureMode" to 0)))
        assertNotNull(sim.handle(Codes.CAM_GET_CAPTURE_MODE, emptyMap()))
    }

    @Test
    fun wifiBandGet() {
        val resp = sim.handle(Codes.GET_WIFI_BAND, emptyMap())
        assertNotNull(resp)
    }

    @Test
    fun batteryStatus() {
        val resp = sim.handle(Codes.BATTERY_STATUS, emptyMap())
        assertNotNull(resp)
    }

    @Test
    fun sdStatus() {
        val resp = sim.handle(Codes.FILE_SD_STATUS, emptyMap())
        assertNotNull(resp)
    }

    @Test
    fun unhandledCodeReturnsGenericAck() {
        val resp = sim.handle(999, emptyMap())
        assertNotNull(resp)
        val first = String(resp[0])
        assertTrue(first.contains("ret:0"), "expected ret:0 ack in: $first")
    }

    @Test
    fun appHandshakeFlow() {
        val hello = sim.handle(Codes.APP_HELLO, emptyMap())
        assertNotNull(hello)
        val ping = sim.handle(Codes.APP_PING, emptyMap())
        assertNotNull(ping)
    }

    @Test
    fun omsRunState() {
        val resp = sim.handle(Codes.OMS_RUN_STATE, emptyMap())
        assertNotNull(resp)
    }

    @Test
    fun omsTaskList() {
        val resp = sim.handle(Codes.OMS_TASK_LIST, emptyMap())
        assertNotNull(resp)
        val first = String(resp[0])
        assertTrue(first.endsWith("#"), "frame must end with #: $first")

        // Strip "1&<code>&2&" envelope + trailing '#' to feed ResponseParser.
        val payload = first.removeSuffix("#").substringAfter("&2&")
        val f = ResponseParser().parseFrame("1&${Codes.OMS_TASK_LIST}&2&$payload")!!
        val list = TaskList.fromFrame(f)
        assertNotNull(list, "TaskList.fromFrame must succeed for simulator output")
        assertEquals(2, list.count, "expected the seeded 2-task table")
        assertEquals(2, list.tasks.size)
        assertEquals("Slew", list.tasks[0].name)
        assertEquals(0, list.tasks[0].id)
        assertEquals(0, list.tasks[0].state)
        assertEquals("Pan", list.tasks[1].name)
        assertEquals(1, list.tasks[1].id)
    }

    @Test
    fun spTestMultiStep() {
        for (step in 1..8) {
            val resp = sim.handle(Codes.SP_TEST, StringMap("step" to step))
            assertNotNull(resp, "step $step returned null")
        }
    }

    @Test
    fun fileList() {
        val resp = sim.handle(Codes.FILE_LIST, emptyMap())
        assertNotNull(resp)
    }

    @Test
    fun exAxisStaQuery() {
        val resp = sim.handle(Codes.EX_AXIS_STA, emptyMap())
        assertNotNull(resp)
        assertTrue(resp.isNotEmpty(), "expected non-empty response for query")
        val first = String(resp[0])
        assertTrue(first.contains("state:"), "expected state: field in: $first")
        assertTrue(first.contains("angle:"), "expected angle: field in: $first")
    }

    @Test
    fun setSettlingTimeIsSetter() {
        // 544 is a setter in the real protocol — no response, mutates internal state.
        val resp = sim.handle(Codes.SET_SETTLING_TIME, StringMap("time" to 2500))
        assertNotNull(resp)
        assertTrue(resp.isEmpty(), "setter must produce no response, got ${resp.size} frame(s)")
    }

    @Test
    fun batteryDetail() {
        val resp = sim.handle(Codes.BATTERY_DETAIL, emptyMap())
        assertNotNull(resp)
        assertTrue(resp.isNotEmpty())
    }
}
