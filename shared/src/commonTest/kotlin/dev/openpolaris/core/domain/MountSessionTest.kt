package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.ResponseParser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Lifecycle tests for [MountSession.lastError] (PLAN-CRITICAL-REVIEW §H).
 *
 * Strategy: stand up a [MountSession] with a [FakeConnection] that can be
 * made to drop on demand. Walk the four transitions:
 *   1. request before connect -> ProtocolError + lastError set.
 *   2. successful connect clears lastError.
 *   3. drop the socket, next request returns ProtocolError + lastError set.
 *   4. re-connect clears lastError again.
 */
class MountSessionTest {

    /** In-memory [Connection] that records writes and can be force-dropped. */
    private class FakeConnection : Connection {
        val written = mutableListOf<ByteArray>()
        val responses = mutableListOf<ByteArray>()
        var failConnect = false
        var failMessage: String? = null
        @Volatile var dropped = false

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) {
            if (failConnect) throw java.io.IOException("refused")
        }

        override suspend fun write(data: ByteArray) {
            if (dropped) throw java.io.IOException("socket closed")
            written += data
        }

        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            if (dropped) throw java.io.IOException("socket closed")
            if (responses.isEmpty()) return -1
            val r = responses.removeAt(0)
            r.copyInto(buffer)
            return r.size
        }

        override fun close() {}
    }

    /** Queue the canned replies [MountSession.connect] expects after the
     *  284 lifecycle handshake: the 820 auth probe (`needed:0` — most
     *  production firmware doesn't require a connection password) plus
     *  the 823 hello ack. Mirrors the helper in [MountSessionReaderTest]
     *  and friends; the FakeConnection class is per-file. */
    private fun FakeConnection.queueDefaultAuthOk() {
        responses += "1&820&2&needed:0;#".toByteArray(Charsets.US_ASCII)
        responses += "1&823&2&app:openpolaris;ver:0.1.0;#".toByteArray(Charsets.US_ASCII)
    }

    @Test
    fun requestBeforeConnectRecordsLastError() = runTest {
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)
        val result = s.request(517) { f -> ResponseParser().parseFrame("1&517&2&yaw:0;pitch:0;roll:0;")?.let { GimbalPosition.fromFrame517(it) } }
        assertIs<MountSession.CmdResult.ProtocolError>(result)
        val err = assertIs<MountSession.CmdResult.ProtocolError>(s.lastError)
        assertEquals("not connected", err.message)
    }

    @Test
    fun successfulConnectClearsLastError() = runTest {
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)
        // First, record an error
        s.request(517) { _ -> null }
        assertEquals(false, s.lastError == null)
        // Now connect successfully - the lifecycle 284 push needs a response,
        // but we don't care about the value, just that connect() returns true.
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
        val ok = s.connect()
        assertEquals(true, ok)
        assertNull(s.lastError)
        s.disconnect()
    }

    @Test
    fun socketDropSetsLastErrorOnNextRequest() = runTest {
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)
        // Connect successfully
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
        s.connect()
        assertNull(s.lastError)
        // Now force-drop the connection
        conn.dropped = true
        val result = s.request(517) { _ -> null }
        assertIs<MountSession.CmdResult.ProtocolError>(result)
        val err = assertIs<MountSession.CmdResult.ProtocolError>(s.lastError)
        // The FakeConnection's IOException message is "socket closed"
        assertEquals("socket closed", err.message)
        s.disconnect()
    }

    @Test
    fun reconnectAfterDropClearsLastError() = runTest {
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        // ...then the 820+823 auth handshake (see [queueDefaultAuthOk]).
        conn.queueDefaultAuthOk()
        s.connect()
        conn.dropped = true
        s.request(517) { _ -> null }
        // lastError is now set
        assertIs<MountSession.CmdResult.ProtocolError>(s.lastError)
        // Re-connect on a fresh socket
        conn.dropped = false
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        // ...then the 820+823 auth handshake for the second connect.
        conn.queueDefaultAuthOk()
        s.disconnect()
        s.connect()
        assertNull(s.lastError)
        s.disconnect()
    }

    @Test
    fun sendBeforeConnectAlsoRecordsLastError() = runTest {
        val conn = FakeConnection()
        val s = MountSession({ conn }, readerScope = this)
        s.send(500, "dir:1;speed:1;")
        val err = assertIs<MountSession.CmdResult.ProtocolError>(s.lastError)
        assertEquals("not connected", err.message)
    }
}
