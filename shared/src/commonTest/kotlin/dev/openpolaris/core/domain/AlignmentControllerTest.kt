package dev.openpolaris.core.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AlignmentControllerTest {

    @Test
    fun submitStarSendsFirmwareFormatWithIncrementingIndex() = runTest {
        val conn = FakeConnection()
        // backgroundScope so the reader is auto-cancelled when the
        // test body returns — otherwise runTest fails with
        // UncompletedCoroutinesError because the reader is parked in
        // its `Channel.receive()` waiting for a frame that is never
        // coming.
        val session = MountSession({ conn }, readerScope = backgroundScope)
        val c = AlignmentController(session)
        session.connect()

        c.submitStar(yawDeg = 123.4567, pitchDeg = 45.6789, latDeg = 51.5, lngEastDeg = -0.12)
        c.submitStar(yawDeg = 200.0, pitchDeg = 30.0, latDeg = 51.5, lngEastDeg = -0.12)

        // Frame: id&code&len&payload# ; written[0] is the connect-time 284 handshake poll.
        assertEquals("1&530&2&step:2;yaw:123.4567;pitch:45.6789;lat:51.5000;num:0;lng:-0.1200;#", conn.written[1].decodeToString())
        assertEquals("1&530&2&step:2;yaw:200.0000;pitch:30.0000;lat:51.5000;num:1;lng:-0.1200;#", conn.written[2].decodeToString())
        assertEquals(2, c.starCount)
        session.disconnect()
    }

    @Test
    fun resetRestartsIndex() = runTest {
        val conn = FakeConnection()
        // backgroundScope so the reader is auto-cancelled when the
        // test body returns — otherwise runTest fails with
        // UncompletedCoroutinesError because the reader is parked in
        // its `Channel.receive()` waiting for a frame that is never
        // coming.
        val session = MountSession({ conn }, readerScope = backgroundScope)
        val c = AlignmentController(session)
        session.connect()
        c.submitStar(10.0, 20.0, 0.0, 0.0)
        c.reset()
        c.submitStar(10.0, 20.0, 0.0, 0.0)
        assertEquals("1&530&2&step:2;yaw:10.0000;pitch:20.0000;lat:0.0000;num:0;lng:0.0000;#",
            conn.written[2].decodeToString())
        session.disconnect()
    }
}
