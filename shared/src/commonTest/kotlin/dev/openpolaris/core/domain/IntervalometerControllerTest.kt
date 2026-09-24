@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.openpolaris.core.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Simulator tests for the typed intervalometer / capture-sequence engine
 * (handover §6: "interval-plan validation and exact step order").
 */
class IntervalometerControllerTest {

    private fun newRig(
        controllerScope: CoroutineScope,
        readerScope: CoroutineScope,
    ): Triple<FakeConnection, MountSession, IntervalometerController> {
        val conn = FakeConnection()
        val session = MountSession({ conn }, readerScope = readerScope)
        val camera = CameraController(session)
        return Triple(conn, session, IntervalometerController(camera, controllerScope))
    }

    @Test
    fun `plan validation rejects zero or negative shot count`() {
        assertFailsWith<IllegalArgumentException> {
            IntervalometerController.SequencePlan(shotCount = 0, intervalMs = 1000)
        }
        assertFailsWith<IllegalArgumentException> {
            IntervalometerController.SequencePlan(shotCount = -1, intervalMs = 1000)
        }
    }

    @Test
    fun `plan validation rejects negative interval or pre-delay`() {
        assertFailsWith<IllegalArgumentException> {
            IntervalometerController.SequencePlan(shotCount = 3, intervalMs = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            IntervalometerController.SequencePlan(shotCount = 3, intervalMs = 1000, preDelayMs = -5)
        }
    }

    @Test
    fun `totalDurationMs is preDelay plus gaps between shots`() {
        val plan = IntervalometerController.SequencePlan(
            shotCount = 4,
            intervalMs = 30_000,
            preDelayMs = 5_000,
        )
        assertEquals(95_000L, plan.totalDurationMs)
    }

    @Test
    fun `pre-delay elapses before first shutter`() = runTest {
        val (conn, session, c) = newRig(backgroundScope, backgroundScope)
        session.connect()
        assertTrue(c.start(IntervalometerController.SequencePlan(1, intervalMs = 0, preDelayMs = 5_000)))

        runCurrent()
        assertEquals(0, conn.written.map { it.decodeToString() }.count { it.contains("&264&") })
        advanceTimeBy(4_999)
        assertEquals(0, conn.written.map { it.decodeToString() }.count { it.contains("&264&") })
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, conn.written.map { it.decodeToString() }.count { it.contains("&264&") })

        c.stop()
        session.disconnect()
    }

    @Test
    fun `start runs all shots and reaches Completed`() = runTest {
        val (conn, session, c) = newRig(backgroundScope, backgroundScope)
        session.connect()

        val plan = IntervalometerController.SequencePlan(shotCount = 3, intervalMs = 1000)
        assertTrue(c.start(plan))

        repeat(3) {
            runCurrent()
            c.markShotDone()
            advanceTimeBy(1_000)
        }
        runCurrent()

        val s = c.state
        assertTrue(s is IntervalometerController.State.Completed, "expected Completed, got $s")
        assertEquals(3, (s as IntervalometerController.State.Completed).completedShots)
        assertFalse(c.hasInFlightShot)

        // Exactly 3 capture frames (264) must have been written.
        val captures = conn.written.map { it.decodeToString() }
            .count { it.contains("&264&") }
        assertEquals(3, captures, "expected 3 capture frames, got $captures")

        session.disconnect()
    }

    @Test
    fun `start is a no-op when already running`() = runTest {
        val (conn, session, c) = newRig(backgroundScope, backgroundScope)
        session.connect()

        val plan = IntervalometerController.SequencePlan(shotCount = 2, intervalMs = 1000)
        assertTrue(c.start(plan))
        // Second start must be rejected.
        assertFalse(c.start(plan))
        session.disconnect()
    }

    @Test
    fun `pause stops the sequence and resume continues from the next shot`() = runTest {
        val (conn, session, c) = newRig(backgroundScope, backgroundScope)
        session.connect()

        val plan = IntervalometerController.SequencePlan(shotCount = 4, intervalMs = 1000)
        assertTrue(c.start(plan))

        // Let shot 1 fire, then pause before confirmation.
        runCurrent()
        c.pause()
        assertTrue(c.state is IntervalometerController.State.Paused)
        assertTrue(c.hasInFlightShot)

        // Resume must wait for shot 1 rather than sending it again.
        c.resume()
        assertTrue(c.state is IntervalometerController.State.Running)
        runCurrent()
        assertEquals(1, conn.written.map { it.decodeToString() }.count { it.contains("&264&") })
        repeat(4) {
            c.markShotDone()
            advanceTimeBy(1_000)
            runCurrent()
        }

        val s = c.state
        assertTrue(s is IntervalometerController.State.Completed, "expected Completed, got $s")
        assertEquals(4, (s as IntervalometerController.State.Completed).completedShots)

        // Exactly 4 capture frames total.
        val captures = conn.written.map { it.decodeToString() }
            .count { it.contains("&264&") }
        assertEquals(4, captures, "expected 4 capture frames, got $captures")

        session.disconnect()
    }

    @Test
    fun `stop halts the sequence early`() = runTest {
        val (conn, session, c) = newRig(backgroundScope, backgroundScope)
        session.connect()

        val plan = IntervalometerController.SequencePlan(shotCount = 5, intervalMs = 1000)
        assertTrue(c.start(plan))

        // Let shot 1 fire, then stop.
        runCurrent()
        c.stop()
        assertTrue(c.state is IntervalometerController.State.Stopped)
        assertEquals(0, (c.state as IntervalometerController.State.Stopped).completedShots)
        assertFalse(c.hasInFlightShot)

        // Only 1 capture frame was written.
        val captures = conn.written.map { it.decodeToString() }
            .count { it.contains("&264&") }
        assertEquals(1, captures, "expected 1 capture frame, got $captures")

        session.disconnect()
    }

    @Test
    fun `pause and stop are idempotent from terminal states`() = runTest {
        val (conn, session, c) = newRig(backgroundScope, backgroundScope)
        session.connect()

        // From Idle: pause/stop are no-ops.
        c.pause()
        assertTrue(c.state is IntervalometerController.State.Idle)
        c.stop()
        assertTrue(c.state is IntervalometerController.State.Idle)

        session.disconnect()
    }

    @Test
    fun `markShotDone clears the in-flight flag`() = runTest {
        val (conn, session, c) = newRig(backgroundScope, backgroundScope)
        session.connect()

        val plan = IntervalometerController.SequencePlan(shotCount = 2, intervalMs = 1000)
        assertTrue(c.start(plan))

        // Let shot 1 fire; the in-flight flag should be true.
        runCurrent()
        assertTrue(c.hasInFlightShot)

        // Progress observer confirms completion.
        c.markShotDone()
        runCurrent()
        assertFalse(c.hasInFlightShot)

        session.disconnect()
    }

    @Test
    fun `exposure index is set before each capture`() = runTest {
        val (conn, session, c) = newRig(backgroundScope, backgroundScope)
        session.connect()

        // Script a 299 EX_TIME reply so the set succeeds.
        conn.scriptResponse(299, "1&299&2&ret:0;#")

        val plan = IntervalometerController.SequencePlan(
            shotCount = 2,
            intervalMs = 1000,
            exposureIndex = 7,
        )
        assertTrue(c.start(plan))
        repeat(2) {
            runCurrent()
            c.markShotDone()
            advanceTimeBy(1_000)
        }
        runCurrent()

        // Each shot should have a 299 EX_TIME write before its 264 capture.
        val frames = conn.written.map { it.decodeToString() }
        val exTimeWrites = frames.count { it.contains("&299&") && it.contains("ExTime:7;") }
        assertEquals(2, exTimeWrites, "expected 2 EX_TIME writes, got $exTimeWrites")

        session.disconnect()
    }
}
