package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** In-memory Connection that records writes and can emit scripted responses. */
class FakeConnection : Connection {
    val written = mutableListOf<ByteArray>()
    val responses = mutableListOf<ByteArray>()
    var failConnect = false

    override suspend fun connect(host: String, port: Int, timeoutMs: Int) {
        if (failConnect) throw java.io.IOException("refused")
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

class TrackingControllerTest {

    private fun newSession(conn: FakeConnection): Pair<MountSession, TrackingController> {
        val s = MountSession({ conn })
        return s to TrackingController(s)
    }

    @Test
    fun startSendsTrackOn() = runTest {
        val conn = FakeConnection()
        val (s, t) = newSession(conn)
        s.connect()
        t.start()
        assertEquals("1&531&2&state:1;#", String(conn.written[1], Charsets.US_ASCII))
    }

    @Test
    fun stopSendsTrackOff() = runTest {
        val conn = FakeConnection()
        val (s, t) = newSession(conn)
        s.connect()
        t.stop()
        assertEquals("1&531&2&state:0;#", String(conn.written[1], Charsets.US_ASCII))
    }

    @Test
    fun halfSpeedIsInvertedOnWire() = runTest {
        val conn = FakeConnection()
        val (s, t) = newSession(conn)
        s.connect()
        t.setHalfSpeed(true)
        t.setHalfSpeed(false)
        assertEquals("1&536&2&halfSpeed:0;#", String(conn.written[1], Charsets.US_ASCII))
        assertEquals("1&536&2&halfSpeed:1;#", String(conn.written[2], Charsets.US_ASCII))
    }

    @Test
    fun gotoFormatsAzAlt() = runTest {
        val conn = FakeConnection()
        val (s, t) = newSession(conn)
        s.connect()
        t.gotoAzAlt(180.0, 45.0)
        assertEquals("1&519&2&az:180.0000;alt:45.0000;#", String(conn.written[1], Charsets.US_ASCII))
    }

    @Test
    fun requestReturnsParsedValue() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&284&2&mode:2;battery:50;#".toByteArray(Charsets.US_ASCII)
        val (s, _) = newSession(conn)
        s.connect()
        val result = s.request(284) { MountState.fromFrame284(it) }
        val st = result as MountSession.CmdResult.Ok
        assertEquals(MountMode.ASTRO, st.value.mode)
        assertEquals(50, st.value.batteryPercent)
    }
}
