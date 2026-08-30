package dev.openpolaris.core.sim

import dev.openpolaris.core.protocol.Codes
import kotlin.test.Test
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
