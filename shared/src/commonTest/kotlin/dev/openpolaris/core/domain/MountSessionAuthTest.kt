package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Auth-handshake tests (issue: 526 SP_TEST evidence; PROTOCOL.md section 3).
 *
 * Background: starting with gimbal firmware sw:6.0.0.54, the gimbal
 * requires an app-handshake after the 284 PUSH_MODE_STATE lifecycle
 * handshake. Without it, every other opcode (810, 519, ...) returns
 * ret:-1 and the gimbal is effectively unusable. The handshake is:
 *
 *   1. 820 probe - gimbal reports needed:0; (no password) or
 *      needed:1; (password required).
 *   2. If needed:1 AND the caller supplied a password, send 821
 *      with token:<password>;. The reply carries ret:0; on accept
 *      or ret:1; on reject.
 *   3. Always send 823 hello with app:<appName>;ver:<appVersion>;.
 *
 * The acceptance for MountSession.connect is that the auth path runs
 * end-to-end and returns true only when the gimbal accepts our
 * credentials. The tests below pin that contract.
 *
 * The tests live in commonTest (not jvmTest) so they run on every
 * KMP target - the bug class is a wire-protocol behaviour, not a
 * platform quirk. The reader scope uses runTest's backgroundScope
 * for virtual time, matching the pattern in MountSessionReaderTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MountSessionAuthTest {

    /** In-memory Connection that records writes and serves queued
     *  responses in FIFO order. Empty queue returns -1 so the reader
     *  parks on its delay(READ_RETRY_MS) retry loop. */
    private class FakeConnection : Connection {
        val written = mutableListOf<ByteArray>()
        val responses = mutableListOf<ByteArray>()

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) {}

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

    /** Pre-load the 284 lifecycle handshake reply so MountSession.connect
     *  gets past the PUSH_MODE_STATE poll. */
    private fun FakeConnection.queueLifecycleHandshake() {
        responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
    }

    @Test
    fun passwordlessSuccess() = runTest {
        val conn = FakeConnection()
        conn.queueLifecycleHandshake()
        // Gimbal says "no password required" and accepts our hello.
        conn.responses += "1&820&2&needed:0;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&823&2&app:openpolaris;ver:0.1.0;#".toByteArray(Charsets.US_ASCII)

        val session = MountSession({ conn }, readerScope = backgroundScope)
        val ok = session.connect()

        assertTrue(ok, "passwordless connect should succeed when 820+823 reply")
        // No 821 token should have been written: the gimbal didn't
        // demand one and the skip-branch is the contract.
        val codes = conn.written.map { String(it, Charsets.US_ASCII) }
            .map { it.split("&").getOrNull(1)?.toIntOrNull() }
        assertTrue(
            Codes.APP_TOKEN !in codes,
            "should not have written 821 on needed:0 path; got $codes",
        )
        // Both 820 and 823 must be present.
        assertTrue(Codes.APP_PASSWORD_INFO in codes, "missing 820 probe; got $codes")
        assertTrue(Codes.APP_HELLO in codes, "missing 823 hello; got $codes")

        session.disconnect()
    }

    /** Real gimbal firmware does NOT reply to 823 — it's a fire-and-forget
     *  notification from the app. Connect must succeed as long as 820
     *  acks (even with `ret:-1`) — the 823 send must NOT block waiting
     *  for a matching reply, or `connect` would hang for the full 10s
     *  823 timeout on every link. */
    @Test
    fun helloIsFireAndForgetOnRealFirmware() = runTest {
        val conn = FakeConnection()
        conn.queueLifecycleHandshake()
        // 820 acks with ret:-1 (some production firmware does this
        // before the app-handshake; the gimbal still becomes usable
        // once 823 has been sent). 823 is NOT replied to.
        conn.responses += "1&820&2&ret:-1;#".toByteArray(Charsets.US_ASCII)
        // No 823 reply enqueued — the gimbal stays silent on 823.

        val session = MountSession({ conn }, readerScope = backgroundScope)
        val ok = session.connect()

        assertTrue(
            ok,
            "connect should succeed even when gimbal does not reply to 823",
        )
        val codes = conn.written.map { String(it, Charsets.US_ASCII) }
            .map { it.split("&").getOrNull(1)?.toIntOrNull() }
        assertTrue(
            Codes.APP_PASSWORD_INFO in codes,
            "820 probe must be written; got $codes",
        )
        assertTrue(
            Codes.APP_HELLO in codes,
            "823 hello must be written (fire-and-forget); got $codes",
        )

        session.disconnect()
    }

    @Test
    fun passwordedSuccess() = runTest {
        val conn = FakeConnection()
        conn.queueLifecycleHandshake()
        // Gimbal demands a password, accepts it, and acks the hello.
        conn.responses += "1&820&2&needed:1;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&821&2&token:sekrit;ret:0;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&823&2&app:openpolaris;ver:0.1.0;#".toByteArray(Charsets.US_ASCII)

        val session = MountSession(
            connectionFactory = { conn },
            readerScope = backgroundScope,
            auth = AuthConfig(password = "sekrit"),
        )
        val ok = session.connect()

        assertTrue(ok, "passworded connect should succeed when 820+821+823 all reply")
        // Confirm the 821 write actually carried the password in the
        // wire payload - silent omission was the original 526-era bug.
        val tokenWrite = conn.written
            .map { String(it, Charsets.US_ASCII) }
            .first { it.startsWith("1&${Codes.APP_TOKEN}&2&") }
        assertTrue(
            "token:sekrit;" in tokenWrite,
            "821 write should carry the configured password; got $tokenWrite",
        )

        session.disconnect()
    }

    @Test
    fun wrongPasswordRejected() = runTest {
        val conn = FakeConnection()
        conn.queueLifecycleHandshake()
        // Gimbal demands a password, REJECTS the one we sent, then we
        // never get a 823 - the connect path must surface a failure.
        conn.responses += "1&820&2&needed:1;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&821&2&token:wrong;ret:1;#".toByteArray(Charsets.US_ASCII)

        val session = MountSession(
            connectionFactory = { conn },
            readerScope = backgroundScope,
            auth = AuthConfig(password = "wrong"),
        )
        val ok = session.connect()

        assertFalse(ok, "connect() must return false when gimbal rejects password")
        // The session should NOT be marked connected after a failed auth.
        assertFalse(
            session.state.value.connected,
            "state.connected must be false after a rejected password",
        )
    }

    @Test
    fun authTimesOutWhenGimbalSilent() = runTest {
        val conn = FakeConnection()
        conn.queueLifecycleHandshake()
        // No 820 reply queued - the 10000ms auth probe timeout must
        // fire and connect() must return false rather than hang.
        val session = MountSession({ conn }, readerScope = backgroundScope)

        val ok = session.connect()

        assertFalse(ok, "connect() must return false when 820 probe times out")
        assertFalse(
            session.state.value.connected,
            "state.connected must be false after an auth timeout",
        )
    }

    @Test
    fun gimbalRefusesMissingPassword() = runTest {
        val conn = FakeConnection()
        conn.queueLifecycleHandshake()
        // Gimbal says "password required" but the caller did NOT
        // configure one. The session must abort the handshake rather
        // than papering over the missing credential.
        conn.responses += "1&820&2&needed:1;#".toByteArray(Charsets.US_ASCII)

        val session = MountSession(
            connectionFactory = { conn },
            readerScope = backgroundScope,
            auth = AuthConfig(password = null),
        )
        val ok = session.connect()

        assertFalse(ok, "connect() must return false when password is required but not set")
        // No 821 should have been written - we have nothing to send.
        val codes = conn.written.map { String(it, Charsets.US_ASCII) }
            .map { it.split("&").getOrNull(1)?.toIntOrNull() }
        assertFalse(
            Codes.APP_TOKEN in codes,
            "should not write 821 when no password is configured; got $codes",
        )
    }

    @Test
    fun helloWriteCarriesConfiguredAppNameAndVersion() = runTest {
        // Belt-and-braces: the 823 payload must contain the configured
        // appName and appVersion, not the gimbal's own (that's what
        // the response carries, not the request). The original
        // implementation sent the wrong appName when callers passed a
        // non-default AuthConfig - this pins the fix.
        val conn = FakeConnection()
        conn.queueLifecycleHandshake()
        conn.responses += "1&820&2&needed:0;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&823&2&app:openpolaris;ver:9.9.9-test;#".toByteArray(Charsets.US_ASCII)

        val session = MountSession(
            connectionFactory = { conn },
            readerScope = backgroundScope,
            auth = AuthConfig(
                appName = "openpolaris",
                appVersion = "9.9.9-test",
            ),
        )
        assertTrue(session.connect())

        val helloWrite = conn.written
            .map { String(it, Charsets.US_ASCII) }
            .first { it.startsWith("1&${Codes.APP_HELLO}&2&") }
        // The request payload is between 2& and the trailing #.
        val payload = helloWrite.substringAfter("2&").substringBeforeLast("#")
        assertEquals(
            "app:openpolaris;ver:9.9.9-test;",
            payload,
            "823 write should carry the configured appName/appVersion",
        )
        session.disconnect()
    }

    // -----------------------------------------------------------------
    // Post-auth 526 (SP_TEST) — see
    // docs/evidence/sp-test-526-investigation-2026-09-01/RESULTS.md
    //
    // Live observation: once the 820/821/823 handshake is complete, the
    // gimbal will (eventually) process 526 sends. The `step:` value
    // determines the reply:
    //   - step:6  -> `526@step:6;ret:0;`  (success, ~23ms turnaround)
    //   - step:1  -> `526@step:1;ret:-1;` (unsupported, ~22s delayed)
    //
    // Before the auth handshake was implemented, *every* 526 step
    // returned nothing (or ret:-1 after a 20s+ delay) because the
    // gimbal was still in the unauthenticated state. The tests below
    // pin the post-auth contract so the regression never returns.
    // -----------------------------------------------------------------

    /** Run a connect that succeeds (passwordless) and returns the
     *  ready-to-issue-requests session. [scope] must be the `TestScope`
     *  of the calling `runTest` (or any scope that's alive for the
     *  duration of the test) - the MountSession reader-loop needs a
     *  live scope. */
    private suspend fun connectedSession(
        conn: FakeConnection,
        scope: kotlinx.coroutines.CoroutineScope,
    ): MountSession {
        val session = MountSession({ conn }, readerScope = scope)
        assertTrue(session.connect(), "test fixture: passwordless connect should succeed")
        return session
    }

    @Test
    fun testStep6ReturnsRet0AfterAuth() = runTest {
        val conn = FakeConnection()
        conn.queueLifecycleHandshake()
        // 820 + 823 handshake replies (needed:0, app:openpolaris;ver:..)
        conn.responses += "1&820&2&needed:0;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&823&2&app:openpolaris;ver:0.1.0;#".toByteArray(Charsets.US_ASCII)
        // The 526 step:6 reply (post-auth, ret:0 - the "first successful
        // 526 reply" from run 9/run 11 of the live investigation).
        conn.responses += "1&526&2&step:6;ret:0;#".toByteArray(Charsets.US_ASCII)

        val session = connectedSession(conn, backgroundScope)
        val result = session.request(Codes.SP_TEST, "step:6;") { f ->
            StepReply(step = f["step"], ret = f["ret"]?.toIntOrNull())
        }

        val parsed = assertIs<MountSession.CmdResult.Ok<StepReply>>(result).value
        assertEquals(6, parsed.step?.toIntOrNull(), "reply should echo step:6")
        assertEquals(0, parsed.ret, "post-auth step:6 should return ret:0")

        // And the write that hit the wire should carry `step:6;` in
        // its payload - silent omission of the step parameter would
        // be a different bug.
        val testWrite = conn.written
            .map { String(it, Charsets.US_ASCII) }
            .first { it.startsWith("1&${Codes.SP_TEST}&2&") }
        assertEquals(
            "1&${Codes.SP_TEST}&2&step:6;#",
            testWrite,
            "526 write should be `1&526&2&step:6;#` (subtype 2, payload step:6;)",
        )

        session.disconnect()
    }

    @Test
    fun testStep1ReturnsRetMinus1AfterAuth() = runTest {
        // Mirror of the run 13 finding: post-auth, step:1 returns
        // ret:-1. The reply is delayed (~22s on the live gimbal),
        // but that's a gimbal-side property, not a MountSession one
        // - the JVM test only pins that the protocol code path
        // accepts the reply and surfaces ret:-1 correctly.
        val conn = FakeConnection()
        conn.queueLifecycleHandshake()
        conn.responses += "1&820&2&needed:0;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&823&2&app:openpolaris;ver:0.1.0;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&526&2&step:1;ret:-1;#".toByteArray(Charsets.US_ASCII)

        val session = connectedSession(conn, backgroundScope)
        val result = session.request(Codes.SP_TEST, "step:1;") { f ->
            StepReply(step = f["step"], ret = f["ret"]?.toIntOrNull())
        }

        val parsed = assertIs<MountSession.CmdResult.Ok<StepReply>>(result).value
        assertEquals(1, parsed.step?.toIntOrNull())
        assertEquals(-1, parsed.ret, "post-auth step:1 should return ret:-1 (unsupported)")

        session.disconnect()
    }

    @Test
    fun testStepTimesOutWhenGimbalSilent() = runTest {
        // Post-auth, no 526 reply. request<T> must surface CmdResult.Timeout
        // (rather than hang) when the gimbal never acks. The 2000ms
        // request<T> default is fine; the test uses 500ms so it runs
        // quickly under virtual time.
        val conn = FakeConnection()
        conn.queueLifecycleHandshake()
        conn.responses += "1&820&2&needed:0;#".toByteArray(Charsets.US_ASCII)
        conn.responses += "1&823&2&app:openpolaris;ver:0.1.0;#".toByteArray(Charsets.US_ASCII)

        val session = connectedSession(conn, backgroundScope)
        val result = session.request(Codes.SP_TEST, "step:6;", timeoutMs = 500) { f ->
            StepReply(step = f["step"], ret = f["ret"]?.toIntOrNull())
        }

        assertEquals(MountSession.CmdResult.Timeout, result,
            "526 must time out (not hang) when the gimbal never replies")
        session.disconnect()
    }

    private data class StepReply(val step: String?, val ret: Int?)
}
