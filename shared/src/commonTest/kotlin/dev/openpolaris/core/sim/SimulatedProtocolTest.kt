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

    @Test
    fun camGetStateReflectsTracking() {
        // Baseline: tracking off → state:0
        val off = sim.handle(Codes.CAM_GET_STATE, emptyMap())!!
        val offFrame = String(off[0])
        assertTrue(offFrame.contains("state:0;"), "expected state:0 when tracking off, got $offFrame")
        assertTrue(offFrame.contains("bulb:0;"))
        assertTrue(offFrame.contains("c:0;"))

        // Enable tracking, expect state:1
        assertNotNull(sim.handle(Codes.SET_TRACK_AU_STATE, StringMap("state" to 1)))
        val on = sim.handle(Codes.CAM_GET_STATE, emptyMap())!!
        val onFrame = String(on[0])
        assertTrue(onFrame.contains("state:1;"), "expected state:1 when tracking on, got $onFrame")
    }

    @Test
    fun camGetStateFrameIsParseable() {
        // The simulator's 266 response should be accepted by the
        // CommandTable.CAM_GET_STATE parser (the same one AppViewModel uses
        // in its 2s poll). A non-null CaptureState proves end-to-end wiring.
        val resp = sim.handle(Codes.CAM_GET_STATE, emptyMap())!!
        val frame = String(resp[0])
        val parsedFrame = ResponseParser().parseFrame(frame)
        assertNotNull(parsedFrame, "ResponseParser could not parse 266 frame: $frame")
        val parsed = dev.openpolaris.core.protocol.CommandTable.CAM_GET_STATE.parse?.invoke(parsedFrame)
        assertNotNull(parsed, "CAM_GET_STATE parser returned null for: $frame")
        // c is the capture counter; the sim always emits 0 for 266 GETs.
        assertEquals(0, parsed.c)
    }

    /**
     * Audit roadmap PR 5: every descriptor in CommandTable.ALL with a typed `parse`
     * lambda must be explicitly dispatched by SimulatedProtocol. The signal is the
     * typed parser: a real handler returns a payload that decodes into T; a code
     * that fell through to the generic default branch returns `ret:0` only, which
     * the typed parser rejects. The audit doc's specific deliverable was
     * "every `CommandTable` descriptor that has a non-`null` `parse` lambda must
     * have a matching `SimulatedProtocol` dispatch entry" — see
     * docs/PROTOCOL-CODE-AUDIT-2026-08-31.md PR 5 (lines 233-238).
     */
    @Test
    fun commandTableCodesAreDispatchedInSimulator() {
        val parser = ResponseParser()
        for (descriptor in dev.openpolaris.core.protocol.CommandTable.ALL.values.flatten()) {
            val parseLambda = descriptor.parse ?: continue
            val resp = sim.handle(descriptor.code, emptyMap())
            assertNotNull(
                resp,
                "${descriptor.name} (code ${descriptor.code}): handle() returned null",
            )
            assertTrue(
                resp.isNotEmpty(),
                "${descriptor.name} (code ${descriptor.code}): handle() returned no frames",
            )
            val frame = String(resp[0])
            assertTrue(
                frame.endsWith("#"),
                "${descriptor.name} (code ${descriptor.code}): frame missing trailing #: $frame",
            )
            val parsedFrame = parser.parseFrame(frame)
            assertNotNull(
                parsedFrame,
                "${descriptor.name} (code ${descriptor.code}): ResponseParser rejected frame: $frame",
            )
            val parsed = parseLambda(parsedFrame)
            assertNotNull(
                parsed,
                "${descriptor.name} (code ${descriptor.code}): typed parser returned null for: $frame — " +
                    "code likely fell through to the generic default branch in SimulatedProtocol.handle()",
            )
        }
    }

    // The second PR-5 idea (asserting every SET-style descriptor must produce
    // an ack response) was dropped: many of the SET codes in CommandTable
    // (e.g. SET_SETTLING_TIME = 544, FILE_UPLOAD_CHUNK = 794, FILE_UPLOAD_END
    // = 795, all 10 CAM_SET_*) are marked UNVERIFIED or "(gap)" in
    // docs/PROTOCOL-CODE-AUDIT-2026-08-31.md — we don't know whether the
    // real firmware acks them. Pre-existing tests in this file already
    // encode the "fire-and-forget" assumption for several of them. Adding a
    // blanket "all SETs must ack" assertion would either force us to invent
    // behavior for speculative codes, or break intentional pre-existing
    // tests. Reintroduce this assertion case-by-case as codes become
    // verified via live capture.

    // ---- Issue #32: TILT / LIMITS / AUTO_LEVEL state decoupling -------------
    //
    // Bug: GET_TILT_STATE and GET_LIMIT_STATE previously reported
    // ditherState / autoLevelEnabled respectively, and GET_AUTO_LEVEL_EN
    // shared a branch with SET_AUTO_LEVEL_EN that mutated
    // autoLevelEnabled. These tests pin the corrected semantics: each
    // state has its own slot, GETs never mutate, and SETs are round-
    // trippable.
    //
    // We use a fresh simulator per test (lazy) so state doesn't leak.

    private fun newSim() = SimulatedProtocol()

    private fun fieldOf(frame: String, key: String): String? {
        val marker = "$key:"
        val start = frame.indexOf(marker)
        if (start < 0) return null
        val after = start + marker.length
        val end = frame.indexOf(';', after)
        return if (end < 0) null else frame.substring(after, end)
    }

    @Test
    fun tiltGetDoesNotMutateTiltOrDitherState() {
        val s = newSim()
        // Sanity: GET returns 0
        val resp0 = s.handle(Codes.GET_TILT_STATE, emptyMap())
        assertEquals("0", fieldOf(String(resp0!![0]), "state"))
        // GET must not have any side effect: state vars untouched
        assertEquals(0, s.tiltState, "GET_TILT_STATE must not mutate tiltState")
        assertEquals(0, s.ditherState, "GET_TILT_STATE must not mutate ditherState")
        // Call GET 50 more times — still no mutation
        repeat(50) { s.handle(Codes.GET_TILT_STATE, emptyMap()) }
        assertEquals(0, s.tiltState)
        assertEquals(0, s.ditherState)
    }

    @Test
    fun tiltSetThenGetReturnsSetValue() {
        val s = newSim()
        s.handle(Codes.SET_TILT_STATE, StringMap("state" to 1))
        assertEquals(1, s.tiltState)
        val resp = s.handle(Codes.GET_TILT_STATE, emptyMap())
        assertEquals("1", fieldOf(String(resp!![0]), "state"))
    }

    @Test
    fun ditherAndTiltStatesAreIndependent() {
        val s = newSim()
        s.handle(Codes.SET_TILT_STATE, StringMap("state" to 1))
        s.handle(Codes.SET_DITHER_STATE, StringMap("state" to 1))
        assertEquals(1, s.tiltState, "TILT setter must not touch ditherState")
        assertEquals(1, s.ditherState, "DITHER setter must not touch tiltState")
        val tilt = s.handle(Codes.GET_TILT_STATE, emptyMap())
        val dither = s.handle(Codes.GET_DITHER_STATE, emptyMap())
        assertEquals("1", fieldOf(String(tilt!![0]), "state"))
        assertEquals("1", fieldOf(String(dither!![0]), "state"))
    }

    @Test
    fun limitsGetDoesNotMutateAutoLevel() {
        val s = newSim()
        // Set autoLevel true via SET
        s.handle(Codes.SET_AUTO_LEVEL_EN, StringMap("en" to 1))
        assertEquals(true, s.autoLevelEnabled)
        // Now issue GET_LIMIT_STATE — must not touch autoLevelEnabled
        s.handle(Codes.GET_LIMIT_STATE, emptyMap())
        assertEquals(true, s.autoLevelEnabled, "GET_LIMIT_STATE must not mutate autoLevelEnabled")
    }

    @Test
    fun limitsGetReportsOwnStateNotAutoLevel() {
        val s = newSim()
        // Set autoLevel true but limits false
        s.handle(Codes.SET_AUTO_LEVEL_EN, StringMap("en" to 1))
        s.handle(Codes.SET_LIMIT_STATE, StringMap("limit" to 0))
        // GET_LIMIT_STATE must report limitState (0), not autoLevelEnabled (1)
        val resp = s.handle(Codes.GET_LIMIT_STATE, emptyMap())
        assertEquals("0", fieldOf(String(resp!![0]), "limit"))
    }

    @Test
    fun limitsSetThenGetReturnsSetValue() {
        val s = newSim()
        s.handle(Codes.SET_LIMIT_STATE, StringMap("limit" to 1))
        assertEquals(1, s.limitState)
        val resp = s.handle(Codes.GET_LIMIT_STATE, emptyMap())
        assertEquals("1", fieldOf(String(resp!![0]), "limit"))
    }

    @Test
    fun limitsSetDoesNotMutateAutoLevel() {
        val s = newSim()
        s.handle(Codes.SET_LIMIT_STATE, StringMap("limit" to 1))
        assertEquals(false, s.autoLevelEnabled, "SET_LIMIT_STATE must not mutate autoLevelEnabled")
    }

    @Test
    fun autoLevelGetDoesNotMutateAutoLevel() {
        val s = newSim()
        s.handle(Codes.SET_AUTO_LEVEL_EN, StringMap("en" to 1))
        assertEquals(true, s.autoLevelEnabled)
        // GET has no `en` field; the old shared branch reset to false
        s.handle(Codes.GET_AUTO_LEVEL_EN, emptyMap())
        assertEquals(true, s.autoLevelEnabled, "GET_AUTO_LEVEL_EN must not mutate autoLevelEnabled")
    }

    @Test
    fun autoLevelGetReturnsCurrentValue() {
        val s = newSim()
        s.handle(Codes.SET_AUTO_LEVEL_EN, StringMap("en" to 1))
        val resp = s.handle(Codes.GET_AUTO_LEVEL_EN, emptyMap())
        assertEquals("1", fieldOf(String(resp!![0]), "en"))
        s.handle(Codes.SET_AUTO_LEVEL_EN, StringMap("en" to 0))
        val resp2 = s.handle(Codes.GET_AUTO_LEVEL_EN, emptyMap())
        assertEquals("0", fieldOf(String(resp2!![0]), "en"))
    }

    @Test
    fun autoLevelSetFalseToTrueRoundTrip() {
        val s = newSim()
        assertEquals(false, s.autoLevelEnabled)
        s.handle(Codes.SET_AUTO_LEVEL_EN, StringMap("en" to 1))
        val resp = s.handle(Codes.GET_AUTO_LEVEL_EN, emptyMap())
        assertEquals("1", fieldOf(String(resp!![0]), "en"))
    }
}
