package dev.openpolaris.core.domain

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

    /**
     * Unsolicited push frames (e.g. 538 SET_TILT_STATE) that have no
     * matching request. The reader sees these ahead of the write-gated
     * [responses] queue, in FIFO order. Use [push] to enqueue.
     */
    private val pushQueue = ArrayDeque<ByteArray>()

    var failConnect = false

    /** True after we've served the canned 284 lifecycle handshake ack. */
    private var handshakeAcked = false

    /** How many writes have been issued per frame code (request count). */
    private val writeCountByCode = mutableMapOf<Int, Int>()

    /** How many responses have been served per frame code. */
    private val servedCountByCode = mutableMapOf<Int, Int>()

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
        // Unsolicited push frames are served first, bypassing the
        // write-keyed gate. The mount pushes 538 (SET_TILT_STATE) and
        // similar codes without any prior request from the app, so the
        // gate would otherwise hold them forever.
        if (pushQueue.isNotEmpty()) {
            val r = pushQueue.removeFirst()
            r.copyInto(buffer)
            return r.size
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
        return -1
    }

    /** True if a write for [code] exists that has not yet been matched by a served response. */
    private fun hasUnmatchedWrite(code: Int): Boolean {
        val writes = writeCountByCode[code] ?: return false
        val served = servedCountByCode[code] ?: 0
        return writes > served
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        outer@ for (i in 0..(haystack.size - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
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

    /**
     * Inject an unsolicited push frame into the reader's byte stream,
     * **bypassing** the write-keyed gate. Use for frames the mount
     * sends without a prior request (e.g. 538 SET_TILT_STATE).
     *
     * Pushes are served FIFO ahead of the write-gated [responses] queue.
     * For solicited responses (anything that should arrive as the
     * reply to a previously-written request code), prefer
     * [scriptResponse] or appending to [responses] directly so the
     * write-keyed gate still applies.
     */
    fun push(frame: ByteArray) {
        pushQueue.addLast(frame)
    }

    /** Convenience overload for [push] that takes a US-ASCII string. */
    fun push(frame: String) {
        pushQueue.addLast(frame.toByteArray(Charsets.US_ASCII))
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

    private fun newSession(conn: FakeConnection, scope: kotlinx.coroutines.CoroutineScope): Pair<MountSession, TrackingController> {
        val s = MountSession({ conn }, readerScope = scope)
        return s to TrackingController(s)
    }

    @Test
    fun startSendsTrackOn() = runTest {
        val conn = FakeConnection()
        val (s, t) = newSession(conn, this)
        s.connect()
        t.start()
        assertEquals("1&531&2&state:1;#", String(conn.written[1], Charsets.US_ASCII))
        s.disconnect()
    }

    @Test
    fun startWithSpeedIncludesSpeedField() = runTest {
        val conn = FakeConnection()
        val (s, t) = newSession(conn, this)
        s.connect()
        t.start(speed = 2)
        assertEquals("1&531&2&state:1;speed:2;#", String(conn.written[1], Charsets.US_ASCII))
        s.disconnect()
    }

    @Test
    fun stopSendsTrackOff() = runTest {
        val conn = FakeConnection()
        val (s, t) = newSession(conn, this)
        s.connect()
        t.stop()
        assertEquals("1&531&2&state:0;#", String(conn.written[1], Charsets.US_ASCII))
        s.disconnect()
    }

    @Test
    fun halfSpeedIsInvertedOnWire() = runTest {
        val conn = FakeConnection()
        val (s, t) = newSession(conn, this)
        s.connect()
        t.setHalfSpeed(true)
        t.setHalfSpeed(false)
        assertEquals("1&536&2&halfSpeed:0;#", String(conn.written[1], Charsets.US_ASCII))
        assertEquals("1&536&2&halfSpeed:1;#", String(conn.written[2], Charsets.US_ASCII))
        s.disconnect()
    }

    @Test
    fun gotoFormatsAzAlt() = runTest {
        val conn = FakeConnection()
        val (s, t) = newSession(conn, this)
        s.connect()
        t.gotoAzAlt(180.0, 45.0)
        assertEquals("1&519&2&az:180.0000;alt:45.0000;#", String(conn.written[1], Charsets.US_ASCII))
        s.disconnect()
    }

    @Test
    fun requestReturnsParsedValue() = runTest {
        val conn = FakeConnection()
        // Stage a 284 for the handshake and a separate 284 for the explicit
        // request below. The handshake in connect() consumes the first.
        conn.responses += "1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII)
        val (s, _) = newSession(conn, this)
        s.connect()
        // After handshake, queue the ASTRO 284 reply for the explicit request.
        conn.responses += "1&284&2&mode:2;battery:50;#".toByteArray(Charsets.US_ASCII)
        val result = s.request(284) { MountState.fromFrame284(it) }
        val st = result as MountSession.CmdResult.Ok
        assertEquals(MountMode.ASTRO, st.value.mode)
        assertEquals(50, st.value.batteryPercent)
        s.disconnect()
    }
}
