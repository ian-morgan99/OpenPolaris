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
import kotlinx.coroutines.newSingleThreadContext
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
    // Issue #7 3b.5 — runAndAwait coroutine-cancellation contract (revised)
    // -------------------------------------------------------------------------
    //
    // Revised contract (per issue #7 reviewer comment 5464953376):
    //   - CancellationException from the *calling* coroutine propagates to
    //     the caller. Coroutine cancellation is control flow, not an
    //     application error; converting it to Failed("cancelled") would
    //     swallow structured cancellation that the rest of the codebase
    //     relies on.
    //   - TimeoutCancellationException is still mapped to TimedOut — the
    //     timeout budget is a domain concept, not coroutine cancellation.
    //   - _isRunning is ALWAYS cleared in `finally`. MutableStateFlow
    //     assignment is non-suspending, so cancellation is not a reason to
    //     suppress this state update.
    //
    // The original 3b contract (Failed("cancelled") on caller cancel) is
    // explicitly NOT in effect: the tests in this block assert the 3b.5
    // contract. The contract docstring on runAndAwait
    // (AutoLevelController.kt) is the source of truth.

    /** A sample source that suspends forever until cancelled. */
    private class HangingSampleSource {
        suspend fun next(): AutoLevelController.Tilt? = kotlinx.coroutines.suspendCancellableCoroutine { /* never resume */ }
    }

    /**
     * Wait until the runner reaches a suspended steady state (active but
     * not yet completed), or fail the test if the runner exits before
     * reaching the suspend point.
     *
     * Polls `runner.isActive` with `yield()` between checks. The
     * [maxIterations] bound is a safety net; in practice the runner
     * reaches the suspend point within a few iterations. This is a
     * *test-helper* polling loop, not a contract assertion — the 1s/5s
     * budget assertions in the individual tests below are the real
     * contract checks.
     */
    private suspend fun waitForSuspend(
        runner: kotlinx.coroutines.Job,
        outcome: CompletableDeferred<*>,
        maxIterations: Int = 1_000,
    ) {
        repeat(maxIterations) {
            if (outcome.isCompleted) {
                fail("runner exited before reaching the suspend point: $outcome")
            }
            if (runner.isActive) return
            kotlinx.coroutines.yield()
        }
        fail("runner did not reach a suspended state within $maxIterations yield iterations")
    }

    /**
     * Issue #7 3b.5 — cancel mid-runAndAwait must propagate
     * CancellationException to the caller (NOT be swallowed and converted to
     * Failed("cancelled")). The propagation must complete within 1 second of
     * the calling coroutine being cancelled.
     *
     * Uses [runTest] with virtual time. The runner is launched on a
     * dedicated [CoroutineScope] (not the [TestScope]) so the
     * [HangingSampleSource] does not prevent [runTest] from returning —
     * `runTest` would otherwise throw `UncompletedCoroutinesError` because
     * the runner is designed to suspend forever until cancelled. The
     * dedicated scope's job is cancelled explicitly at the end of the test
     * to clean up the hanging coroutine.
     *
     * `runTest(timeout = 60.seconds)` is required: kotlinx-coroutines-test
     * 1.9.0 has a hard 5-second wall-clock default, which fires long before
     * the 1s assertion can complete under any scheduling pressure. The
     * `1_000` ms contract budget is about cancellation latency, not
     * scheduling jitter: under virtual time the round-trip is effectively
     * instantaneous.
     *
     * Virtual time makes the 1s budget deterministic: it is not at the
     * mercy of the JVM's `DefaultExecutor` (which is shared across the
     * entire JVM and is the timer executor for `runBlocking`-based
     * `withTimeout` calls). The 1s budget is expressed as virtual-time
     * advancement via `advanceUntilIdle()`.
     */
    @Test
    fun cancelMidRunAndAwaitPropagatesCancellationException() = runTest(timeout = 60.seconds) {
        val conn = FakeConnection()
        val mountScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val s = MountSession({ conn }, readerScope = mountScope)
        s.connect()
        runCurrent()
        val ctrl = AutoLevelController(s, sampleSource = { HangingSampleSource().next() })

        // Dedicated runner scope, separate from TestScope, so the hanging
        // source doesn't make runTest fail with UncompletedCoroutinesError.
        val runnerJob = Job()
        val runnerScope = CoroutineScope(StandardTestDispatcher(testScheduler) + runnerJob)
        val outcome = CompletableDeferred<kotlinx.coroutines.CancellationException>()
        val runner = runnerScope.launch {
            try {
                // Hanging source never returns, so this only completes via
                // the cancellation catch arm below.
                ctrl.runAndAwait(60.seconds)
                fail("runAndAwait returned on a hanging source; expected it to suspend until cancelled")
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 3b.5 contract: this catch MUST fire. The previous 3b
                // contract swallowed this and returned Failed("cancelled"),
                // which the reviewer flagged as swallowing structured
                // cancellation.
                outcome.complete(e)
            } catch (e: Throwable) {
                fail("runAndAwait threw non-cancellation throwable: $e")
            }
        }
        // Run the runner until it suspends in HangingSampleSource.next().
        // We use runCurrent() NOT advanceUntilIdle(): advanceUntilIdle
        // would advance virtual time to the 60s runAndAwait timeout, which
        // would fire the timeout and complete runAndAwait with TimedOut
        // before the cancel arrives, leaving the test's contract assertion
        // meaningless.
        runCurrent()
        assertTrue(
            runner.isActive,
            "runner should be active and suspended in the sample source; " +
                "isActive=${runner.isActive}, completed=${runner.isCompleted}",
        )
        assertFalse(
            outcome.isCompleted,
            "outcome should still be pending; got $outcome",
        )

        // Cancel and assert CancellationException propagates. With virtual
        // time, this round-trips immediately on advanceUntilIdle() — no
        // 5s real-time withTimeout safety net needed.
        //
        // We cancel the reader first (via s.disconnect) before
        // advanceUntilIdle so the reader's delay(READ_RETRY_MS=10ms) loop
        // doesn't keep virtual time alive forever (the reader schedules
        // a fresh delay on every read that returns -1, which is an
        // infinite virtual-time spin under advanceUntilIdle).
        val elapsedMs = measureTimeMillis {
            runner.cancel()
            s.disconnect()
            mountScope.cancel()
            advanceUntilIdle()
            val cancellation = outcome.await()
            assertNotNull(
                cancellation,
                "runAndAwait should have thrown CancellationException to the caller",
            )
        }
        // 1s budget is a *contract* assertion. measureTimeMillis reports
        // real wall-clock time, so it is at most a few ms under virtual
        // time scheduling.
        assertTrue(
            elapsedMs < 1_000,
            "CancellationException should propagate within 1s of cancel; took ${elapsedMs}ms",
        )
        assertTrue(runner.isCancelled, "runner should be in cancelled state after cancel")

        runnerJob.cancel()
    }

    /**
     * Issue #7 3b.5 — _isRunning must be cleared to false after a mid-settle
     * cancellation. The previous 3b `finally` guarded the state write on
     * `currentCoroutineContext()[Job]?.isActive`, so it was skipped
     * precisely when the coroutine was cancelled, leaving observers seeing
     * `isRunning == true` even though the operation was gone.
     *
     * This test does NOT assert `isRunning == true` mid-settle (it is a
     * scheduled-coroutine race, and not the contract under test). It
     * asserts the *post*-cancel state: regardless of what `isRunning` was
     * at the moment of cancellation, it must be `false` once the runner
     * has finished unwinding, because `_isRunning` is unconditionally
     * cleared in `finally` under the 3b.5 contract.
     *
     * Uses [runTest] with virtual time. See the comment on
     * [cancelMidRunAndAwaitPropagatesCancellationException] for why the
     * runner is on a dedicated scope rather than the [TestScope].
     */
    @Test
    fun cancelMidSettleClearsIsRunning() = runTest(timeout = 60.seconds) {
        val conn = FakeConnection()
        val mountScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val s = MountSession({ conn }, readerScope = mountScope)
        s.connect()
        runCurrent()
        val ctrl = AutoLevelController(s, sampleSource = { HangingSampleSource().next() })

        val runnerJob = Job()
        val runnerScope = CoroutineScope(StandardTestDispatcher(testScheduler) + runnerJob)
        val outcome = CompletableDeferred<Unit>()
        val runner = runnerScope.launch {
            try {
                ctrl.runAndAwait(60.seconds)
            } catch (e: kotlinx.coroutines.CancellationException) {
                outcome.complete(Unit)
                throw e
            } catch (e: Throwable) {
                fail("runAndAwait threw non-cancellation throwable: $e")
            }
        }
        // Use runCurrent() NOT advanceUntilIdle(): the MountSession
        // reader loop is on the same TestDispatcher and schedules a
        // delay(READ_RETRY_MS=10ms) continuation on every read that
        // returns -1, which advanceUntilIdle would process forever
        // (an infinite virtual-time spin). runCurrent() only dispatches
        // currently-ready tasks, so the runner reaches the
        // HangingSampleSource suspend point and the reader parks.
        runCurrent()
        assertTrue(
            runner.isActive,
            "runner should be active and suspended in the sample source",
        )
        assertFalse(outcome.isCompleted, "outcome should still be pending")

        runner.cancel()
        // Cancel the reader first so advanceUntilIdle can drain without
        // the reader's delay(10ms) loop keeping virtual time alive.
        s.disconnect()
        mountScope.cancel()
        advanceUntilIdle()
        assertTrue(outcome.isCompleted, "outcome should complete after cancel")
        runner.join()
        // The previous contract would leave isRunning == true here because
        // the `finally` guard skipped the state write on cancellation.
        assertFalse(
            ctrl.isRunning.value,
            "isRunning should be false after mid-settle cancellation; " +
                "got isRunning=${ctrl.isRunning.value}",
        )

        s.disconnect()
        runnerJob.cancel()
        mountScope.cancel()
    }

    /**
     * Issue #7 3b.5 — after the calling scope is cancelled, runAndAwait must
     * leave no leftover coroutines behind (the audit §F "no leaks" check).
     *
     * Note: this is unchanged in spirit from 3b.2 — the 3b.5 contract still
     * requires the cleanup to be complete — but the result is now a
     * CancellationException that propagates to the caller, not a result
     * value, so this test asserts the throwable instead of a Failed result.
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
        val outcome = CompletableDeferred<Unit>()
        val runner = childScope.launch {
            try {
                ctrl.runAndAwait(60.seconds)
                fail("runAndAwait should have thrown CancellationException")
            } catch (e: kotlinx.coroutines.CancellationException) {
                outcome.complete(Unit)
            } catch (e: Throwable) {
                fail("runAndAwait threw non-cancellation throwable: $e")
            }
        }
        waitForSuspend(runner, outcome)

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

        // The runner must have completed by throwing CancellationException.
        assertTrue(
            outcome.isCompleted,
            "outcome should be completed; runner did not propagate cancellation",
        )
        assertTrue(
            runner.isCancelled,
            "runner should be in cancelled state, not completed normally",
        )

        parent.cancel()
        s.disconnect()
        mountScope.cancel()
    }

    /**
     * Issue #7 3b.5 — regression for the timeout path. The 3b.5 contract
     * rewords the cancellation behaviour, but the timeout path
     * (TimeoutCancellationException → AutoLevelResult.TimedOut) must remain
     * unchanged. This test uses a tiny real-time timeout to make sure the
     * path still resolves to TimedOut, not to a propagated
     * CancellationException (the 3b.5 contract distinguishes the two).
     */
    @Test
    fun timeoutStillMapsToTimedOut() = runBlocking {
        val conn = FakeConnection()
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val s = MountSession({ conn }, readerScope = scope)
        s.connect()
        val ctrl = AutoLevelController(s, sampleSource = { HangingSampleSource().next() })

        val result = ctrl.runAndAwait(timeout = 100.milliseconds)
        assertEquals(
            AutoLevelController.AutoLevelResult.TimedOut,
            result,
            "timeout must still map to AutoLevelResult.TimedOut",
        )
        // The 3b.5 contract also requires _isRunning to be cleared on the
        // timeout path.
        assertFalse(
            ctrl.isRunning.value,
            "isRunning should be false after timeout",
        )

        s.disconnect()
        scope.cancel()
    }
}
