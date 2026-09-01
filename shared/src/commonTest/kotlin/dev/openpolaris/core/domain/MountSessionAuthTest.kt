package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
