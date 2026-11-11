package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.PreviewTransport
import dev.openpolaris.core.domain.ObservationProgrammeExecutor
import dev.openpolaris.core.domain.ObservationProgrammePlanner
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Deterministic regression coverage for issue #90's event-correlated capture. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelCapturePhaseTest {
    private class FakeConnection : Connection {
        private val channel = Channel<ByteArray>(Channel.BUFFERED)
        val written = mutableListOf<String>()
        var cameraFrame: String? = "1&286&2&manufacturer:Pentax;model:K-3 III;state:1;storage:128;photoFormat:1;#"
        var photoRecordStartFrame: String? = null
        var photoRecordStopFrame: String? = null
        var shutterFrame: String? = null
        var respondToShutter: Boolean = true
        var shutterIndex: Int = 0

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) {
            val wire = String(data, Charsets.US_ASCII)
            written += wire
            val response = when {
                wire.startsWith("1&284&") -> "1&284&2&mode:0;#"
                wire.startsWith("1&286&") -> cameraFrame
                wire.startsWith("1&268&") -> if (respondToShutter) {
                    shutterFrame ?: "1&268&2&V:" + shutterIndex + ";R:M,B;RD:1;#"
                } else {
                    null
                }
                wire.startsWith("1&264&2&state:1;") -> photoRecordStartFrame
                wire.startsWith("1&264&2&state:0;") -> photoRecordStopFrame
                wire.startsWith("1&820&") -> "1&820&2&needed:0;#"
                wire.startsWith("1&823&") -> "1&823&2&app:openpolaris;ver:test;#"
                wire.startsWith("1&770&") -> "1&770&2&type:1;page:2;count:2;id:11;name:SP_0011.dng;prot:0;id:12;name:SP_0012.jpg;prot:1;#"
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
    fun cameraAttachmentIsReadOnceOnConnectNotEveryFiveSeconds() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            assertEquals(1, conn.count(286), "connect may take one identity snapshot")
            advanceTimeBy(20_000)
            assertEquals(1, conn.count(286), "OpenPolaris must not keep querying camera state every five seconds")
            vm.refreshCameraAttachment()
            runCurrent()
            assertEquals(2, conn.count(286), "refresh is explicit and bounded")
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }

    @Test
    fun fileListUsesSelectedCategoryAndPage() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            vm.setFileType(1)
            vm.setFilePage(2)
            vm.refreshFileList()
            runCurrent()

            assertTrue(conn.written.any { it.contains("type:1;page:2;") })
            assertEquals(2, vm.fileList?.files?.size)
            assertEquals("SP_0011.dng", vm.fileList?.files?.first()?.name)
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }

    @Test
    fun unqualifiedFocusAndVideoRemainReadOnly() = runTest(UnconfinedTestDispatcher()) {
        FeatureFlags.reset()
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            vm.adjustFocusNear()
            vm.adjustFocusFar(fast = true)
            vm.setVideoRecording(true)
            runCurrent()

            assertEquals(0, conn.count(311), "unqualified focus must not send manual-focus adjustments")
            assertEquals(0, conn.count(263), "unqualified video must not send record control")
            assertTrue(vm.statusMessage.contains("read-only"))
        } finally {
            FeatureFlags.reset()
            vm.disconnect(); vm.preview.shutdown()
        }
    }

    @Test
    fun unknownCameraStatusBlocksCaptureAdmission() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection().apply { cameraFrame = null }
        val vm = vm(this, conn)
        try {
            connect(this, vm)
            assertEquals(null, vm.cameraAttachment)

            vm.capture()
            assertEquals(0, conn.count(264), "unknown camera status must not reach the shutter command")
            assertTrue(vm.statusMessage.contains("status not verified"))
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }

    @Test
    fun capturePreflightRefusesCameraReadinessErrorWithoutSending264() =
        runTest(UnconfinedTestDispatcher()) {
            val conn = FakeConnection().apply {
                shutterFrame = "1&268&2&ret:-2110;RD:0;V:;R:;#"
            }
            val vm = vm(this, conn)
            try {
                connect(this, vm)
                vm.capture()
                advanceTimeBy(100)
                runCurrent()

                assertEquals(0, conn.count(264), "-2110 readiness failure must block the shutter")
                assertIs<AppViewModel.CapturePhase.Failed>(vm.capturePhase)
                assertTrue(vm.statusMessage.contains("-2110"), vm.statusMessage)
                assertTrue(vm.statusMessage.contains("reconnect"), vm.statusMessage)
            } finally {
                vm.disconnect()
                vm.preview.shutdown()
            }
        }

    @Test
    fun cameraReadinessTimeoutRequiresReconnectBeforeSuccessfulRecoveryCapture() =
        runTest(UnconfinedTestDispatcher()) {
            val failedConnection = FakeConnection().apply { respondToShutter = false }
            val failedVm = vm(this, failedConnection)
            connect(this, failedVm)
            failedVm.capture()
            advanceTimeBy(5_000)
            runCurrent()

            assertEquals(0, failedConnection.count(264))
            assertIs<AppViewModel.CapturePhase.Failed>(failedVm.capturePhase)
            assertTrue(failedVm.statusMessage.contains("timed_out"), failedVm.statusMessage)
            failedVm.disconnect()
            failedVm.preview.shutdown()

            val recoveredConnection = FakeConnection().apply {
                photoRecordStartFrame = "1&264&2&state:1;bulb:0;c:-1;#"
            }
            val recoveredVm = vm(this, recoveredConnection)
            try {
                connect(this, recoveredVm)
                recoveredVm.capture()
                runCurrent()
                recoveredVm.testOnCaptureFrame(frame("264@state:4;bulb:0;c:-1;#"))
                recoveredVm.testOnCaptureFrame(
                    frame("773@type:1;path:/app/sd/recovered/SP_0100.jpg;size:42;#"),
                )
                recoveredVm.testOnCaptureFrame(frame("264@state:0;bulb:0;c:-1;#"))
                assertIs<AppViewModel.CapturePhase.Completed>(recoveredVm.capturePhase)
                assertEquals("/app/sd/recovered/SP_0100.jpg", recoveredVm.lastCapturePublication?.path)
            } finally {
                recoveredVm.disconnect()
                recoveredVm.preview.shutdown()
            }
        }

    @Test
    fun capturePreflightRechecksCameraBeforeSendingTheShutter() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection()
        val vm = vm(this, conn)
        try {
            connect(this, vm)
            conn.cameraFrame = "1&286&2&manufacturer:none;model:none;state:-5;storage:0;photoFormat:0;#"

            vm.capture()
            runCurrent()

            assertEquals(0, conn.count(264), "fresh absence must block the shutter")
            assertIs<AppViewModel.CapturePhase.Failed>(vm.capturePhase)
            assertTrue(vm.statusMessage.contains("no camera detected"))
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun absentCameraBlocksCaptureAndSequenceAdmission() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection().apply {
            cameraFrame = "1&286&2&manufacturer:none;model:none;state:-5;storage:0;photoFormat:0;#"
        }
        val vm = vm(this, conn)
        try {
            connect(this, vm)
            assertEquals(false, vm.cameraAttachment?.isAttached)

            vm.capture()
            vm.captureBulb(1)
            assertEquals(0, conn.count(264), "no-camera capture must not reach the shutter command")
            assertTrue(vm.statusMessage.contains("No camera detected"))

            assertEquals(false, vm.startSequence(shotCount = 1, intervalMs = 0))
            assertEquals(0, conn.count(264), "no-camera sequence must not reach the shutter command")
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }


    @Test
    fun rejectedNativeBulbNeverExposesStopOrSendsASecond264() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection().apply {
            shutterIndex = 1
            photoRecordStartFrame = "1&264&2&state:0;bulb:0;c:-1;#"
        }
        val vm = vm(this, conn)
        try {
            connect(this, vm)
            vm.captureBulb(8)
            advanceTimeBy(3_000)
            runCurrent()

            assertIs<AppViewModel.CapturePhase.Failed>(vm.capturePhase)
            assertFalse(vm.nativeBulbCaptureActive)
            assertTrue(vm.statusMessage.contains("Bulb command rejected"))
            val writesAfterReject = conn.count(264)

            vm.stopBulb()
            runCurrent()

            assertEquals(writesAfterReject, conn.count(264), "a rejected Bulb command must not emit a stop edge")
            assertTrue(vm.statusMessage.contains("No native Bulb capture"))
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun nativeBulbStopIsAvailableOnlyAfterStateOneAcknowledgement() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection().apply {
            shutterIndex = 1
            photoRecordStartFrame = "1&264&2&state:1;bulb:8;c:-1;#"
            photoRecordStopFrame = "1&264&2&state:0;bulb:0;c:-1;#"
        }
        val vm = vm(this, conn)
        try {
            connect(this, vm)
            vm.captureBulb(8)
            advanceTimeBy(3_000)
            runCurrent()

            assertTrue(
                vm.capturePhase is AppViewModel.CapturePhase.Requested ||
                    vm.capturePhase is AppViewModel.CapturePhase.Busy,
            )
            assertTrue(vm.nativeBulbCaptureActive)
            val writesAfterStart = conn.count(264)

            vm.stopBulb()
            runCurrent()

            assertEquals(writesAfterStart + 1, conn.count(264), "accepted Bulb must use one matching 264/state:0 stop edge")
            assertTrue(vm.statusMessage.contains("Bulb stop sent"))

            vm.testOnCaptureFrame(frame("264@state:1;bulb:8;c:-1;#"))
            vm.testOnCaptureFrame(frame("264@state:4;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/bulb/SP_0088.dng;size:8;#"))
            vm.testOnCaptureFrame(frame("264@state:0;bulb:0;c:-1;#"))

            assertIs<AppViewModel.CapturePhase.Completed>(vm.capturePhase)
            assertFalse(vm.nativeBulbCaptureActive)
            assertEquals("/app/sd/bulb/SP_0088.dng", vm.lastCapturePublication?.path)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun nativeBulbCommandTimeoutIsFailureAndNeverBulbActive() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm(this, FakeConnection().apply { shutterIndex = 1 })
        try {
            connect(this, vm)
            vm.captureBulb(8)
            advanceTimeBy(100)
            runCurrent()
            assertFalse(vm.nativeBulbCaptureActive, "Bulb stop must stay hidden while 264 acknowledgement is pending")
            advanceTimeBy(11_000)
            runCurrent()

            assertIs<AppViewModel.CapturePhase.Failed>(vm.capturePhase)
            assertFalse(vm.nativeBulbCaptureActive)
            assertTrue(vm.statusMessage.contains("TIMEOUT"))
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun acceptedBulbWithoutLifecycleOrFileEvidenceBecomesOutcomeUnknown() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection().apply {
            shutterIndex = 1
            photoRecordStartFrame = "1&264&2&state:1;bulb:1;c:-1;#"
        }
        val vm = vm(this, conn)
        try {
            connect(this, vm)
            vm.captureBulb(1)
            advanceTimeBy(3_000)
            runCurrent()
            assertTrue(vm.nativeBulbCaptureActive)

            advanceTimeBy(17_000)
            runCurrent()

            assertIs<AppViewModel.CapturePhase.OutcomeUnknown>(vm.capturePhase)
            assertEquals(null, vm.lastCapturePublication)
            assertTrue(vm.statusMessage.contains("outcome unknown"))
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun demoHdrProgrammeRehearsesPlannerCaptureAndGalleryEvidence() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm(this, FakeConnection())
        try {
            vm.connectDemo(startPolling = false)
            runCurrent()
            val plan = ObservationProgrammePlanner.plan(
                ObservationProgrammePlanner.Request.Hdr(
                    baseExposureIndex = 20,
                    bracketOffsets = listOf(-1, 0, 1),
                    intervalMs = 0L,
                ),
            )

            assertTrue(vm.startObservationProgramme(plan))
            advanceTimeBy(8_000)
            runCurrent()

            val observed = vm.observationProgrammeState
            assertTrue(
                observed is ObservationProgrammeExecutor.State.Completed,
                "state=" + observed + "; status=" + vm.statusMessage,
            )
            val completed = observed as ObservationProgrammeExecutor.State.Completed
            assertEquals(3, completed.completedSteps)
            vm.refreshFileList()
            runCurrent()
            assertEquals(
                listOf("IMG_0001.JPG", "IMG_0002.JPG", "IMG_0003.JPG"),
                vm.fileList?.files?.map { it.name },
            )
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun demoFocusStackAndPanoramaProgrammesRehearseWithoutHardwareMetadataWrites() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm(this, FakeConnection())
        try {
            vm.connectDemo(startPolling = false)
            runCurrent()
            val focusStack = ObservationProgrammePlanner.plan(
                ObservationProgrammePlanner.Request.FocusStack(
                    startPosition = 10.0,
                    endPosition = 20.0,
                    stepCount = 5,
                    intervalMs = 0L,
                ),
            )
            assertTrue(vm.startObservationProgramme(focusStack))
            advanceTimeBy(20_000)
            runCurrent()
            val focusCompleted = assertIs<ObservationProgrammeExecutor.State.Completed>(vm.observationProgrammeState)
            assertEquals(5, focusCompleted.completedSteps)

            val panorama = ObservationProgrammePlanner.plan(
                ObservationProgrammePlanner.Request.Panorama(
                    rows = 2,
                    columns = 3,
                    yawStepDeg = 30.0,
                    pitchStepDeg = 10.0,
                    intervalMs = 0L,
                ),
            )
            assertTrue(vm.startObservationProgramme(panorama))
            advanceTimeBy(20_000)
            runCurrent()
            val panoramaCompleted = assertIs<ObservationProgrammeExecutor.State.Completed>(vm.observationProgrammeState)
            assertEquals(6, panoramaCompleted.completedSteps)
            vm.refreshFileList()
            runCurrent()
            assertEquals(
                (1..11).map { "IMG_" + it.toString().padStart(4, '0') + ".JPG" },
                vm.fileList?.files?.map { it.name },
            )
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun demoHdrProgrammeSupportsPauseAndResumeAtCaptureBoundary() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm(this, FakeConnection())
        try {
            vm.connectDemo(startPolling = false)
            runCurrent()
            val plan = ObservationProgrammePlanner.plan(
                ObservationProgrammePlanner.Request.Hdr(
                    baseExposureIndex = 20,
                    bracketOffsets = listOf(-1, 0, 1),
                    intervalMs = 1_000L,
                ),
            )

            assertTrue(vm.startObservationProgramme(plan))
            runCurrent()
            vm.pauseObservationProgramme()
            advanceTimeBy(2_000)
            runCurrent()

            val paused = vm.observationProgrammeState
            assertIs<ObservationProgrammeExecutor.State.Paused>(paused)
            assertEquals(1, paused.completedSteps)
            assertTrue(vm.resumeObservationProgramme())
            advanceTimeBy(5_000)
            runCurrent()
            val completed = assertIs<ObservationProgrammeExecutor.State.Completed>(vm.observationProgrammeState)
            assertEquals(3, completed.completedSteps)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun demoFocusAndVideoRehearsalUsesAcknowledgedSourceAlignedRoutes() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm(this, FakeConnection())
        try {
            vm.connectDemo(startPolling = false)
            runCurrent()
            assertTrue(vm.cameraAttachment?.isAttached == true)

            vm.adjustFocusNear()
            vm.adjustFocusFar(fast = true)
            runCurrent()
            assertTrue(vm.focusStatus?.contains("Far focus acknowledged") == true)

            vm.setVideoRecording(true)
            runCurrent()
            assertEquals(true, vm.videoRecording)

            vm.capture()
            assertTrue(vm.statusMessage.contains("Stop video"))

            vm.setVideoRecording(false)
            runCurrent()
            assertEquals(false, vm.videoRecording)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun enabledVideoCommandIsSingleFlightAndTimeoutLeavesStateUnknown() =
        runTest(UnconfinedTestDispatcher()) {
            FeatureFlags.reset()
            val conn = FakeConnection()
            val vm = vm(this, conn)
            try {
                connect(this, vm)
                FeatureFlags.enable("experimentalCamera")
                FeatureFlags.enable("cameraVideo")

                vm.setVideoRecording(true)
                runCurrent()

                assertTrue(vm.videoCommandInFlight)
                assertEquals(1, conn.count(263))
                vm.setVideoRecording(true)
                assertTrue(vm.statusMessage.contains("already in progress"))
                assertEquals(1, conn.count(263))

                advanceTimeBy(10_001)
                runCurrent()
                assertFalse(vm.videoCommandInFlight)
                assertEquals(null, vm.videoRecording)
                assertTrue(vm.statusMessage.contains("not confirmed"))
            } finally {
                FeatureFlags.reset()
                vm.disconnect()
                vm.preview.shutdown()
            }
        }

    @Test
    fun demoManualAndBulbCaptureCompleteWithSimulatedLifecycleAndPublication() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm(this, FakeConnection())
        try {
            vm.connectDemo(startPolling = false)
            runCurrent()
            assertTrue(vm.cameraAttachment?.isAttached == true, "demo must expose a confirmed camera")
            assertEquals(1, vm.camera.shutterIndex, "demo should read back the current B mode")
            assertEquals(listOf("M", "B"), vm.camera.shutterOptions)

            vm.setShutter(0)
            advanceTimeBy(200)
            runCurrent()
            assertEquals(0, vm.camera.shutterIndex, "demo should verify a switch to M")
            assertTrue(vm.statusMessage.contains("Shutter verified"))

            vm.capture()
            advanceTimeBy(850)
            runCurrent()

            assertIs<AppViewModel.CapturePhase.Completed>(vm.capturePhase)
            assertEquals("/demo/DCIM/IMG_0001.JPG", vm.lastCapturePublication?.path)
            assertEquals(12_345L, vm.lastCapturePublication?.sizeBytes)
            assertEquals(
                listOf(
                    AppViewModel.CaptureTimelineStage.REQUESTED,
                    AppViewModel.CaptureTimelineStage.ACTIVE,
                    AppViewModel.CaptureTimelineStage.PUBLISHED,
                    AppViewModel.CaptureTimelineStage.CONFIRMED,
                ),
                vm.captureTimeline.map { it.stage },
            )

            vm.setShutter(1)
            advanceTimeBy(200)
            runCurrent()
            assertEquals(1, vm.camera.shutterIndex, "demo should verify a switch back to B")

            vm.captureBulb(3)
            advanceTimeBy(850)
            runCurrent()
            assertIs<AppViewModel.CapturePhase.Completed>(vm.capturePhase)
            assertEquals("/demo/DCIM/IMG_0002.JPG", vm.lastCapturePublication?.path)

            vm.refreshFileList()
            vm.refreshSdStatus()
            runCurrent()
            assertEquals(
                listOf("IMG_0001.JPG", "IMG_0002.JPG"),
                vm.fileList?.files?.map { it.name },
            )
            assertTrue(vm.sdStatus?.present == true)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun disconnectCancelsQueuedCaptureDispatch() = runTest {
        val conn = FakeConnection()
        val vm = vm(this, conn)
        try {
            connect(this, vm)
            vm.capture()
            vm.disconnect()
            runCurrent()

            assertIs<AppViewModel.CapturePhase.Idle>(vm.capturePhase)
            assertEquals(0, conn.count(264), "a queued capture must not reach the shutter after disconnect")
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun lifecycleAndPositiveFileEventCompleteCapture() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            vm.capture(); advanceTimeBy(3_000)
            val writesBeforeIncorrectBulbStop = conn.count(264)
            vm.stopBulb()
            runCurrent()
            assertEquals(
                writesBeforeIncorrectBulbStop,
                conn.count(264),
                "ordinary capture must never send the native Bulb stop edge",
            )
            assertTrue(vm.statusMessage.contains("native Bulb"))
            vm.testOnCaptureFrame(frame("264@state:1;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("264@state:4;bulb:0;c:-1;#"))
            assertIs<AppViewModel.CapturePhase.Busy>(vm.capturePhase)
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/SP_0042.dng;size:28198130;#"))
            vm.testOnCaptureFrame(frame("264@state:0;bulb:0;c:-1;#"))
            assertIs<AppViewModel.CapturePhase.Completed>(vm.capturePhase)
            assertEquals("/app/sd/normal/SP_0042.dng", vm.lastCapturePublication?.path)
            assertEquals(28198130L, vm.lastCapturePublication?.sizeBytes)
            assertEquals(
                listOf(
                    AppViewModel.CaptureTimelineStage.REQUESTED,
                    AppViewModel.CaptureTimelineStage.ACTIVE,
                    AppViewModel.CaptureTimelineStage.PUBLISHED,
                    AppViewModel.CaptureTimelineStage.CONFIRMED,
                ),
                vm.captureTimeline.map { it.stage },
            )
            assertEquals(1, conn.count(264))
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }

    @Test
    fun ordinaryCameraFailureDoesNotBecomeBulbActiveOrAcceptLaterStopFrame() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            vm.capture(); advanceTimeBy(3_000)
            vm.testOnCaptureFrame(frame("264@state:-110;bulb:0;c:-1;#"))

            assertIs<AppViewModel.CapturePhase.Failed>(vm.capturePhase)
            assertFalse(vm.nativeBulbCaptureActive)
            assertTrue(vm.statusMessage.contains("state -110"), vm.statusMessage)
            val writesAfterFailure = conn.count(264)

            vm.stopBulb()
            vm.testOnCaptureFrame(frame("264@state:0;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/stale.dng;size:42;#"))
            vm.testOnCaptureFrame(frame("264@state:0;bulb:0;c:-1;#"))
            runCurrent()

            assertEquals(writesAfterFailure, conn.count(264), "ordinary failure must not emit a generic Bulb stop")
            assertIs<AppViewModel.CapturePhase.Failed>(vm.capturePhase)
            assertEquals(null, vm.lastCapturePublication)
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }

    @Test
    fun transientNegativeCaptureStateDoesNotPreventLaterPositiveEvidence() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            vm.capture(); advanceTimeBy(3_000)
            vm.testOnCaptureFrame(frame("264@state:-1005;bulb:0;c:-1;#"))
            assertIs<AppViewModel.CapturePhase.Busy>(vm.capturePhase)
            assertFalse(vm.nativeBulbCaptureActive)

            vm.testOnCaptureFrame(frame("264@state:4;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/transient.dng;size:42;#"))
            vm.testOnCaptureFrame(frame("264@state:0;bulb:0;c:-1;#"))

            assertIs<AppViewModel.CapturePhase.Completed>(vm.capturePhase)
            assertEquals("/app/sd/normal/transient.dng", vm.lastCapturePublication?.path)
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }

    @Test
    fun fileEventWithoutLifecycleCannotCompleteCapture() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            vm.capture(); advanceTimeBy(3_000)
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/old.dng;size:42;#"))
            vm.testOnCaptureFrame(frame("264@state:0;bulb:0;c:-1;#"))
            assertIs<AppViewModel.CapturePhase.Requested>(vm.capturePhase)
            assertEquals(null, vm.lastCapturePublication)
            assertTrue(vm.captureTimeline.none { it.stage == AppViewModel.CaptureTimelineStage.PUBLISHED })
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
            vm.testOnCaptureFrame(frame("264@state:0;bulb:0;c:-1;#"))
            assertIs<AppViewModel.CapturePhase.Requested>(vm.capturePhase)
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }

    @Test
    fun timeoutIsUnknownAndBlocksSecondShutter() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            val attachmentPollsBeforeCapture = conn.count(286)
            vm.capture(); advanceTimeBy(20_000)
            assertIs<AppViewModel.CapturePhase.OutcomeUnknown>(vm.capturePhase)
            assertFalse(vm.nativeBulbCaptureActive)
            assertEquals(attachmentPollsBeforeCapture + 1, conn.count(286), "only the one-shot readiness preflight may read camera attachment")
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
            advanceTimeBy(850) // let the shared poll window drain before shutter admission
            runCurrent()
            assertEquals(1, conn.count(264), "first shutter should be in flight")
            vm.testOnCaptureFrame(frame("264@state:1;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/SP_0001.dng;size:1;#"))
            vm.testOnCaptureFrame(frame("264@state:0;bulb:0;c:-1;#"))
            assertIs<dev.openpolaris.core.domain.IntervalometerController.State.Running>(vm.sequenceState)

            // Confirming shot 1 releases the engine, which then waits the 1 s
            // interval before firing shot 2.
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(2, conn.count(264), "second shutter should be in flight")
            vm.testOnCaptureFrame(frame("264@state:1;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/SP_0002.dng;size:1;#"))
            vm.testOnCaptureFrame(frame("264@state:0;bulb:0;c:-1;#"))

            // Confirming shot 2 releases the engine, which waits the interval
            // before firing the final shot 3.
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(3, conn.count(264), "third shutter should be in flight")
            vm.testOnCaptureFrame(frame("264@state:1;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/SP_0003.dng;size:1;#"))
            vm.testOnCaptureFrame(frame("264@state:0;bulb:0;c:-1;#"))

            runCurrent()
            val s = vm.sequenceState
            assertIs<dev.openpolaris.core.domain.IntervalometerController.State.Completed>(s)
            assertEquals(3, s.completedShots)
            assertEquals(3, conn.count(264), "exactly one shutter per shot")
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }

    @Test
    fun sequenceOutcomeUnknownKeepsCameraWorkQuiescedAndNeverSendsNextShutter() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            assertTrue(vm.startSequence(shotCount = 2, intervalMs = 100))
            runCurrent()
            advanceTimeBy(850)
            runCurrent()
            assertEquals(1, conn.count(264), "only the first shutter may be sent before positive completion")

            advanceTimeBy(300_000)
            runCurrent()
            assertIs<dev.openpolaris.core.domain.IntervalometerController.State.OutcomeUnknown>(vm.sequenceState)
            assertIs<AppViewModel.CapturePhase.OutcomeUnknown>(vm.capturePhase)
            assertEquals(1, conn.count(264), "timeout must not automatically trigger a second exposure")
            val pollsAtTimeout = conn.count(286)
            advanceTimeBy(15_000)
            assertEquals(pollsAtTimeout, conn.count(286), "286 polling must remain stopped until session recovery")
            assertTrue(!vm.startSequence(shotCount = 1, intervalMs = 0), "an unknown shutter must block another sequence")
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
            advanceTimeBy(850)
            runCurrent()
            vm.testOnCaptureFrame(frame("264@state:1;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/SP_0001.dng;size:1;#"))
            vm.testOnCaptureFrame(frame("264@state:0;bulb:0;c:-1;#"))
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
            vm.testOnCaptureFrame(frame("264@state:0;bulb:0;c:-1;#"))

            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(3, conn.count(264), "final shutter should be in flight")
            vm.testOnCaptureFrame(frame("264@state:1;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/SP_0003.dng;size:1;#"))
            vm.testOnCaptureFrame(frame("264@state:0;bulb:0;c:-1;#"))

            runCurrent()
            val s = vm.sequenceState
            assertIs<dev.openpolaris.core.domain.IntervalometerController.State.Completed>(s)
            assertEquals(3, s.completedShots)
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }

    @Test
    fun qualifiedLapseProgrammeDrivesCorrelatedCapturesThroughTheSharedOwner() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            val plan = dev.openpolaris.core.domain.ObservationProgrammePlanner.plan(
                dev.openpolaris.core.domain.ObservationProgrammePlanner.Request.Lapse(
                    shotCount = 2,
                    intervalMs = 1_000L,
                ),
            )
            assertTrue(vm.startObservationProgramme(plan))
            runCurrent()
            advanceTimeBy(850)
            runCurrent()
            assertEquals(1, conn.count(264), "programme must issue one shutter for its first step")
            vm.testOnCaptureFrame(frame("264@state:1;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/PROGRAMME_0001.dng;size:1;#"))
            vm.testOnCaptureFrame(frame("264@state:0;bulb:0;c:-1;#"))
            runCurrent()

            advanceTimeBy(1_000)
            runCurrent()
            advanceTimeBy(850)
            runCurrent()
            assertEquals(2, conn.count(264), "programme must wait for evidence before the next shutter")
            vm.testOnCaptureFrame(frame("264@state:1;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/PROGRAMME_0002.dng;size:1;#"))
            vm.testOnCaptureFrame(frame("264@state:0;bulb:0;c:-1;#"))
            runCurrent()

            assertIs<dev.openpolaris.core.domain.ObservationProgrammeExecutor.State.Completed>(vm.observationProgrammeState)
            assertEquals(2, conn.count(264), "programme must not duplicate a confirmed step")
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }

    @Test
    fun unqualifiedProgrammeKindsRemainPreviewOnly() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            val hdr = dev.openpolaris.core.domain.ObservationProgrammePlanner.plan(
                dev.openpolaris.core.domain.ObservationProgrammePlanner.Request.Hdr(
                    baseExposureIndex = 20,
                    bracketOffsets = listOf(-2, 0, 2),
                    intervalMs = 1_000L,
                ),
            )
            assertEquals(false, vm.startObservationProgramme(hdr))
            assertEquals(0, conn.count(264), "unqualified HDR must not send a shutter")
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }


    @Test
    fun unknownSingleCaptureBlocksAProgrammeUntilSessionRecovery() = runTest(UnconfinedTestDispatcher()) {
        val conn = FakeConnection(); val vm = vm(this, conn)
        try {
            connect(this, vm)
            vm.capture()
            advanceTimeBy(20_000)
            val plan = dev.openpolaris.core.domain.ObservationProgrammePlanner.plan(
                dev.openpolaris.core.domain.ObservationProgrammePlanner.Request.Lapse(1, 0L),
            )
            assertEquals(false, vm.startObservationProgramme(plan))
            assertEquals(1, conn.count(264), "unknown capture must block a programme retry")
        } finally { vm.disconnect(); vm.preview.shutdown() }
    }

}
