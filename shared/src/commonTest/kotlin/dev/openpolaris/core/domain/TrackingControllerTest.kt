@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.openpolaris.core.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * In-memory Connection that records writes and serves scripted responses
 * deterministically.
 *
 * To make the demultiplexed reader/test handshake deterministic, `read()`
 * only returns a queued response when the request for that response's
 * frame code has been **written**. Pre-queued responses are held back
 * until the matching request lands, eliminating the race where the
 * reader would otherwise hot-loop and drain pre-queued frames before
 * the test body has registered the per-frame waiter.
 *
 * Two ways to stage responses:
 *  - Append raw bytes to [responses]. `read()` will skip any whose code
 *    has not been written yet (request-keyed gating).
 *  - Call [scriptResponse] for the same effect with explicit code and
 *    frame text.
 *  - Call [push] to enqueue a frame onto the internal channel; the
 *    reader will receive it on its next `read()` call.
 *
 * Either way, the 284 lifecycle handshake is auto-acked the first time
 * a 284 request is written, matching the real mount's behaviour.
 */
class FakeConnection : Connection {
    val written = mutableListOf<ByteArray>()

    /**
     * Responses ready to be served in FIFO order. The reader will only
     * see a response once the request for its code has been written.
     * Tests can either append raw bytes here, or use [scriptResponse].
     *
     * **Write-keyed gating**: a response is served only when at least one
     * *unmatched* write for its code has been issued. This prevents the
     * reader from hot-looping and consuming the 2nd, 3rd, ... pre-queued
     * response for the same code before the test coroutine has issued
     * the matching subsequent request.
     */
    val responses = mutableListOf<ByteArray>()

    private val channel = kotlinx.coroutines.channels.Channel<ByteArray>(
        capacity = kotlinx.coroutines.channels.Channel.UNLIMITED
    )

    var failConnect = false
    var closed = false
        private set

    /** True after we've served the canned 284 lifecycle handshake ack. */
    private var handshakeAcked = false

    /** How many writes have been issued per frame code (request count). */
    private val writeCountByCode = mutableMapOf<Int, Int>()

    /** How many responses have been served per frame code. */
    private val servedCountByCode = mutableMapOf<Int, Int>()

    /** Queue a frame for the next [read] call. */
    fun push(frame: ByteArray) {
        channel.trySend(frame)
    }

    override suspend fun connect(host: String, port: Int, timeoutMs: Int) {
        if (failConnect) throw java.io.IOException("refused")
    }

    override suspend fun write(data: ByteArray) {
        written += data
        // Count writes per code so we can match responses one-to-one.
        extractFrameCode(data)?.let { c ->
            writeCountByCode[c] = (writeCountByCode[c] ?: 0) + 1
        }
    }

    override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
        // Prefer a channel-pushed frame if any. This lets tests drive the
        // reader directly with `push(...)` for ad-hoc sequences.
        val pending = channel.tryReceive()
        if (pending.isSuccess) {
            val frame = pending.getOrThrow()
            frame.copyInto(buffer)
            return frame.size
        }
        // Walk the queue, return the first response whose code has at
        // least one *unmatched* write. If none are ready, return -1 so
        // the reader retries (instead of hot-looping and dropping
        // frames into the void).
        for (i in responses.indices) {
            val r = responses[i]
            val code = extractFrameCode(r)
            if (code != null && hasUnmatchedWrite(code)) {
                responses.removeAt(i)
                servedCountByCode[code] = (servedCountByCode[code] ?: 0) + 1
                r.copyInto(buffer)
                return r.size
            }
        }
        // No scripted response is ready. Auto-respond to the 284 handshake
        // (PROTOCOL.md §4) the first time it is requested, matching the
        // "mode:0" ack the real mount returns immediately after power-on.
        if (!handshakeAcked && hasUnmatchedWrite(284)) {
            handshakeAcked = true
            servedCountByCode[284] = (servedCountByCode[284] ?: 0) + 1
            val ack = "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
            ack.copyInto(buffer)
            return ack.size
        }
        // Likewise, auto-ack the connect-time 820 (auth probe) and 823
        // (hello) frames the first time they are requested, with the
        // `needed:0` response — matching the common production firmware
        // behaviour where no connection password is required. Tests that
        // need a different auth outcome (e.g. `needed:1` or a 823 that
        // returns an error) should call [scriptResponse] explicitly.
        if (hasUnmatchedWrite(820)) {
            servedCountByCode[820] = (servedCountByCode[820] ?: 0) + 1
            val ack = "1&820&2&needed:0;#".toByteArray(Charsets.US_ASCII)
            ack.copyInto(buffer)
            return ack.size
        }
        if (hasUnmatchedWrite(823)) {
            servedCountByCode[823] = (servedCountByCode[823] ?: 0) + 1
            val ack = "1&823&2&app:openpolaris;ver:0.1.0;#".toByteArray(Charsets.US_ASCII)
            ack.copyInto(buffer)
            return ack.size
        }
        return -1
    }

    /** True if a write for [code] exists that has not yet been matched by a served response. */
    private fun hasUnmatchedWrite(code: Int): Boolean {
        val writes = writeCountByCode[code] ?: return false
        val served = servedCountByCode[code] ?: 0
        return writes > served
    }

    /**
     * Extract the frame code from a payload (e.g.
     * "1&537&2&pitch:...;#" -> 537). Returns null if the payload
     * doesn't look like a frame.
     */
    private fun extractFrameCode(bytes: ByteArray): Int? {
        if (bytes.size < 4) return null
        if (bytes[0] != '1'.code.toByte() || bytes[1] != '&'.code.toByte()) return null
        var i = 2
        var num = 0
        var sawDigit = false
        while (i < bytes.size && bytes[i] != '&'.code.toByte()) {
            val c = bytes[i]
            if (c < '0'.code.toByte() || c > '9'.code.toByte()) return null
            num = num * 10 + (c - '0'.code.toByte())
            sawDigit = true
            i++
        }
        return if (sawDigit && i < bytes.size && bytes[i] == '&'.code.toByte()) num else null
    }

    override fun close() {
        handshakeAcked = false
        responses.clear()
        writeCountByCode.clear()
        servedCountByCode.clear()
        channel.close()
    }

    /**
     * Script a response that will be served only after a request for
     * [code] has been written. Use this in tests that drive multiple
     * requests on the same connect — the response is held back until
     * the matching request lands, eliminating reader/test race.
     */
    fun scriptResponse(code: Int, frame: String) {
        responses += frame.toByteArray(Charsets.US_ASCII)
    }

    /** Test helper: reset between test cases that share an instance. */
    fun reset() {
        written.clear()
        responses.clear()
        failConnect = false
        handshakeAcked = false
        writeCountByCode.clear()
        servedCountByCode.clear()
    }
}

class TrackingControllerTest {

    private fun newSession(
        conn: FakeConnection,
        scope: CoroutineScope,
    ): Pair<MountSession, TrackingController> {
        val s = MountSession({ conn }, readerScope = scope)
        return s to TrackingController(s)
    }

    @Test
    fun startSendsTrackOn() = runTest {
        val conn = FakeConnection()
        val (s, t) = newSession(conn, backgroundScope)
        s.connect()
        t.start()
        assertEquals("1&531&2&state:1;#", String(conn.written[3], Charsets.US_ASCII))
        s.disconnect()
    }

    @Test
    fun startWithSpeedIncludesSpeedField() = runTest {
        val conn = FakeConnection()
        val (s, t) = newSession(conn, backgroundScope)
        s.connect()
        t.start(speed = 2)
        assertEquals("1&531&2&state:1;speed:2;#", String(conn.written[3], Charsets.US_ASCII))
        s.disconnect()
    }

    @Test
    fun stopSendsTrackOff() = runTest {
        val conn = FakeConnection()
        val (s, t) = newSession(conn, backgroundScope)
        s.connect()
        t.stop()
        assertEquals("1&531&2&state:0;#", String(conn.written[3], Charsets.US_ASCII))
        s.disconnect()
    }

    @Test
    fun halfSpeedIsInvertedOnWire() = runTest {
        val conn = FakeConnection()
        val (s, t) = newSession(conn, backgroundScope)
        s.connect()
        t.setHalfSpeed(true)
        t.setHalfSpeed(false)
        assertEquals("1&536&2&halfSpeed:0;#", String(conn.written[3], Charsets.US_ASCII))
        assertEquals("1&536&2&halfSpeed:1;#", String(conn.written[4], Charsets.US_ASCII))
        s.disconnect()
    }

    @Test
    fun gotoFormatsAzAlt() = runTest {
        val conn = FakeConnection()
        val (s, t) = newSession(conn, backgroundScope)
        s.connect()
        t.gotoAzAlt(180.0, 45.0)
        assertEquals("1&519&2&az:180.0000;alt:45.0000;#", String(conn.written[3], Charsets.US_ASCII))
        s.disconnect()
    }

    @Test
    fun requestReturnsParsedValue() = runTest {
        val conn = FakeConnection()
        val (s, _) = newSession(conn, backgroundScope)
        s.connect()
        val deferred = async {
            s.request(284) { MountState.fromFrame284(it) }
        }
        // runCurrent (not advanceUntilIdle) drains tasks already
        // scheduled without advancing virtual time past the
        // request's withTimeout timer. The reader starts, parks on
        // receive; the async body runs, registers its waiter.
        runCurrent()
        conn.push("1&284&2&mode:2;battery:50;#".toByteArray(Charsets.US_ASCII))
        // Let the reader consume the frame and dispatch it to the
        // waiter without advancing past the 2000ms timeout.
        runCurrent()
        val result = deferred.await()
        val st = result as MountSession.CmdResult.Ok
        assertEquals(MountMode.ASTRO, st.value.mode)
        assertEquals(50, st.value.batteryPercent)
        s.disconnect()
    }
}
