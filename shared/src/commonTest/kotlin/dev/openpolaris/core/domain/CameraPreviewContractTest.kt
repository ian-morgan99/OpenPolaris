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
 * Contract tests for the camera liveview (preview) commands 291/292, derived from
 * the Benro Connect APK and live-verified on K-3 III v9d (docs/PROTOCOL.md §3.4.2,
 * issue #80 evidence). These pin:
 *  - the exact outgoing payload including subtype 2 (preview frames use type 2,
 *    unlike the subtype-1 focus jogs);
 *  - SET 291 replies carry `state:` + `ret:` — success requires explicit ret >= 0;
 *  - GET 292 replies carry `state:` but NO `ret` (live capture: `292@state:1;#`) —
 *    a missing ret must not be treated as failure for the query;
 *  - timeout / protocol-error handling never reporting success.
 */
class CameraPreviewContractTest {

    private fun newSession(
        conn: FakeConnection,
        scope: CoroutineScope,
    ): Pair<MountSession, CameraController> {
        val s = MountSession({ conn }, readerScope = scope)
        return s to CameraController(s)
    }

    @Test
    fun `preview on sends exact state 1 payload with subtype 2`() = runTest {
        val conn = FakeConnection()
        // Live-captured K-3 III v9d reply shape: `291@state:1;ret:0;#`.
        conn.responses += "1&291&2&state:1;ret:0;#".toByteArray(Charsets.US_ASCII)
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        val result = c.setCameraPreview(on = true)

        // Exact wire frame: code 291, subtype 2, payload `state:1;`.
        val written = String(conn.written.last(), Charsets.US_ASCII)
        assertEquals("1&291&2&state:1;#", written)
        assertTrue(result.accepted, "ret:0 must count as accepted")
        assertEquals(1, result.state)
        assertEquals(0, result.ret)
        s.disconnect()
    }

    @Test
    fun `preview off sends exact state 0 payload`() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&291&2&state:0;ret:0;#".toByteArray(Charsets.US_ASCII)
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        val result = c.setCameraPreview(on = false)

        val written = String(conn.written.last(), Charsets.US_ASCII)
        assertEquals("1&291&2&state:0;#", written)
        assertTrue(result.accepted)
        assertEquals(0, result.state)
        s.disconnect()
    }

    @Test
    fun `preview query sends empty payload with subtype 2`() = runTest {
        val conn = FakeConnection()
        // Live-captured K-3 III v9d reply: `292@state:1;#` — note NO ret field.
        conn.responses += "1&292&2&state:1;#".toByteArray(Charsets.US_ASCII)
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        val result = c.queryCameraPreview()

        val written = String(conn.written.last(), Charsets.US_ASCII)
        assertEquals("1&292&2&-100#", written)
        // The query succeeded and reported state; the absent ret is normal for 292.
        assertTrue(result.sent)
        assertEquals(1, result.state)
        assertNull(result.ret, "the live-captured 292 reply carries no ret field")
        s.disconnect()
    }

    @Test
    fun `preview set with negative ret is not accepted`() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&291&2&state:1;ret:-1;#".toByteArray(Charsets.US_ASCII)
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        val result = c.setCameraPreview(on = true)

        assertFalse(result.accepted, "ret:-1 must not display as success")
        assertEquals(-1, result.ret)
        s.disconnect()
    }

    @Test
    fun `preview set without ret is not accepted`() = runTest {
        val conn = FakeConnection()
        // A SET reply missing its ret field: the state echo alone must not be
        // treated as an acknowledgement.
        conn.responses += "1&291&2&state:1;#".toByteArray(Charsets.US_ASCII)
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        val result = c.setCameraPreview(on = true)

        assertFalse(result.accepted, "a SET acknowledgement must contain ret")
        assertEquals(1, result.state)
        assertNull(result.ret)
        s.disconnect()
    }

    @Test
    fun `preview timeout is reported as not sent, not success`() = runTest {
        val conn = FakeConnection()
        // No response queued: the 10s preview timeout must elapse in virtual time.
        val (s, c) = newSession(conn, backgroundScope)
        s.connect()

        val result = c.setCameraPreview(on = true)

        assertFalse(result.accepted)
        assertFalse(result.sent)
        assertEquals("TIMEOUT", result.error)
        s.disconnect()
    }

    @Test
    fun `constants match the BenroCamera registry`() {
        assertEquals(291, Codes.BenroCamera.SET_CAMERA_PREVIEW)
        assertEquals(292, Codes.BenroCamera.GET_CAMERA_PREVIEW)
        // The pre-existing top-level constants must stay in lockstep.
        assertEquals(Codes.CAM_LIVEVIEW_SET, Codes.BenroCamera.SET_CAMERA_PREVIEW)
        assertEquals(Codes.CAM_LIVEVIEW_GET, Codes.BenroCamera.GET_CAMERA_PREVIEW)
    }
}
