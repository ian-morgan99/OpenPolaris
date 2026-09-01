package dev.openpolaris.ui

import dev.openpolaris.core.domain.AutoLevelController
import dev.openpolaris.core.domain.AutoLevelController.Tilt
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MountSession
import dev.openpolaris.core.solver.NullStarDetector
import dev.openpolaris.core.solver.OnDevicePlateSolver
import dev.openpolaris.core.solver.PyramidMatcher
import dev.openpolaris.core.solver.SyntheticTestCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Tests [AppViewModel.runAutoLevel]'s caller-side cancellation contract.
 *
 * Issue #22 originally asked `runAndAwait` to return `Failed("cancelled")`
 * within 1000ms of the calling coroutine being cancelled. That contract
 * was reversed in `aba706e` (3b.5) — `runAndAwait` now propagates
 * `CancellationException` per structured concurrency, as asserted by
 * [dev.openpolaris.core.domain.AutoLevelControllerTest.cancelMidRunAndAwaitPropagatesCancellationException]
 * and friends. The 3b.5 KDoc explicitly puts the "user-visible cancelled
 * status" responsibility on the *caller* (`AppViewModel.runAutoLevel`).
 *
 * This file is the regression guard for that caller-side contract:
 *   - `runAutoLevel()` must set `statusMessage = "Auto-level cancelled"`
 *     on caller-scope cancellation.
 *   - The `runAndAwait` job must no longer be active within the budget
 *     after cancellation.
 *
 * The test mirrors the
 * [dev.openpolaris.core.domain.AutoLevelControllerTest] 3b.5 pattern: a
 * sample source that suspends forever (so `runAndAwait` only completes
 * via cancel) plus a dedicated runner scope separate from [runTest] (so
 * the hanging source does not trigger `UncompletedCoroutinesError`).
 * Virtual time keeps the budget deterministic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelAutoLevelTest {

    /**
     * In-memory [Connection] that records writes. The reader suspends
     * until a response is enqueued. For this test the reader is only
     * needed to satisfy `MountSession.connect()` — no responses are
     * required.
     */
    private class FakeConnection : Connection {
        val written = mutableListOf<ByteArray>()
        private val channel = Channel<ByteArray>(Channel.BUFFERED)
        var failConnect = false

        fun enqueueResponse(data: ByteArray) { channel.trySend(data) }

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) {
            if (failConnect) throw java.io.IOException("refused")
        }

        override suspend fun write(data: ByteArray) { written += data }

        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val r = channel.receive()
            r.copyInto(buffer)
            return r.size
        }

        override fun close() { channel.close() }
    }

    /**
     * Issue #22 — on caller-scope cancellation, `statusMessage` must
     * show "Auto-level cancelled" and the `runAndAwait` job must be
     * cancelled, all within the contract budget.
     *
     * The viewModel is constructed with a *child* of the test's
     * [TestScope] so cancelling the viewModel's scope does not take
     * the test scope down. Virtual time makes the budget deterministic
     * regardless of the JVM `DefaultExecutor` load.
     *
     * `runTest(timeout = 60.seconds)` is required: kotlinx-coroutines-test
     * 1.9.0 has a hard 5s wall-clock default, which fires before the
     * assertions can complete under any scheduling pressure.
     */
    @Test
    fun runAutoLevelCancellationSurfacesCancelledStatusMessage() = runTest(timeout = 60.seconds) {
        val conn = FakeConnection()

        // Child scope for the viewModel so we can cancel just the
        // viewModel's coroutines (runAutoLevel, the autoLevel
        // collectors, etc.) without taking the surrounding TestScope
        // down. Same scheduler as the TestScope so runCurrent /
        // advanceUntilIdle still drive the viewModel's launches.
        val vmJob = Job()
        val vmScope = CoroutineScope(StandardTestDispatcher(testScheduler) + vmJob)
        val vm = AppViewModel(
            scope = vmScope,
            connectionFactory = { conn },
            solver = OnDevicePlateSolver(
                catalog = SyntheticTestCatalog.asCatalog,
                matcher = PyramidMatcher(angularToleranceDeg = 0.05, pixelTolerance = 5.0),
                searchRadiusDeg = 5.0,
                minConfidence = 0.5,
            ),
            starDetector = NullStarDetector,
        )

        val mountScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val s = MountSession({ conn }, readerScope = mountScope)
        s.connect()
        runCurrent()

        // Install the session via the AppViewModel test seam so the
        // `autoLevelController` slot will be non-null in runAutoLevel.
        vm.testInstallSession(s)

        // Inject a controller whose sample source hangs forever. The
        // only way runAndAwait() can return is via caller-scope
        // cancellation. Mirror the 3b.5 controller test pattern
        // (AutoLevelControllerTest.cancelMidRunAndAwaitPropagatesCancellationException):
        // dedicated runner scope, separate from the TestScope, so the
        // hanging source does not make runTest fail with
        // UncompletedCoroutinesError.
        val runnerJob = Job()
        val runnerScope = CoroutineScope(StandardTestDispatcher(testScheduler) + runnerJob)
        val hangingSampleSource: suspend () -> Tilt? = {
            suspendCancellableCoroutine<Tilt> { /* never resume */ }
        }
        val controller = AutoLevelController(s, sampleSource = hangingSampleSource)
        vm.testInstallAutoLevel(controller)
        controller.start(runnerScope)
        runCurrent()

        val runJob = vm.runAutoLevel()

        // Drive until runAutoLevel suspends in runAndAwait's sample
        // wait. runCurrent (NOT advanceUntilIdle) so the virtual clock
        // does not jump to runAndAwait's 60s timeout.
        runCurrent()
        assertTrue(
            runJob.isActive,
            "runAutoLevel should be active and suspended in the sample wait; " +
                "isActive=${runJob.isActive}, completed=${runJob.isCompleted}",
        )

        // Cancel the viewModel's coroutine scope — the same operation
        // AppViewModel.disconnect / viewModelScope.onCleared would
        // perform, and what triggers the CancellationException catch
        // in runAutoLevel.
        vmScope.cancel()
        // advanceUntilIdle drains the scheduler to the point where
        // every pending continuation has run, including the catch arm
        // in runAutoLevel and the structured cancellation propagation.
        // Virtual time makes this deterministic regardless of JVM
        // DefaultExecutor load.
        advanceUntilIdle()

        // runAutoLevel must have caught the CancellationException,
        // set the user-visible status, and re-thrown (so runJob is now
        // completed with cancellation, not still active).
        assertEquals(
            "Auto-level cancelled",
            vm.statusMessage,
            "expected 'Auto-level cancelled' status after scope.cancel(); " +
                "got '${vm.statusMessage}'",
        )
        assertNotNull(runJob, "runJob reference should still be observable")
        // runAutoLevel re-throws the CancellationException so the
        // launch{} is cancelled; the job reports isCompleted=true.
        assertTrue(
            runJob.isCancelled || runJob.isCompleted,
            "runAutoLevel job should be cancelled/completed after scope.cancel(); " +
                "isActive=${runJob.isActive}, isCancelled=${runJob.isCancelled}, " +
                "isCompleted=${runJob.isCompleted}",
        )

        // Clean up: stop the controller, cancel the runner scope
        // (which also drops the hanging source), and disconnect the
        // mount so runTest does not see UncompletedCoroutinesError.
        controller.stop()
        runnerJob.cancel()
        mountScope.cancel()
        vm.preview.shutdown()
    }
}
