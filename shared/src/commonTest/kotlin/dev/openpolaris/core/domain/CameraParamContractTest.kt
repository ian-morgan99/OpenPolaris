@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.openpolaris.core.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import dev.openpolaris.core.protocol.Codes

/**
 * Contract tests for the task-3 camera parameter commands (docs/PROTOCOL.md §3.4.3,
 * APK-derived): image format 282, control mode 296/297, exposure time 298/299,
 * interval type 306/307. These pin the quirks that differ from the rest of the
 * camera cluster:
 *  - 282 uses subtype **4** (the only camera code that does);
 *  - 306 GET sends a literal **empty** payload, not `-100`;
 *  - 307 SET has **no trailing semicolon** and is fire-and-forget;
 *  - SET replies (297/299) carry only `ret:` — success requires explicit ret >= 0;
 *  - unobserved values are rejected before writing.
 */
class CameraParamContractTest {

    private fun newSession(
        conn: FakeConnection,
        scope: CoroutineScope,
    ): Pair<MountSession, CameraController> {
        val s = MountSession({ conn }, readerScope = scope)
        return s to CameraController(s)
    }

    @Test
    fun `image format query uses subtype 4 and -100 payload`() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&282&4&format:RAW;#".toByteArray(Charsets.US_ASCII)
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        val result = c.queryImageFormat()

        // Exact wire frame: code 282, subtype 4, payload -100.
        val written = String(conn.written.last(), Charsets.US_ASCII)
        assertEquals("1&282&4&-100#", written)
        assertTrue(result.sent)
        assertEquals("RAW", result.value)
        s.disconnect()
    }

    @Test
    fun `control mode query sends -100 with subtype 2`() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&296&2&mode:1;#".toByteArray(Charsets.US_ASCII)
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        val result = c.queryControlMode()

        val written = String(conn.written.last(), Charsets.US_ASCII)
        assertEquals("1&296&2&-100#", written)
        assertEquals("1", result.value)
        s.disconnect()
    }

    @Test
    fun `control mode set sends exact mode payload with subtype 2`() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&297&2&ret:0;#".toByteArray(Charsets.US_ASCII)
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        val result = c.setControlMode(mode = 1)

        val written = String(conn.written.last(), Charsets.US_ASCII)
        assertEquals("1&297&2&mode:1;#", written)
        assertTrue(result.accepted, "ret:0 must count as accepted")
        assertEquals(0, result.ret)
        s.disconnect()
    }

    @Test
    fun `control mode set rejects unobserved values before writing`() = runTest {
        val conn = FakeConnection()
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()
        val writesBefore = conn.written.size

        val result = c.setControlMode(mode = 2)

        assertFalse(result.sent)
        assertEquals(writesBefore, conn.written.size)
        s.disconnect()
    }

    @Test
    fun `exposure time query sends -100 with subtype 2`() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&298&2&ExTime:3;#".toByteArray(Charsets.US_ASCII)
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        val result = c.queryExposureTime()

        val written = String(conn.written.last(), Charsets.US_ASCII)
        assertEquals("1&298&2&-100#", written)
        assertEquals("3", result.value)
        s.disconnect()
    }

    @Test
    fun `exposure time set sends exact ExTime payload`() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&299&2&ret:0;#".toByteArray(Charsets.US_ASCII)
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        val result = c.setExposureTime(index = 3)

        val written = String(conn.written.last(), Charsets.US_ASCII)
        assertEquals("1&299&2&ExTime:3;#", written)
        assertTrue(result.accepted)
        s.disconnect()
    }

    @Test
    fun `exposure time set rejects negative index before writing`() = runTest {
        val conn = FakeConnection()
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()
        val writesBefore = conn.written.size

        val result = c.setExposureTime(index = -1)

        assertFalse(result.sent)
        assertEquals(writesBefore, conn.written.size)
        s.disconnect()
    }

    @Test
    fun `interval type query sends literal empty payload not -100`() = runTest {
        val conn = FakeConnection()
        // The stock app broadcasts the whole reply with no field parse.
        conn.responses += "1&306&2&type:1;#".toByteArray(Charsets.US_ASCII)
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        val result = c.queryIntervalType()

        // Exact wire frame: code 306, subtype 2, EMPTY payload (not -100).
        val written = String(conn.written.last(), Charsets.US_ASCII)
        assertEquals("1&306&2&#", written)
        assertTrue(result.sent)
        s.disconnect()
    }

    @Test
    fun `interval type set sends no trailing semicolon`() = runTest {
        val conn = FakeConnection()
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        val result = c.setIntervalType(type = 1)

        // Exact wire frame: code 307, subtype 2, payload `type:1` with NO trailing `;`.
        val written = String(conn.written.last(), Charsets.US_ASCII)
        assertEquals("1&307&2&type:1#", written)
        assertTrue(result.sent)
        s.disconnect()
    }

    @Test
    fun `interval type set rejects unobserved values before writing`() = runTest {
        val conn = FakeConnection()
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()
        val writesBefore = conn.written.size

        val result = c.setIntervalType(type = 5)

        assertFalse(result.sent)
        assertEquals(writesBefore, conn.written.size)
        s.disconnect()
    }

    @Test
    fun `control mode set with negative ret is not accepted`() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&297&2&ret:-1;#".toByteArray(Charsets.US_ASCII)
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        val result = c.setControlMode(mode = 0)

        assertFalse(result.accepted, "ret:-1 must not display as success")
        assertEquals(-1, result.ret)
        s.disconnect()
    }

    @Test
    fun `constants match the BenroCamera registry`() {
        assertEquals(282, Codes.BenroCamera.GET_IMG_FORMAT)
        assertEquals(296, Codes.BenroCamera.GET_CONTROL_MODE)
        assertEquals(297, Codes.BenroCamera.SET_CONTROL_MODE)
        assertEquals(298, Codes.BenroCamera.GET_EX_TIME)
        assertEquals(299, Codes.BenroCamera.SET_EX_TIME)
        assertEquals(306, Codes.BenroCamera.GET_INTERVAL_TYPE)
        assertEquals(307, Codes.BenroCamera.SET_INTERVAL_TYPE)
    }
}
