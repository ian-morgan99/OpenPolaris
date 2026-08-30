package dev.openpolaris.core.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * In-memory Connection that records writes and can emit scripted responses.
 *
 * The reader background loop in [MountSession] uses [Connection.read] to
 * detect idle vs close. Returning a hard -1 from a non-suspending function
 * turns the loop into a tight CPU spin that never lets the [runTest]
 * virtual-time scheduler advance. A naive `while (empty) yield()` spins
 * the same way. We need a *real* suspension point: a [Channel] that the
 * test code feeds via [push] or the test scheduler's [advanceUntilIdle]
 * unblocks via cancellation.
 */
class FakeConnection : Connection {
    val written = mutableListOf<ByteArray>()
    private val channel = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    var failConnect = false
    var closed = false
        private set

    /** Queue a frame for the next [read] call. */
    fun push(frame: ByteArray) {
        channel.trySend(frame)
    }

    override suspend fun connect(host: String, port: Int, timeoutMs: Int) {
        if (failConnect) throw java.io.IOException("refused")
    }

    override suspend fun write(data: ByteArray) {
        written += data
    }

    override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
        // If the test is shutting the connection down we want read() to
        // wake up so the reader can observe the close.
        if (closed) return 0
        // Real suspension point. The test scheduler can advance virtual
        // time while we are parked here; the reader is not burning CPU.
        // On cancellation (e.g. disconnect, runTest end) the receive
        // throws and we let it propagate — that is what tells the
        // reader to stop.
        val frame = channel.receive()
        frame.copyInto(buffer)
        return frame.size
    }

    override fun close() {
        closed = true
        channel.close()
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
        assertEquals("1&531&2&state:1;#", String(conn.written[1], Charsets.US_ASCII))
    }

    @Test
    fun startWithSpeedIncludesSpeedField() = runTest {
        val conn = FakeConnection()
        val (s, t) = newSession(conn, backgroundScope)
        s.connect()
        t.start(speed = 2)
        assertEquals("1&531&2&state:1;speed:2;#", String(conn.written[1], Charsets.US_ASCII))
    }

    @Test
    fun stopSendsTrackOff() = runTest {
        val conn = FakeConnection()
        val (s, t) = newSession(conn, backgroundScope)
        s.connect()
        t.stop()
        assertEquals("1&531&2&state:0;#", String(conn.written[1], Charsets.US_ASCII))
    }

    @Test
    fun halfSpeedIsInvertedOnWire() = runTest {
        val conn = FakeConnection()
        val (s, t) = newSession(conn, backgroundScope)
        s.connect()
        t.setHalfSpeed(true)
        t.setHalfSpeed(false)
        assertEquals("1&536&2&halfSpeed:0;#", String(conn.written[1], Charsets.US_ASCII))
        assertEquals("1&536&2&halfSpeed:1;#", String(conn.written[2], Charsets.US_ASCII))
    }

    @Test
    fun gotoFormatsAzAlt() = runTest {
        val conn = FakeConnection()
        val (s, t) = newSession(conn, backgroundScope)
        s.connect()
        t.gotoAzAlt(180.0, 45.0)
        assertEquals("1&519&2&az:180.0000;alt:45.0000;#", String(conn.written[1], Charsets.US_ASCII))
    }

    @Test
    fun requestReturnsParsedValue() = runTest {
        val conn = FakeConnection()
        val (s, _) = newSession(conn, backgroundScope)
        s.connect()
        val deferred = async {
            val r = s.request(284) { MountState.fromFrame284(it) }
            r
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
    }
}
