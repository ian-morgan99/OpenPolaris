package dev.openpolaris.core.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewControllerTest {

    /**
     * Test double for [PreviewTransport]. Each test supplies a custom
     * `onStart` and `onStop` block; the controller drives them via the
     * real interface. Avoids reaching into the platform-specific
     * `createPreviewTransport` factory.
     */
    private class FakePreviewTransport(
        val onFrame: (ByteArray) -> Boolean,
        val onError: (Throwable) -> Unit,
        val onStart: (FakePreviewTransport) -> Unit,
        val onStop: (FakePreviewTransport) -> Unit,
    ) : PreviewTransport {
        override fun start(host: String, port: Int, path: String) = onStart(this)
        override fun stop() = onStop(this)
    }

    /**
     * Happy path: the transport emits three frames, the controller
     * forwards them all and transitions to [PreviewController.State.Streaming].
     */
    @Test
    fun publishesBytesAndReachesStreamingState() = runTest {
        val frames = listOf("frame-a".toByteArray(), "frame-b".toByteArray(), "frame-c".toByteArray())
        var consumed = 0
        val factory: ((ByteArray) -> Boolean, (Throwable) -> Unit) -> PreviewTransport =
            { onFrame, _ ->
                FakePreviewTransport(
                    onFrame = onFrame,
                    onError = {},
                    onStart = { t ->
                        for (f in frames) {
                            t.onFrame(f); consumed++
                        }
                    },
                    onStop = {},
                )
            }
        // Inject the test scheduler as the controller's dispatcher so the
        // launched transport hop advances in lockstep with the test, instead
        // of racing on the real `Dispatchers.Default` pool.
        val c = PreviewController(
            transportFactory = factory,
            parent = SupervisorJob(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        c.start("192.168.0.1")
        // With UnconfinedTestDispatcher, transport.start() and its onStart
        // callback have already run by the time start() returns.
        assertEquals(3, consumed, "all frames should have been forwarded")
        assertTrue(
            c.state.value is PreviewController.State.Streaming,
            "expected Streaming, was ${c.state.value}",
        )
        c.shutdown()
    }

    /**
     * Error from the transport must surface via [PreviewController.state].
     */
    @Test
    fun surfacesTransportError() = runTest {
        val factory: ((ByteArray) -> Boolean, (Throwable) -> Unit) -> PreviewTransport =
            { _, onError ->
                FakePreviewTransport(
                    onFrame = { _ -> true },
                    onError = onError,
                    onStart = { t -> t.onError(IllegalStateException("404")) },
                    onStop = {},
                )
            }
        // Same dispatcher injection as above: the launched `t.start(...)`
        // now runs in the test coroutine, so the `onError` callback fires
        // and updates the StateFlow before `c.start()` returns. This
        // removes the long-standing "expected Error, was Connecting"
        // failure caused by `Dispatchers.Default` contention in the full
        // test suite.
        val c = PreviewController(
            transportFactory = factory,
            parent = SupervisorJob(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        c.start("192.168.0.1")
        val s = c.state.value
        assertTrue(s is PreviewController.State.Error, "expected Error, was $s")
        assertEquals("404", (s as PreviewController.State.Error).message)
        c.shutdown()
    }

    /**
     * Calling [PreviewController.start] twice in a row must stop the
     * first transport before opening the second. Regression guard
     * against the "I now have two threads fighting over one port" bug.
     *
     * This test cannot use the UnconfinedTestDispatcher trick because its
     * `onStart` deliberately blocks the thread for 60s to prove the
     * controller can interrupt it. We stick with the real dispatcher
     * here, drive the test with real `Thread.sleep` calls, and accept
     * the real-time cost.
     */
    @Test
    fun restartStopsPriorTransport() = runTest {
        var firstStops = 0
        var firstStarts = 0
        var secondStarts = 0
        val factory: ((ByteArray) -> Boolean, (Throwable) -> Unit) -> PreviewTransport =
            { onFrame, _ ->
                FakePreviewTransport(
                    onFrame = onFrame,
                    onError = {},
                    onStart = { _ ->
                        val first = firstStarts == 0 && secondStarts == 0
                        if (first) firstStarts++ else secondStarts++
                        // Block forever; the controller must stop us on restart.
                        try { Thread.sleep(60_000) } catch (_: InterruptedException) {}
                    },
                    onStop = {
                        if (firstStarts == 1 && secondStarts == 0) firstStops++
                    },
                )
            }
        val c = PreviewController(transportFactory = factory, parent = SupervisorJob())
        c.start("10.0.0.1")
        Thread.sleep(50)
        c.start("10.0.0.2")
        Thread.sleep(50)
        assertEquals(1, firstStops, "first transport must be stopped on restart")
        c.shutdown()
    }
}
