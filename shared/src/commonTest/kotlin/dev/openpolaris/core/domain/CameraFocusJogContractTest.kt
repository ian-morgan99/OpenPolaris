@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import dev.openpolaris.core.protocol.Codes

/**
 * Contract tests for the focus jog (262) and MF adjust (311) payloads, derived
 * from the Benro Connect APK (see docs/PROTOCOL.md §3.4.1). These pin:
 *  - the exact outgoing payload including case and trailing semicolon;
 *  - subtype 1 in the request frame (camera SET/jog frames use type 1, not 2);
 *  - ret-only reply parsing (no INFO read-back exists for these codes);
 *  - timeout / protocol-error handling never reporting success.
 */
class CameraFocusJogContractTest {

    private fun newSession(conn: FakeConnection, scope: CoroutineScope): Pair<MountSession, CameraController> {
        val s = MountSession({ conn }, readerScope = scope)
        return s to CameraController(s)
    }

    @Test
    fun `focus jog sends exact mod and f payload with subtype 1`() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&262&2&ret:0;#".toByteArray(Charsets.US_ASCII)
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        val result = c.jogFocus(mod = 1, speed = 6)

        // Exact wire frame: code 262, subtype 1, payload `mod:1;f:6;`.
        val written = String(conn.written.last(), Charsets.US_ASCII)
        assertEquals("1&262&1&mod:1;f:6;#", written)
        assertTrue(result.accepted, "ret:0 must count as accepted")
        assertEquals(0, result.ret)
        s.disconnect()
    }

    @Test
    fun `focus jog stop sends mod 0 f 0`() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&262&2&ret:0;#".toByteArray(Charsets.US_ASCII)
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        c.jogFocus(mod = 0, speed = 0)

        val written = String(conn.written.last(), Charsets.US_ASCII)
        assertEquals("1&262&1&mod:0;f:0;#", written)
        s.disconnect()
    }

    @Test
    fun `mf adjust sends exact mode and adj payload with subtype 1`() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&311&2&ret:0;#".toByteArray(Charsets.US_ASCII)
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        // Focus-track add-fast value observed in the stock app.
        val result = c.adjustManualFocus(mode = 1, adj = -4)

        val written = String(conn.written.last(), Charsets.US_ASCII)
        assertEquals("1&311&1&mode:1;adj:-4;#", written)
        assertTrue(result.accepted)
        s.disconnect()
    }

    @Test
    fun `negative ret is not accepted`() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&262&2&ret:-1;#".toByteArray(Charsets.US_ASCII)
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        val result = c.jogFocus(mod = 1, speed = 1)

        assertFalse(result.accepted, "ret:-1 must not display as success")
        assertEquals(-1, result.ret)
        s.disconnect()
    }

    @Test
    fun `reply without ret is not accepted`() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&262&2&state:0;#".toByteArray(Charsets.US_ASCII)
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        val result = c.jogFocus(mod = 1, speed = 1)

        assertFalse(result.accepted, "a jog acknowledgement must contain ret")
        assertEquals(null, result.ret)
        s.disconnect()
    }

    @Test
    fun `unobserved jog values fail before writing`() = runTest {
        val conn = FakeConnection()
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()
        val writesBefore = conn.written.size

        val jog = c.jogFocus(mod = 1, speed = 3)
        val adjust = c.adjustManualFocus(mode = 1, adj = 2)

        assertFalse(jog.sent)
        assertFalse(adjust.sent)
        assertEquals(writesBefore, conn.written.size)
        s.disconnect()
    }

    @Test
    fun `timeout is reported as not sent, not success`() = runTest {
        val conn = FakeConnection()
        // No response queued: the 10s jog timeout must elapse.
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        val result = c.jogFocus(mod = 1, speed = 2)

        assertFalse(result.accepted)
        assertFalse(result.sent)
        assertEquals("TIMEOUT", result.error)
        s.disconnect()
    }

    @Test
    fun `constants match the BenroCamera registry`() {
        assertEquals(262, Codes.BenroCamera.SET_FOCUS)
        assertEquals(311, Codes.BenroCamera.SET_FOCUS_ADJ)
    }
}
