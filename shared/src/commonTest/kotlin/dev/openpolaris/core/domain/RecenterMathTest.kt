package dev.openpolaris.core.domain

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Stream 7.5 - the contract for in-VR recenter is:
 *   effective(raw) after recenter(raw) is (0, 0)
 *   the view does not jump on recenter
 *   pressing recenter while already centred is a no-op for the toast
 *
 * These tests assert each half of the contract in isolation so a
 * regression in either the math or the debounce lands a failing test
 * with a clear name.
 */
class RecenterMathTest {

    private val yawAt = { deg: Double -> (deg * PI / 180.0).toFloat() }

    // --- recenter resets effective to origin ---------------------------------

    @Test
    fun `recenter at origin keeps offset at origin`() {
        val r = RecenterMath()
        r.recenter(0f, 0f)
        assertEquals(0f, r.yawOffset)
        assertEquals(0f, r.pitchOffset)
        assertEquals(0f to 0f, r.effective(0f, 0f))
    }

    @Test
    fun `recenter at non-zero pose records offset and zeroes effective`() {
        val r = RecenterMath()
        r.recenter(yawAt(7.0), yawAt(-3.0))

        // The recorded offset is the raw pose at the moment of recenter.
        assertEquals(yawAt(7.0), r.yawOffset, 1e-5f)
        assertEquals(yawAt(-3.0), r.pitchOffset, 1e-5f)

        // The effective pose of that same raw value is now (0, 0).
        assertEquals(0f, r.effective(yawAt(7.0), yawAt(-3.0)).first, 1e-5f)
        assertEquals(0f, r.effective(yawAt(7.0), yawAt(-3.0)).second, 1e-5f)
    }

    @Test
    fun `recenter does not jump the view - effective of unrelated raw shifts by offset`() {
        val r = RecenterMath()
        r.recenter(yawAt(5.0), yawAt(2.0))

        // The "no jump" contract: a user who was looking at some unrelated
        // raw pose sees the same rendered image after recenter, because
        // effective shifts by exactly the offset (raw - offset) and
        // CardboardWarp's pan math is linear in (yaw, pitch).
        val rawY = yawAt(15.0)
        val rawP = yawAt(8.0)
        val (ey, ep) = r.effective(rawY, rawP)
        assertEquals(yawAt(10.0), ey, 1e-5f)
        assertEquals(yawAt(6.0), ep, 1e-5f)
    }

    // --- recenter is idempotent and overwriting --------------------------------

    @Test
    fun `recenter twice with different poses uses the latest`() {
        val r = RecenterMath()
        r.recenter(yawAt(5.0), 0f)
        r.recenter(yawAt(-3.0), yawAt(2.0))

        // Latest wins, not sum.
        assertEquals(yawAt(-3.0), r.yawOffset, 1e-5f)
        assertEquals(yawAt(2.0), r.pitchOffset, 1e-5f)
        assertEquals(0f, r.effective(yawAt(-3.0), yawAt(2.0)).first, 1e-5f)
    }

    @Test
    fun `recenter at the same pose twice is idempotent`() {
        val r = RecenterMath()
        r.recenter(yawAt(4.0), yawAt(-2.0))
        val firstYaw = r.yawOffset
        val firstPitch = r.pitchOffset
        r.recenter(yawAt(4.0), yawAt(-2.0))
        assertEquals(firstYaw, r.yawOffset, 1e-5f)
        assertEquals(firstPitch, r.pitchOffset, 1e-5f)
    }

    // --- clamping protects the eye-shader's downstream clamp -------------------

    @Test
    fun `recenter clamps extreme yaw to maxOffsetRad`() {
        val r = RecenterMath()
        // 45 deg - well outside the 10 deg clamp.
        r.recenter(yawAt(45.0), 0f)
        assertEquals(RecenterMath.MAX_OFFSET_RAD, r.yawOffset, 1e-5f)
    }

    @Test
    fun `recenter clamps extreme negative pitch to -maxOffsetRad`() {
        val r = RecenterMath()
        r.recenter(0f, yawAt(-30.0))
        assertEquals(-RecenterMath.MAX_OFFSET_RAD, r.pitchOffset, 1e-5f)
    }

    @Test
    fun `clamp policy still centres the view for an off-axis press`() {
        val r = RecenterMath()
        // The user is looking 30 deg off-axis and presses recenter. The
        // 10 deg clamp kicks in, the offset is capped, and the next frame
        // still pans 20 deg to centre - the view does not jump, but the
        // recenter still has a real effect.
        r.recenter(yawAt(30.0), 0f)
        val (ey, _) = r.effective(yawAt(30.0), 0f)
        assertEquals(yawAt(20.0), ey, 1e-4f)
    }

    // --- toast debounce ---------------------------------------------------------

    @Test
    fun `recenterWouldChange is true for a clearly off-centre pose`() {
        val r = RecenterMath()
        assertTrue(r.recenterWouldChange(yawAt(5.0), yawAt(-3.0)))
    }

    @Test
    fun `recenterWouldChange is false for a pose inside the epsilon radius`() {
        val r = RecenterMath()
        // ~0.1 deg - well inside the 0.5 deg epsilon.
        assertFalse(r.recenterWouldChange(yawAt(0.1), yawAt(0.05)))
    }

    @Test
    fun `recenterWouldChange becomes false immediately after a recenter`() {
        val r = RecenterMath()
        r.recenter(yawAt(5.0), yawAt(-3.0))
        // The user just pressed the button. Don't flash the toast again
        // the next frame just because sensor noise jitters the effective
        // pose by a fraction of a degree.
        assertFalse(r.recenterWouldChange(yawAt(5.0), yawAt(-3.0)))
    }

    @Test
    fun `recenterWouldChange becomes true again once the user drifts off-centre`() {
        val r = RecenterMath()
        r.recenter(yawAt(5.0), yawAt(-3.0))
        // The user keeps moving after the recenter. Once they're back to
        // a clearly off-centre raw pose, the toast should fire on the
        // *next* recenter.
        assertTrue(r.recenterWouldChange(yawAt(12.0), yawAt(-1.0)))
    }
}
