package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression tests for the step-tagged failure surface added in commit
 * 71a1f08.
 *
 * Why this file exists: before 71a1f08, every [MountSession.connect]
 * failure collapsed into a single opaque "Could not reach $host" line,
 * forcing the user to dig through `/tmp/openpolaris-desktop.log` to find
 * out *which* step failed (284 lifecycle, 820 auth probe, 821 token,
 * 823 hello, password mismatch, etc.). The fix in [MountSession.tryConnect]
 * tags each `IOException` with a step-specific reason and stores it in
 * `MountState.lastErrorMessage`, which `AppViewModel.connect()` then
 * appends to the user-visible status string.
 *
 * These tests pin that contract: each of the six known throw sites
 * must surface a recognisable, user-disclosing reason in
 * `state.value.lastErrorMessage`. The set of substrings is deliberately
 * kept short and literal so a future refactor that strips the tag
 * (e.g. a refactor that swallows the IOException into a single string)
 * is caught by the test suite instead of by a confused user in
 * the field.
 *
 * Virtual time: the failure paths for 820/821/823 all involve a
 * `withTimeout(10000)`-ish wait, which would take 10s+ of wall time
 * per test if it weren't for `runTest`. With virtual time the entire
 * suite finishes in milliseconds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MountSessionConnectFailureTest {

    /**
     * In-memory [Connection]. Empty response queue returns -1 so the
     * reader parks on its retry-delay loop. The `failConnect` knob
     * lets a test make the underlying [Connection.connect] throw to
     * exercise the catch-all path inside `tryConnect()`.
     */
    private class FakeConnection : Connection {
        val written = mutableListOf<ByteArray>()
        val responses = mutableListOf<ByteArray>()
        var failConnect = false
        var failMessage: String? = null

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) {
            if (failConnect) {
                throw java.io.IOException(failMessage ?: "connection refused")
            }
        }

        override suspend fun write(data: ByteArray) {
            written += data
        }

        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            if (responses.isEmpty()) return -1
            val r = responses.removeAt(0)
            r.copyInto(buffer)
            return r.size
        }

        override fun close() {}
    }

    // ---------------------------------------------------------------
    // 1. connectionFactory() itself throws
    //
    // The outer catch in tryConnect() tags this as "factory: $reason"
    // so the user can tell the wiring is broken (e.g. wrong host,
    // permission denied opening a socket) versus the gimbal being
    // unreachable.
    // ---------------------------------------------------------------
    @Test
    fun testFactoryThrowIsTaggedAsFactory() = runTest {
        val session = MountSession(
            connectionFactory = { throw java.io.IOException("Network is unreachable") },
            readerScope = backgroundScope,
        )
        val ok = session.connect()
        assertFalse(ok, "factory throw must surface as connect() == false")
        val reason = session.state.value.lastErrorMessage
        assertNotNull(reason, "factory throw must populate lastErrorMessage")
        assertTrue(
            reason!!.startsWith("factory:"),
            "factory-tagged reason should start with 'factory:'; got: $reason",
        )
        assertTrue(
            reason.contains("Network is unreachable"),
            "factory reason should preserve the underlying IOException message; got: $reason",
        )
        session.disconnect()
    }

    // ---------------------------------------------------------------
    // 2. 284 lifecycle handshake times out
    //
    // tryConnect() sends a 284 PUSH_MODE_STATE and waits up to 2s for
    // a reply. If the gimbal is in deep sleep or has crashed, the
    // reply never arrives and the request returns CmdResult.Timeout.
    // MountSession.kt re-throws that as `IOException("handshake failed:
    // ...")`. The tag switch maps that prefix to a user hint about
    // waking the gimbal.
    // ---------------------------------------------------------------
    @Test
    fun testHandshakeTimeoutIsTagged() = runTest {
        val conn = FakeConnection()
        // Intentionally do NOT queue a 284 reply -> request<284> will
        // time out after 2s (virtual time, so the test is fast).
        val session = MountSession(
            connectionFactory = { conn },
            readerScope = backgroundScope,
        )
        val ok = session.connect()
        assertFalse(ok, "284 timeout must surface as connect() == false")
        val reason = session.state.value.lastErrorMessage
        assertNotNull(reason, "handshake timeout must populate lastErrorMessage")
        assertTrue(
            reason!!.contains("284", ignoreCase = true) ||
                reason.contains("handshake", ignoreCase = true),
            "handshake-tagged reason should mention 284 or 'handshake'; got: $reason",
        )
        session.disconnect()
    }

    // ---------------------------------------------------------------
    // 3. 820 auth probe times out
    //
    // The lifecycle 284 succeeds but the gimbal never answers the
    // 820 auth probe. The user sees a "820" tag so they know the
    // lifecycle is fine but the app-handshake is wedged.
    // ---------------------------------------------------------------
    @Test
    fun testAuthProbeTimeoutIsTagged() = runTest {
        val conn = FakeConnection()
        // 284 lifecycle handshake succeeds.
        conn.responses += "1&${Codes.PUSH_MODE_STATE}&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        // No 820 reply queued -> request<820> will time out.

        val session = MountSession(
            connectionFactory = { conn },
            readerScope = backgroundScope,
        )
        val ok = session.connect()
        assertFalse(ok, "820 timeout must surface as connect() == false")
        val reason = session.state.value.lastErrorMessage
        assertNotNull(reason, "820 timeout must populate lastErrorMessage")
        assertTrue(
            reason!!.contains("820"),
            "820-tagged reason should mention '820'; got: $reason",
        )
        session.disconnect()
    }

    // ---------------------------------------------------------------
    // 4. 823 hello is fire-and-forget on real firmware
    //
    // Earlier code treated 823 as request/reply and surfaced a
    // "823" tag when the gimbal stayed silent. Live captures against
    // current production firmware (sw 6.x) show that 823 is a
    // one-way notification from the app — the gimbal does NOT
    // reply. We must therefore NOT fail connect() just because no
    // 823 frame came back. The test below pins the new contract:
    // 284 + 820 succeed, 823 is sent, no reply comes back, and
    // connect() returns true.
    // ---------------------------------------------------------------
    @Test
    fun testAppHelloFireAndForgetSucceeds() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&${Codes.PUSH_MODE_STATE}&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&${Codes.APP_PASSWORD_INFO}&2&needed:0;#".toByteArray(Charsets.US_ASCII)
        // No 823 reply queued — gimbal stays silent on 823.

        val session = MountSession(
            connectionFactory = { conn },
            readerScope = backgroundScope,
        )
        val ok = session.connect()
        assertTrue(ok, "823 silence must NOT fail connect() on real firmware")
        // And the 823 frame must still have been written to the wire.
        val wroteHello = conn.written.any {
            val s = String(it, Charsets.US_ASCII)
            s.startsWith("1&${Codes.APP_HELLO}&2&")
        }
        assertTrue(wroteHello, "823 hello must be sent (fire-and-forget)")
        session.disconnect()
    }

    // ---------------------------------------------------------------
    // 5. 820 reports needed:1 but no password was configured
    //
    // AuthConfig() defaults to password = null. If the gimbal reports
    // needed:1, authenticate() throws an IOException whose message
    // starts with "gimbal requires connection password". The tag
    // switch surfaces "gimbal requires a connection password (none
    // configured)" so the user can set one in the app.
    // ---------------------------------------------------------------
    @Test
    fun testPasswordRequiredButNotConfiguredIsTagged() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&${Codes.PUSH_MODE_STATE}&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&${Codes.APP_PASSWORD_INFO}&2&needed:1;#".toByteArray(Charsets.US_ASCII)
        // No 821, no 823 (authenticate() will throw before sending them).

        // Default AuthConfig() has password = null.
        val session = MountSession(
            connectionFactory = { conn },
            readerScope = backgroundScope,
        )
        val ok = session.connect()
        assertFalse(ok, "needed:1 with no password must surface as connect() == false")
        val reason = session.state.value.lastErrorMessage
        assertNotNull(reason, "needed:1/no-password must populate lastErrorMessage")
        assertTrue(
            reason!!.contains("password", ignoreCase = true),
            "password-required reason should mention 'password'; got: $reason",
        )
        session.disconnect()
    }

    // ---------------------------------------------------------------
    // 6. 821 reply carries ret=1 (gimbal rejected the password)
    //
    // User supplied a password, the gimbal accepted the 820 probe
    // (needed:1) and answered 821, but the 821 reply's ret field was
    // 1 instead of 0. The tag switch surfaces "821 reported ret!=0
    // -- wrong password" so the user knows to re-enter.
    // ---------------------------------------------------------------
    @Test
    fun testPasswordRejectedIsTagged() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&${Codes.PUSH_MODE_STATE}&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&${Codes.APP_PASSWORD_INFO}&2&needed:1;#".toByteArray(Charsets.US_ASCII)
        // 821 reply with ret=1 (rejection).
        conn.responses += "1&${Codes.APP_TOKEN}&2&ret:1;#".toByteArray(Charsets.US_ASCII)

        val session = MountSession(
            connectionFactory = { conn },
            readerScope = backgroundScope,
            auth = AuthConfig(
                appName = "openpolaris",
                appVersion = "0.1.0",
                password = "wrongpw",
            ),
        )
        val ok = session.connect()
        assertFalse(ok, "821 ret=1 must surface as connect() == false")
        val reason = session.state.value.lastErrorMessage
        assertNotNull(reason, "password-rejection must populate lastErrorMessage")
        assertTrue(
            reason!!.contains("821") || reason.contains("password", ignoreCase = true),
            "password-rejection reason should mention '821' or 'password'; got: $reason",
        )
        session.disconnect()
    }

    // ---------------------------------------------------------------
    // 7. Connection.connect() throws (succeeding factory, broken link)
    //
    // connectionFactory() returns a Connection, but Connection.connect()
    // itself throws (e.g. ENETUNREACH, ECONNREFUSED). The inner catch
    // in tryConnect() handles this; the reason is the raw IOException
    // message (no "factory:" prefix, since the factory DID succeed).
    // This pins the boundary between "factory broken" (outer catch,
    // "factory:" tag) and "factory OK but link broken" (inner catch,
    // raw message).
    // ---------------------------------------------------------------
    @Test
    fun testLinkRefusedIsTaggedWithoutFactoryPrefix() = runTest {
        val conn = FakeConnection()
        conn.failConnect = true
        conn.failMessage = "Connection refused"

        val session = MountSession(
            connectionFactory = { conn },
            readerScope = backgroundScope,
        )
        val ok = session.connect()
        assertFalse(ok, "Connection.connect throw must surface as connect() == false")
        val reason = session.state.value.lastErrorMessage
        assertNotNull(reason, "Connection.connect throw must populate lastErrorMessage")
        assertEquals(
            "Connection refused",
            reason,
            "Connection.connect() throw should surface the raw IOException message " +
                "(no 'factory:' prefix, since the factory itself succeeded); got: $reason",
        )
        session.disconnect()
    }

    // ---------------------------------------------------------------
    // 8. Successful connect clears lastErrorMessage
    //
    // The contract from 71a1f08: a successful connect must null
    // lastErrorMessage (line 279 of MountSession.kt). This test pins
    // that, because a regression that left a stale error message in
    // place would make the diagnostic surface useless on retry.
    // ---------------------------------------------------------------
    @Test
    fun testSuccessfulConnectClearsLastErrorMessage() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&${Codes.PUSH_MODE_STATE}&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&${Codes.APP_PASSWORD_INFO}&2&needed:0;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&${Codes.APP_HELLO}&2&app:openpolaris;ver:0.1.0;#".toByteArray(Charsets.US_ASCII)

        val session = MountSession(
            connectionFactory = { conn },
            readerScope = backgroundScope,
        )
        val ok = session.connect()
        assertTrue(ok, "fully-handshaken connect should succeed")
        val state = session.state.value
        assertEquals(null, state.lastErrorMessage,
            "successful connect must clear lastErrorMessage; got: ${state.lastErrorMessage}")
        assertEquals(true, state.connected,
            "successful connect must set connected=true; got: ${state.connected}")
        session.disconnect()
    }
}
