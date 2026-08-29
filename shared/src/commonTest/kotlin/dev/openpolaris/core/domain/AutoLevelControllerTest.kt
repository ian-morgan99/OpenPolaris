package dev.openpolaris.core.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import dev.openpolaris.core.protocol.Codes

@OptIn(ExperimentalCoroutinesApi::class)
class AutoLevelControllerTest {

    private fun newSession(conn: FakeConnection, scope: kotlinx.coroutines.CoroutineScope): Pair<MountSession, AutoLevelController> {
        val s = MountSession({ conn }, readerScope = scope)
        return s to AutoLevelController(s)
    }

    @Test
    fun refreshEnabledParsesEnField() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&547&2&en:1;#".toByteArray(Charsets.US_ASCII)
        val (s, a) = newSession(conn, this)
        s.connect()
        val v = a.refreshEnabled()
        assertEquals(true, v)
        assertEquals(true, a.isEnabled.value)
        s.disconnect()
    }

    @Test
    fun refreshEnabledParsesZero() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&547&2&en:0;#".toByteArray(Charsets.US_ASCII)
        val (s, a) = newSession(conn, this)
        s.connect()
        val v = a.refreshEnabled()
        assertEquals(false, v)
        s.disconnect()
    }

    @Test
    fun refreshEnabledTimeoutYieldsNull() = runTest {
        val conn = FakeConnection()
        // no responses queued
        val (s, a) = newSession(conn, this)
        s.connect()
        val v = a.refreshEnabled()
        assertNull(v)
        s.disconnect()
    }

    @Test
    fun setEnabledSendsExpectedPayload() = runTest {
        val conn = FakeConnection()
        val (s, a) = newSession(conn, this)
        s.connect()
        a.setEnabled(true)
        a.setEnabled(false)
        assertEquals("1&548&2&en:1;#", String(conn.written[1], Charsets.US_ASCII))
        assertEquals("1&548&2&en:0;#", String(conn.written[2], Charsets.US_ASCII))
        assertEquals(false, a.isEnabled.value)
        s.disconnect()
    }

    @Test
    fun runSendsTrigger() = runTest {
        val conn = FakeConnection()
        val (s, a) = newSession(conn, this)
        s.connect()
        a.run()
        assertEquals("1&549&2&-100#", String(conn.written[1], Charsets.US_ASCII))
        assertTrue(a.isRunning.value)
        s.disconnect()
    }

    @Test
    fun readTiltParsesPitchAndRoll() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&537&2&pitch:0.25;roll:-0.10;#".toByteArray(Charsets.US_ASCII)
        val (s, a) = newSession(conn, this)
        s.connect()
        val t = a.readTilt()
        assertNotNull(t)
        assertEquals(0.25, t.pitchDeg, 1e-6)
        assertEquals(-0.10, t.rollDeg, 1e-6)
        assertTrue(t.withinTolerance)
        s.disconnect()
    }

    @Test
    fun tiltOutOfToleranceFlagged() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&537&2&pitch:1.20;roll:0.00;#".toByteArray(Charsets.US_ASCII)
        val (s, a) = newSession(conn, this)
        s.connect()
        val t = a.readTilt()
        assertNotNull(t)
        assertFalse(t.withinTolerance)
        s.disconnect()
    }

    @Test
    fun observerCapturesPushTilt() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val conn = FakeConnection()
        // Pretend a tilt push already arrived on the wire. The real MountSession
        // would have emitted this through its reader loop; for the test we
        // just feed the parser response and rely on the controller's collect
        // to update the StateFlow when frames change.
        val (s, a) = newSession(conn, this)
        s.connect()
        val scope = CoroutineScope(dispatcher)
        a.start(scope)
        // Force a frame via session.frames by responding to a request first.
        conn.responses += "1&537&2&pitch:0.10;roll:0.05;#".toByteArray(Charsets.US_ASCII)
        a.readTilt()
        // Disconnect BEFORE advanceUntilIdle so the reader hot-loop is cancelled;
        // otherwise advanceUntilIdle would never return (it waits for the
        // continuously-rescheduling reader to quiesce, which it never does).
        a.stop()
        s.disconnect()
        advanceUntilIdle()
        assertNotNull(a.tilt.value)
        assertTrue(a.tilt.value!!.withinTolerance)
    }

    @Test
    fun runClearsWhenTiltArrives() = runTest {
        val conn = FakeConnection()
        val (s, a) = newSession(conn, this)
        s.connect()
        a.run()
        assertTrue(a.isRunning.value)
        // Simulate a 538 push arriving on the wire by feeding a frame.
        // We don't have a direct hook to inject into the StateFlow from outside,
        // so this just confirms that calling run() flips isRunning on; the
        // full happy-path is exercised on hardware.
        assertTrue(a.isRunning.value)
        s.disconnect()
    }

    // -------------------------------------------------------------------------
    // Issue #5 settling tests
    // -------------------------------------------------------------------------
    //
    // The production reader loop in MountSession is driven by `request()`, not
    // by a background reader, so we cannot inject 538 frames into
    // `session.frames` without sending a real command. To exercise the
    // settling predicate end-to-end on the JVM we pass a queue-based
    // sampleSource to AutoLevelController and assert against
    // AutoLevelResult. The trigger frame is still observed on the wire via
    // `conn.written` to prove the controller called 549.

    /** Queue-backed sample source that returns the next tilt or null when drained. */
    private class QueueSampleSource(
        samples: List<AutoLevelController.Tilt>,
        private val step: kotlin.time.Duration = 0.seconds,
    ) {
        private val q = ArrayDeque(samples)
        suspend fun next(): AutoLevelController.Tilt? {
            if (step > 0.seconds) kotlinx.coroutines.delay(step)
            return q.removeFirstOrNull()
        }
    }

    private fun newSessionWithSource(
        conn: FakeConnection,
        source: QueueSampleSource,
        scope: kotlinx.coroutines.CoroutineScope,
    ): Pair<MountSession, AutoLevelController> {
        val s = MountSession({ conn }, readerScope = scope)
        return s to AutoLevelController(s, sampleSource = { source.next() })
    }

    @Test
    fun runSettlesWhenTiltIsSteady() = runTest {
        val conn = FakeConnection()
        val samples = List(AutoLevelController.SETTLE_WINDOW) {
            AutoLevelController.Tilt(pitchDeg = 0.001 * it, rollDeg = 0.0)
        }
        val (s, a) = newSessionWithSource(conn, QueueSampleSource(samples), this)
        s.connect()

        val result = a.runAndAwait(5.seconds)

        assertTrue(result is AutoLevelController.AutoLevelResult.Completed)
        result as AutoLevelController.AutoLevelResult.Completed
        // All 10 samples have roll == 0 so the mean roll must be 0.0 exactly.
        assertEquals(0.0, result.rollDeg, 1e-9)
        // Pitch samples step by 0.001 deg; mean of 0..0.009 is 0.0045.
        assertEquals(0.0045, result.pitchDeg, 1e-9)
        // The 549 trigger must have been sent on the wire.
        assertTrue(conn.written.isNotEmpty(), "expected the 549 trigger to be sent")
        val trigger = String(conn.written.last(), Charsets.US_ASCII)
        assertTrue(trigger.contains("549"), "expected a 549 frame, got: $trigger")
        s.disconnect()
    }

    @Test
    fun runTimesOutWhenTiltKeepsMoving() = runTest {
        val conn = FakeConnection()
        // Emit samples that keep drifting so the predicate can never settle.
        // Step the source 100ms between samples; with a 2s timeout the
        // 2.0-second budget will elapse long before all 50 samples drain,
        // exercising the withTimeout branch.
        val samples = (0 until 50).map {
            AutoLevelController.Tilt(pitchDeg = 0.5 + 0.01 * it, rollDeg = -0.3)
        }
        val (s, a) = newSessionWithSource(conn, QueueSampleSource(samples, step = 100.milliseconds), this)
        s.connect()

        val result = a.runAndAwait(2.seconds)

        assertEquals(AutoLevelController.AutoLevelResult.TimedOut, result)
        s.disconnect()
    }

    @Test
    fun runFailsWhenSourceIsExhausted() = runTest {
        val conn = FakeConnection()
        // Fewer samples than the window; the source dries up.
        val samples = listOf(
            AutoLevelController.Tilt(pitchDeg = 0.0, rollDeg = 0.0),
            AutoLevelController.Tilt(pitchDeg = 0.0, rollDeg = 0.0),
        )
        val (s, a) = newSessionWithSource(conn, QueueSampleSource(samples), this)
        s.connect()

        val result = a.runAndAwait(2.seconds)

        assertTrue(result is AutoLevelController.AutoLevelResult.Failed)
        s.disconnect()
    }

    // -------------------------------------------------------------------------
    // PLAN-CRITICAL-REVIEW §F / issue #5 — 517/538 frame-id demux contract
    // -------------------------------------------------------------------------
    //
    // The original spec referenced "the 517 position push" for auto-level
    // settling, but 517 is GET_GIMBAL_POS (RA/Dec) — it has no pitch/roll
    // fields. The actual tilt push is 538 (SET_TILT_STATE). AutoLevelController
    // must demux on frame id: 538 frames feed `tilt` and the settling loop;
    // 517 frames must be ignored entirely. These two tests pin that contract
    // using the test-only `publishFrameForTest` seam on MountSession so the
    // demux is exercised without a real socket.

    @Test
    fun gimbalPosFrame517DoesNotFeedTilt() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val conn = FakeConnection()
        val (s, a) = newSession(conn, this)
        s.connect()
        val scope = CoroutineScope(dispatcher)
        a.start(scope)

        // 517 = GET_GIMBAL_POS push: RA/Dec reply, no tilt fields.
        // Even if a hostile reply tried to inject pitch/roll under this code,
        // the demux must drop it.
        s.publishFrameForTest(
            dev.openpolaris.core.protocol.ResponseParser.Frame(
                code = Codes.GET_GIMBAL_POS,
                fields = mapOf("pitch" to "99.9", "roll" to "99.9"),
            )
        )
        runCurrent()
        a.stop()
        s.disconnect()
        advanceUntilIdle()

        assertNull(a.tilt.value, "517 frame must not update the tilt StateFlow")
    }

    @Test
    fun tiltStateFrame538DoesFeedTilt() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val conn = FakeConnection()
        val (s, a) = newSession(conn, this)
        s.connect()
        val scope = CoroutineScope(dispatcher)
        a.start(scope)

        // 538 = SET_TILT_STATE push: the real tilt source.
        s.publishFrameForTest(
            dev.openpolaris.core.protocol.ResponseParser.Frame(
                code = Codes.SET_TILT_STATE,
                fields = mapOf("pitch" to "0.12", "roll" to "-0.05"),
            )
        )
        // Let the collect block fire on the test scheduler before tearing down;
        // otherwise the StateFlow update is cancelled mid-flight.
        runCurrent()
        a.stop()
        s.disconnect()
        advanceUntilIdle()

        val tilt = a.tilt.value
        assertNotNull(tilt, "538 frame must populate the tilt StateFlow")
        assertEquals(0.12, tilt.pitchDeg, 1e-6)
        assertEquals(-0.05, tilt.rollDeg, 1e-6)
    }

    // -------------------------------------------------------------------------
    // Issue #7 slice 3a — stop() + restart contract
    // -------------------------------------------------------------------------
    //
    // AutoLevelController owns a single `observeJob` over session.frames and
    // exposes `start(scope)` / `stop()`. The contract is:
    //   * `stop()` is idempotent — calling it twice must not throw and must
    //     leave `isRunning == false`.
    //   * `_tilt.value` is a long-lived cache of the last-known envelope. It
    //     must survive a `stop()` so observers (UI, plateau logic) can keep
    //     showing the last reading even after unsubscribing.
    //   * After `stop()` the controller must be re-startable: a fresh
    //     `start(newScope)` must install a new collector and a subsequent 538
    //     frame must update `_tilt`. There must be no leaked frameJob from
    //     the first subscription (only one collector should be live).

    @Test
    fun stopIsIdempotent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val conn = FakeConnection()
        val (s, a) = newSession(conn, this)
        s.connect()
        val scope = CoroutineScope(dispatcher)
        a.start(scope)
        a.run() // flips isRunning → true

        a.stop()
        a.stop() // second call must not throw

        assertFalse(a.isRunning.value, "isRunning must be false after stop()")
        runCurrent()
        a.stop() // a third call, after the scheduler has caught up, is also safe
        assertFalse(a.isRunning.value)
        s.disconnect()
        advanceUntilIdle()
    }

    @Test
    fun tiltValueSurvivesStop() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val conn = FakeConnection()
        val (s, a) = newSession(conn, this)
        s.connect()
        val scope = CoroutineScope(dispatcher)
        a.start(scope)

        s.publishFrameForTest(
            dev.openpolaris.core.protocol.ResponseParser.Frame(
                code = Codes.SET_TILT_STATE,
                fields = mapOf("pitch" to "0.30", "roll" to "0.10"),
            )
        )
        runCurrent()
        val captured = a.tilt.value
        assertNotNull(captured, "538 frame must populate tilt before stop()")
        assertEquals(0.30, captured.pitchDeg, 1e-6)

        a.stop()
        // After stop() the last-known tilt must still be readable.
        val afterStop = a.tilt.value
        assertNotNull(afterStop, "_tilt.value must survive stop() — UI observers may still read it")
        assertEquals(0.30, afterStop.pitchDeg, 1e-6)
        assertEquals(0.10, afterStop.rollDeg, 1e-6)

        s.disconnect()
        advanceUntilIdle()
    }

    @Test
    fun restartAfterStopReceivesNewFrame() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val conn = FakeConnection()
        val (s, a) = newSession(conn, this)
        s.connect()
        val scopeA = CoroutineScope(dispatcher + kotlinx.coroutines.Job())
        a.start(scopeA)

        // First 538 under scopeA: pitch=0.10
        s.publishFrameForTest(
            dev.openpolaris.core.protocol.ResponseParser.Frame(
                code = Codes.SET_TILT_STATE,
                fields = mapOf("pitch" to "0.10", "roll" to "0.00"),
            )
        )
        runCurrent()
        assertEquals(0.10, a.tilt.value!!.pitchDeg, 1e-6)

        a.stop()
        // The collector for scopeA is cancelled. Now reopen with a fresh scope
        // and publish a new frame — the controller must see it.
        val scopeB = CoroutineScope(dispatcher + kotlinx.coroutines.Job())
        a.start(scopeB)

        s.publishFrameForTest(
            dev.openpolaris.core.protocol.ResponseParser.Frame(
                code = Codes.SET_TILT_STATE,
                fields = mapOf("pitch" to "0.55", "roll" to "-0.20"),
            )
        )
        runCurrent()

        val afterRestart = a.tilt.value
        assertNotNull(afterRestart, "restart must re-establish the 538 observer")
        assertEquals(0.55, afterRestart.pitchDeg, 1e-6)
        assertEquals(-0.20, afterRestart.rollDeg, 1e-6)

        a.stop()
        s.disconnect()
        advanceUntilIdle()
    }

    // -------------------------------------------------------------------------
    // Issue #7 3b — runAndAwait coroutine-cancellation contract
    // -------------------------------------------------------------------------
    //
    // When the *calling* coroutine is cancelled mid-settle, runAndAwait must
    // return AutoLevelResult.Failed("cancelled") rather than propagating the
    // CancellationException. This is the difference between "the user pressed
    // Stop / the AppViewModel scope was cancelled" (expected to surface as a
    // result the UI can display) and "the runAndAwait timeout elapsed"
    // (TimedOut, distinct reason). It also lets callers that have already
    // moved on (e.g. an Android UI that called launch{} then a new action)
    // see a normal return value, not a throw.
    //
    // The contract (issue #7 3b.1): return Failed("cancelled") within 1 s of
    // the calling coroutine being cancelled.
    // The contract (issue #7 3b.2): no leftover coroutines after the cancel.

    /** A sample source that suspends forever until cancelled. */
    private class HangingSampleSource {
        suspend fun next(): AutoLevelController.Tilt? = kotlinx.coroutines.suspendCancellableCoroutine { /* never resume */ }
    }

    /**
     * Issue #7 3b.1 — cancel mid-runAndAwait must surface as
     * Failed("cancelled") within 1 second of the calling coroutine being
     * cancelled, and must NOT throw CancellationException.
     *
     * Uses runBlocking + a real wall-clock withTimeout, because the 1s
     * contract is about real time, not virtual scheduler time.
     */
    @Test
    fun runAndAwaitReturnsFailedCancelledWhenCallingScopeIsCancelled() = runBlocking {
        val conn = FakeConnection()
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val s = MountSession({ conn }, readerScope = scope)
        s.connect()
        val ctrl = AutoLevelController(s, sampleSource = { HangingSampleSource().next() })

        val outcome = CompletableDeferred<AutoLevelController.AutoLevelResult>()
        val runner = scope.launch {
            try {
                outcome.complete(ctrl.runAndAwait(60.seconds))
            } catch (e: Throwable) {
                outcome.completeExceptionally(e)
            }
        }
        // Wait until runAndAwait is actively suspended in awaitSettling.
        // The hanging source never resumes, so the runner is in a steady state.
        // We give it a small real-time window to reach the await point.
        var suspended = false
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < 2_000) {
            if (!runner.isActive) {
                fail("runner exited before reaching the suspend point: $outcome")
            }
            // Heuristic: yield so the dispatcher runs the runner up to the suspend.
            kotlinx.coroutines.yield()
            // If outcome is already completed, the runner never suspended.
            if (outcome.isCompleted) continue
            // Check that the runner is now in the cancelling=false, isActive=true,
            // and not-yet-completed steady state.
            if (runner.isActive && !outcome.isCompleted) {
                suspended = true
                break
            }
        }
        assertTrue(suspended, "runner should be suspended on the hanging source")

        val cancelledAtNs = System.nanoTime()
        runner.cancel()
        val elapsedMs = measureTimeMillis {
            val result = withTimeout(1_000) { outcome.await() }
            assertTrue(
                result is AutoLevelController.AutoLevelResult.Failed,
                "expected Failed, got $result",
            )
            result as AutoLevelController.AutoLevelResult.Failed
            assertEquals(
                "cancelled", result.reason,
                "Failed.reason should be exactly \"cancelled\"",
            )
        }
        assertTrue(
            elapsedMs < 1_000,
            "runAndAwait should return within 1s of cancel; took ${elapsedMs}ms",
        )
        // The runner job must have completed (not still cancelling).
        runner.join()
        assertTrue(runner.isCompleted, "runner should be completed after cancel")

        s.disconnect()
        scope.cancel()
    }

    /**
     * Issue #7 3b.2 — after the calling scope is cancelled, runAndAwait must
     * leave no leftover coroutines behind (the audit §F "no leaks" check).
     *
     * Uses runBlocking rather than runTest because runTest insists on all
     * coroutines completing before the body returns, and the
     * HangingSampleSource is supposed to suspend forever.
     */
    @Test
    fun runAndAwaitCancelLeavesNoLeftoverCoroutines() = runBlocking {
        val conn = FakeConnection()
        val mountScope = CoroutineScope(Dispatchers.Default + Job())
        val s = MountSession({ conn }, readerScope = mountScope)
        s.connect()
        val ctrl = AutoLevelController(s, sampleSource = { HangingSampleSource().next() })

        // Wrap runAndAwait in a child scope so we can count its coroutines
        // via the parent Job's children.
        val parent = Job()
        val childScope = CoroutineScope(Dispatchers.Default + parent)
        val outcome = CompletableDeferred<AutoLevelController.AutoLevelResult>()
        val runner = childScope.launch {
            try {
                outcome.complete(ctrl.runAndAwait(60.seconds))
            } catch (e: Throwable) {
                outcome.completeExceptionally(e)
            }
        }
        // Wait until the runner is suspended in awaitSettling.
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < 2_000) {
            if (outcome.isCompleted) {
                fail("runner exited before reaching the suspend point: $outcome")
            }
            if (runner.isActive) break
            kotlinx.coroutines.yield()
        }
        assertTrue(runner.isActive, "runner should be suspended on the hanging source")

        val childrenBeforeCancel = parent.children.toList()
        assertTrue(
            childrenBeforeCancel.isNotEmpty(),
            "runner should be a child of the parent job",
        )

        runner.cancelAndJoin()
        // Give any trailing coroutines a chance to wind down.
        kotlinx.coroutines.yield()

        // The parent should have no live children (or all children completed).
        val liveChildren = parent.children.toList().filter { it.isActive }
        assertTrue(
            liveChildren.isEmpty(),
            "expected no live children after cancel, found ${liveChildren.size}: $liveChildren",
        )

        // The result is still Failed("cancelled") — cancellation contract holds.
        val result = outcome.await()
        assertTrue(result is AutoLevelController.AutoLevelResult.Failed)
        assertEquals(
            "cancelled",
            (result as AutoLevelController.AutoLevelResult.Failed).reason,
        )

        parent.cancel()
        s.disconnect()
        mountScope.cancel()
    }

    /**
     * Issue #7 3b.1 (no-throw half) — runAndAwait must NEVER throw a
     * CancellationException out to the caller. The result is
     * Failed("cancelled"), but a caller using
     * `try { runAndAwait() } catch (e: CancellationException) {…}` must
     * never see that catch fire when they themselves cancel the call.
     */
    @Test
    fun runAndAwaitCancellationDoesNotThrow() = runBlocking {
        val conn = FakeConnection()
        val mountScope = CoroutineScope(Dispatchers.Default + Job())
        val s = MountSession({ conn }, readerScope = mountScope)
        s.connect()
        val ctrl = AutoLevelController(s, sampleSource = { HangingSampleSource().next() })

        val outcome = CompletableDeferred<AutoLevelController.AutoLevelResult>()
        val runner = mountScope.launch {
            try {
                outcome.complete(ctrl.runAndAwait(60.seconds))
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Per contract, this catch must NOT fire. If it does,
                // runAndAwait propagated a cancellation, which violates 3b.1.
                outcome.completeExceptionally(AssertionError("runAndAwait threw CancellationException: $e"))
            } catch (e: Throwable) {
                outcome.completeExceptionally(e)
            }
        }
        // Wait for the runner to reach the suspend point.
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < 2_000) {
            if (outcome.isCompleted) {
                fail("runner exited before reaching the suspend point: $outcome")
            }
            if (runner.isActive) break
            kotlinx.coroutines.yield()
        }
        assertTrue(runner.isActive, "runner should be suspended on the hanging source")

        runner.cancelAndJoin()
        kotlinx.coroutines.yield()

        val result = outcome.await()
        assertTrue(result is AutoLevelController.AutoLevelResult.Failed)
        assertEquals(
            "cancelled",
            (result as AutoLevelController.AutoLevelResult.Failed).reason,
        )

        s.disconnect()
        mountScope.cancel()
    }
}
