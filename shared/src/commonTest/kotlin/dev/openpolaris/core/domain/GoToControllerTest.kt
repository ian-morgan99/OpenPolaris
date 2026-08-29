package dev.openpolaris.core.domain

import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoToControllerTest {

    private fun newRig(scope: kotlinx.coroutines.CoroutineScope): Triple<FakeConnection, MountSession, GoToController> {
        val conn = FakeConnection()
        val session = MountSession({ conn }, readerScope = scope)
        val tracking = TrackingController(session)
        return Triple(conn, session, GoToController(session, tracking))
    }

    @Test
    fun `angular distance is symmetric and zero at same point`() {
        assertEquals(0.0, GoToController.angularDistance(10.0, 20.0, 10.0, 20.0), 1e-9)
        val d = GoToController.angularDistance(350.0, 10.0, 10.0, 10.0)
        // great-circle distance at constant altitude shrinks slightly vs az delta
        assertTrue(abs(d - 20.0) < 0.5, "az wrap-around should give ~20 deg, got $d")
    }

    @Test
    fun `shortest angle wraps correctly`() {
        assertEquals(-20.0, GoToController.shortestAngle(340.0))
        assertEquals(10.0, GoToController.shortestAngle(190.0 + 180.0))
        assertEquals(5.0, GoToController.shortestAngle(365.0))
    }

    @Test
    fun `goToRaDec sends corrected az alt frame`() = runTest {
        val (conn, session, c) = newRig(this)
        session.connect()

        // Known transform: RA=LST (HA=0), Dec=+30 at lat 0 ->
        // sin(alt)=cos(dec)=0.866 -> alt=60; az=atan2(0,-tan30)+180 -> 0.
        val jd = AstroMath.julianDate(2024, 1, 1, 12, 0, 0)
        val lst = AstroMath.localSiderealTimeDeg(jd, 0.0)
        val raDeg = lst // HA = 0
        conn.responses += "1&517&2&yaw:0.0;pitch:60.0;roll:0.0;#".toByteArray(Charsets.US_ASCII)
        c.goToRaDec(raDeg, 30.0, latDeg = 0.0, lngEastDeg = 0.0, jdUtc = jd)

        // written[0] is the connect handshake; written[1] is the goto frame.
        val frame = conn.written[1].decodeToString()
        assertTrue(frame.contains("&519&"), "expected goto 519 frame, got $frame")
        assertTrue(frame.contains("alt:60."), "altitude should be ~60, got $frame")
        assertTrue(frame.contains("az:0."), "azimuth should be ~0, got $frame")
        session.disconnect()
    }

    @Test
    fun `pointing model applies offsets after fit`() {
        val m = PointingModel()
        m.addSample(PointingModel.Sample(commandedAz = 100.0, commandedAlt = 40.0, trueAz = 101.0, trueAlt = 39.0))
        m.addSample(PointingModel.Sample(commandedAz = 200.0, commandedAlt = 50.0, trueAz = 201.0, trueAlt = 49.0))
        m.fit()
        assertEquals(1.0, m.azOffset, 1e-9)
        assertEquals(-1.0, m.altOffset, 1e-9)
        val (az, alt) = m.applyTo(45.0, 45.0)
        assertEquals(46.0, az, 1e-9)
        assertEquals(44.0, alt, 1e-9)
    }
}
