package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.PreviewTransport
import dev.openpolaris.core.protocol.ResponseParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Deterministic regression coverage for issue #90's event-correlated capture. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelCapturePhaseTest {
    private class FakeConnection : Connection {
        private val channel = Channel<ByteArray>(Channel.BUFFERED)
        val written = mutableListOf<String>()

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) {
            val wire = String(data, Charsets.US_ASCII)
            written += wire
            val response = when {
                wire.startsWith("1&284&") -> "1&284&2&mode:0;#"
                wire.startsWith("1&820&") -> "1&820&2&needed:0;#"
                wire.startsWith("1&823&") -> "1&823&2&app:openpolaris;ver:test;#"
                else -> null
            }
            response?.let { channel.trySend(it.toByteArray(Charsets.US_ASCII)) }
        }
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val bytes = channel.receive()
            bytes.copyInto(buffer)
            return bytes.size
        }
        override fun close() { channel.close() }
        fun count(code: Int) = written.count { it.startsWith("1&$code&") }
    }

    private fun vm(scope: CoroutineScope, connection: FakeConnection) = AppViewModel(
        scope = scope,
        connectionFactory = { connection },
        ioDispatcher = UnconfinedTestDispatcher(scope.coroutineContext[TestCoroutineScheduler]),
        sessionReaderScope = scope,
        previewTransportFactory = { _, _ -> object : PreviewTransport {
            override fun start(host: String, port: Int, path: String) = Unit
            override fun stop() = Unit
        } },
        previewIoDispatcher = UnconfinedTestDispatcher(scope.coroutineContext[TestCoroutineScheduler]),
    )

    private val parser = ResponseParser()
    private fun frame(wire: String) = requireNotNull(parser.parseFrame(wire))

    private suspend fun connect(scope: kotlinx.coroutines.test.TestScope, vm: AppViewModel) {
        vm.connect()
        scope.advanceTimeBy(3_000)
        assertEquals("Connected", vm.statusMessage)
    }

    @Test
    fun liveConnectionNeverPollsWhiteBalanceCode266() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            advanceTimeBy(10_000)
            assertEquals(0, conn.count(266), "266 is WB configuration, not capture state")
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }

    @Test
    fun lifecycleAndPositiveFileEventCompleteCapture() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            vm.capture(); advanceTimeBy(3_000)
            vm.testOnCaptureFrame(frame("264@state:1;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("264@state:4;bulb:0;c:-1;#"))
            assertIs<AppViewModel.CapturePhase.Busy>(vm.capturePhase)
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/SP_0042.dng;size:28198130;#"))
            assertIs<AppViewModel.CapturePhase.Completed>(vm.capturePhase)
            assertEquals(1, conn.count(264))
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }

    @Test
    fun fileEventWithoutLifecycleCannotCompleteCapture() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            vm.capture(); advanceTimeBy(3_000)
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/old.dng;size:42;#"))
            assertIs<AppViewModel.CapturePhase.Requested>(vm.capturePhase)
            advanceTimeBy(16_000)
            assertIs<AppViewModel.CapturePhase.OutcomeUnknown>(vm.capturePhase)
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }

    @Test
    fun malformedAndUnrelatedFramesCannotCompleteCapture() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            vm.capture(); advanceTimeBy(3_000)
            vm.testOnCaptureFrame(frame("266@RD:0;V:0;R:Automatic,Daylight;#"))
            vm.testOnCaptureFrame(frame("284@state:0;#"))
            vm.testOnCaptureFrame(frame("517@yaw:1;pitch:2;#"))
            vm.testOnCaptureFrame(frame("773@path:;size:0;#"))
            assertIs<AppViewModel.CapturePhase.Requested>(vm.capturePhase)
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }

    @Test
    fun timeoutIsUnknownAndBlocksSecondShutter() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            vm.capture(); advanceTimeBy(20_000)
            assertIs<AppViewModel.CapturePhase.OutcomeUnknown>(vm.capturePhase)
            vm.capture(); advanceTimeBy(100)
            assertEquals(1, conn.count(264), "unknown outcome requires operator recovery")
            assertTrue(vm.statusMessage.contains("in progress"))
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }

    /**
     * §6 regression: a capture sequence drives N correlated captures. The engine
     * fires one 264 shutter per shot; each shot is confirmed by the
     * capture-event observer correlating the 264 lifecycle + 773 file events,
     * which releases the engine's in-flight shutter so it advances to the next
     * shot. Verifies the full loop reaches Completed with exactly N captures.
     */
    @Test
    fun sequenceDrivesNCorrelatedCaptures() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            assertTrue(vm.startSequence(shotCount = 3, intervalMs = 1_000))

            // Shot 1: the engine fires the first shutter immediately (no pre-delay).
            runCurrent()
            assertEquals(1, conn.count(264), "first shutter should be in flight")
            vm.testOnCaptureFrame(frame("264@state:1;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/SP_0001.dng;size:1;#"))
            assertIs<dev.openpolaris.core.domain.IntervalometerController.State.Running>(vm.sequenceState)

            // Confirming shot 1 releases the engine, which then waits the 1 s
            // interval before firing shot 2.
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(2, conn.count(264), "second shutter should be in flight")
            vm.testOnCaptureFrame(frame("264@state:1;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/SP_0002.dng;size:1;#"))

            // Confirming shot 2 releases the engine, which waits the interval
            // before firing the final shot 3.
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(3, conn.count(264), "third shutter should be in flight")
            vm.testOnCaptureFrame(frame("264@state:1;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/SP_0003.dng;size:1;#"))

            runCurrent()
            val s = vm.sequenceState
            assertIs<dev.openpolaris.core.domain.IntervalometerController.State.Completed>(s)
            assertEquals(3, s.completedShots)
            assertEquals(3, conn.count(264), "exactly one shutter per shot")
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }

    /** §6: a paused sequence resumes from the next unshot index and completes. */
    @Test
    fun pausedSequenceResumesFromNextShot() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            assertTrue(vm.startSequence(shotCount = 3, intervalMs = 1_000))

            // Complete shot 1, then pause before the interval elapses.
            runCurrent()
            vm.testOnCaptureFrame(frame("264@state:1;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/SP_0001.dng;size:1;#"))
            vm.pauseSequence()
            assertIs<dev.openpolaris.core.domain.IntervalometerController.State.Paused>(vm.sequenceState)

            // Resume: the engine fires shot 2 immediately (no pre-delay on resume).
            vm.resumeSequence()
            runCurrent()
            assertEquals(2, conn.count(264), "resumed sequence should fire the next shutter")
            // Confirming shot 2 releases the engine, which then waits the 1 s
            // interval before firing the final shot 3.
            vm.testOnCaptureFrame(frame("264@state:1;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/SP_0002.dng;size:1;#"))

            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(3, conn.count(264), "final shutter should be in flight")
            vm.testOnCaptureFrame(frame("264@state:1;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/SP_0003.dng;size:1;#"))

            runCurrent()
            val s = vm.sequenceState
            assertIs<dev.openpolaris.core.domain.IntervalometerController.State.Completed>(s)
            assertEquals(3, s.completedShots)
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }
}
