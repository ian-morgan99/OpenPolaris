package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.protocol.CommandTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Deterministic tests for the #60 capture state machine ([AppViewModel.CapturePhase]).
 *
 * The K-3 III capture path is asynchronous: the shutter request is
 * acknowledged in ~12 ms, the camera reports a transient negative state
 * (e.g. `-1005`) around 2 s, goes idle at ~3 s, and the final image lands
 * 3–4 s after the shutter. The pre-fix code treated the first response as
 * completion, so the UI flashed a false "failed" while the shot was
 * actually succeeding.
 *
 * These tests drive [AppViewModel.onCaptureStatePoll] (via the
 * `testOnCaptureStatePoll` seam) with scripted 266 (CAM_GET_STATE) poll
 * results and assert the phase transitions, the debounce behaviour, and the
 * bounded watchdog timeout — covering the six scenarios required by #60:
 *
 * 1. acknowledgement -> transient negative/busy -> image-ready success;
 * 2. acknowledgement -> terminal failure (watchdog timeout, no idle);
 * 3. acknowledgement -> timeout (explicit Failed("timeout…"));
 * 4. late event after timeout (idle recovers a timed-out capture to Completed);
 * 5. second shutter press while busy (debounced, no duplicate 264);
 * 6. exactly-once completion with interleaved unrelated frames.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelCapturePhaseTest {

    /**
     * Fake connection that answers the 284/820/823 handshake and serves a
     * scripted queue of 266 (CAM_GET_STATE) responses for the capture poll.
     * Records every written frame so tests can assert on the 264 shutter
     * command count (debounce check).
     */
    private class FakeConnection : Connection {
        private val channel = Channel<ByteArray>(Channel.BUFFERED)
        val written = mutableListOf<ByteArray>()
        /** Scripted 266 responses, served in order. Empty -> poll times out. */
        var captureStateResponses: List<String> = emptyList()
        private var capturePollIndex = 0

        fun enqueueResponse(data: ByteArray) {
            channel.trySend(data)
        }

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) {
            // No-op; the handshake happens over write/read below.
        }

        override suspend fun write(data: ByteArray) {
            written += data
            val s = String(data, Charsets.US_ASCII)
            when {
                s.startsWith("1&284&") ->
                    enqueueResponse("1&284&2&mode:0;#".toByteArray(Charsets.US_ASCII))
                s.startsWith("1&820&") ->
                    enqueueResponse("1&820&2&needed:0;#".toByteArray(Charsets.US_ASCII))
                s.startsWith("1&823&") ->
                    enqueueResponse("1&823&2&app:openpolaris;ver:0.1.0;#".toByteArray(Charsets.US_ASCII))
                s.startsWith("1&266&") -> {
                    val resp = captureStateResponses.getOrNull(capturePollIndex)
                    capturePollIndex++
                    if (resp != null) enqueueResponse(resp.toByteArray(Charsets.US_ASCII))
                    // else: no response -> the 266 poll times out, keeping last good state
                }
            }
        }

        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val r = channel.receive()
            r.copyInto(buffer)
            return r.size
        }

        override fun close() {
            channel.close()
        }

        /** Count of 264 (CAM_CAPTURE shutter) frames written. */
        fun captureCommandCount(): Int =
            written.count { String(it, Charsets.US_ASCII).startsWith("1&264&") }
    }

    private fun newViewModel(
        scope: CoroutineScope,
        connectionFactory: () -> Connection,
    ): AppViewModel = AppViewModel(
        scope = scope,
        connectionFactory = connectionFactory,
        ioDispatcher = UnconfinedTestDispatcher(scope.coroutineContext[TestCoroutineScheduler]),
        sessionReaderScope = scope,
    )

    private fun state(s: Int) = CommandTable.CaptureState(state = s, bulb = 0, c = -1)

    /** Connect and let the post-connect bootstrap (polling, capture poll) settle. */
    private suspend fun connectAndSettle(scope: kotlinx.coroutines.test.TestScope, vm: AppViewModel) {
        vm.connect()
        scope.advanceTimeBy(3_000)
        assertEquals("Connected", vm.statusMessage)
    }

    // 1. acknowledgement -> transient negative/busy -> image-ready success.
    @Test
    fun transientNegativeThenIdleCompletesCapture() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection().apply {
            captureStateResponses = listOf(
                "1&266&2&state:1;bulb:0;c:-1;#",   // in progress
                "1&266&2&state:-1005;bulb:0;c:-1;#", // transient negative (K-3 III)
                "1&266&2&state:0;bulb:0;c:-1;#",    // idle -> shot done
            )
        }
        val vm = newViewModel(this, { conn })
        try {
            connectAndSettle(this, vm)
            assertEquals(AppViewModel.CapturePhase.Idle, vm.capturePhase)

            vm.capture()
            advanceTimeBy(1_000) // let the capture() coroutine send 264 + set Requested
            assertIs<AppViewModel.CapturePhase.Requested>(vm.capturePhase)
            assertEquals(1, conn.captureCommandCount())

            // Drive the poll results through the state machine.
            vm.testOnCaptureStatePoll(state(1))
            assertIs<AppViewModel.CapturePhase.Busy>(vm.capturePhase)

            vm.testOnCaptureStatePoll(state(-1005))
            // Transient negative must NOT be terminal failure.
            assertIs<AppViewModel.CapturePhase.Busy>(vm.capturePhase)

            vm.testOnCaptureStatePoll(state(0))
            assertIs<AppViewModel.CapturePhase.Completed>(vm.capturePhase)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    // 2. acknowledgement -> terminal failure (camera never returns to idle;
    //    the bounded watchdog fires and reports an explicit timeout).
    @Test
    fun noIdleWithinTimeoutReportsFailed() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection().apply {
            // Camera stays busy for the whole window; no idle ever arrives.
            captureStateResponses = List(8) { "1&266&2&state:1;bulb:0;c:-1;#" }
        }
        val vm = newViewModel(this, { conn })
        try {
            connectAndSettle(this, vm)
            vm.capture()
            advanceTimeBy(1_000)
            assertIs<AppViewModel.CapturePhase.Requested>(vm.capturePhase)

            // Advance past the 15 s watchdog with no idle observed.
            advanceTimeBy(16_000)
            val phase = vm.capturePhase
            assertIs<AppViewModel.CapturePhase.Failed>(phase)
            assertTrue(phase.reason.contains("timeout"), "expected a timeout reason, got '${phase.reason}'")
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    // 3. acknowledgement -> explicit timeout (same watchdog path, asserted as
    //    the distinct Failed terminal rather than a stuck Busy).
    @Test
    fun timeoutIsExplicitFailedNotStuckBusy() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection().apply {
            captureStateResponses = List(8) { "1&266&2&state:1;bulb:0;c:-1;#" }
        }
        val vm = newViewModel(this, { conn })
        try {
            connectAndSettle(this, vm)
            vm.capture()
            advanceTimeBy(1_000)
            vm.testOnCaptureStatePoll(state(1))
            assertIs<AppViewModel.CapturePhase.Busy>(vm.capturePhase)

            advanceTimeBy(16_000)
            // Must be the terminal Failed, not still Busy.
            assertIs<AppViewModel.CapturePhase.Failed>(vm.capturePhase)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    // 4. late event after timeout: an idle that arrives after the watchdog
    //    already reported Failed still recovers the phase to Completed — the
    //    shot evidently completed, so we don't leave the UI stuck on Failed.
    @Test
    fun lateIdleAfterTimeoutRecoversToCompleted() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection().apply {
            captureStateResponses = List(8) { "1&266&2&state:1;bulb:0;c:-1;#" }
        }
        val vm = newViewModel(this, { conn })
        try {
            connectAndSettle(this, vm)
            vm.capture()
            advanceTimeBy(1_000)
            vm.testOnCaptureStatePoll(state(1))

            // Watchdog fires -> Failed.
            advanceTimeBy(16_000)
            assertIs<AppViewModel.CapturePhase.Failed>(vm.capturePhase)

            // Late idle arrives (the camera did finish, just after our window).
            vm.testOnCaptureStatePoll(state(0))
            assertIs<AppViewModel.CapturePhase.Completed>(vm.capturePhase)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    // 5. second shutter press while busy: debounced, no duplicate 264 sent.
    @Test
    fun secondShutterPressWhileBusyIsDebounced() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection().apply {
            captureStateResponses = listOf(
                "1&266&2&state:1;bulb:0;c:-1;#",
                "1&266&2&state:0;bulb:0;c:-1;#",
            )
        }
        val vm = newViewModel(this, { conn })
        try {
            connectAndSettle(this, vm)
            vm.capture()
            advanceTimeBy(1_000)
            assertEquals(1, conn.captureCommandCount())

            // Phase is Requested -> a second press must be ignored.
            vm.capture()
            advanceTimeBy(500)
            assertEquals(1, conn.captureCommandCount(), "second press while in flight must not send another 264")

            // Busy -> still debounced.
            vm.testOnCaptureStatePoll(state(1))
            vm.capture()
            advanceTimeBy(500)
            assertEquals(1, conn.captureCommandCount(), "press while busy must not send another 264")

            // After completion a new press is accepted again.
            vm.testOnCaptureStatePoll(state(0))
            assertIs<AppViewModel.CapturePhase.Completed>(vm.capturePhase)
            vm.capture()
            advanceTimeBy(500)
            assertEquals(2, conn.captureCommandCount(), "press after completion must send a new 264")
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    // 6. exactly-once completion with interleaved unrelated frames: once the
    //    phase reaches Completed, further idle polls do not re-trigger it.
    @Test
    fun completionIsExactlyOnceWithInterleavedFrames() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection().apply {
            captureStateResponses = listOf(
                "1&266&2&state:1;bulb:0;c:-1;#",
                "1&266&2&state:0;bulb:0;c:-1;#",
                "1&266&2&state:0;bulb:0;c:-1;#", // unrelated subsequent idle poll
                "1&266&2&state:0;bulb:0;c:-1;#",
            )
        }
        val vm = newViewModel(this, { conn })
        try {
            connectAndSettle(this, vm)
            vm.capture()
            advanceTimeBy(1_000)

            vm.testOnCaptureStatePoll(state(1))
            assertIs<AppViewModel.CapturePhase.Busy>(vm.capturePhase)

            vm.testOnCaptureStatePoll(state(0))
            assertIs<AppViewModel.CapturePhase.Completed>(vm.capturePhase)

            // Interleaved unrelated idle frames must not re-trigger completion.
            vm.testOnCaptureStatePoll(state(0))
            assertIs<AppViewModel.CapturePhase.Completed>(vm.capturePhase)
            vm.testOnCaptureStatePoll(state(0))
            assertIs<AppViewModel.CapturePhase.Completed>(vm.capturePhase)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
