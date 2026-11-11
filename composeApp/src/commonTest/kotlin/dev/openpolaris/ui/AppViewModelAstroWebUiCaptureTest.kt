package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiCaptureClient
import dev.openpolaris.core.domain.AstroWebUiCaptureHttpRequest
import dev.openpolaris.core.domain.AstroWebUiCaptureTransport
import dev.openpolaris.core.domain.AstroWebUiHttpResponse
import dev.openpolaris.core.domain.CameraController
import dev.openpolaris.core.domain.CameraReleaseMode
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.PreviewTransport
import dev.openpolaris.core.protocol.ResponseParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelAstroWebUiCaptureTest {
    private class FakeConnection : Connection {
        private val channel = Channel<ByteArray>(Channel.BUFFERED)

        var shutterFrame = "1&268&2&V:0;R:M,B;RD:1;#"

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit

        override suspend fun write(data: ByteArray) {
            val wire = String(data, Charsets.US_ASCII)
            val response = when {
                wire.startsWith("1&284&") -> "1&284&2&mode:0;#"
                wire.startsWith("1&286&") -> "1&286&2&manufacturer:Pentax;model:K-3 III;state:1;storage:128;photoFormat:1;#"
                wire.startsWith("1&268&") -> shutterFrame
                wire.startsWith("1&820&") -> "1&820&2&needed:0;#"
                wire.startsWith("1&823&") -> "1&823&2&app:openpolaris;ver:test;#"
                else -> null
            }
            response?.let { channel.trySend(it.encodeToByteArray()) }
        }

        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val bytes = channel.receive()
            bytes.copyInto(buffer)
            return bytes.size
        }

        override fun close() { channel.close() }
    }

    private class RecordingTransport : AstroWebUiCaptureTransport {
        val requests = mutableListOf<AstroWebUiCaptureHttpRequest>()

        override suspend fun post(request: AstroWebUiCaptureHttpRequest): AstroWebUiHttpResponse {
            requests += request
            return AstroWebUiHttpResponse(
                statusCode = 200,
                contentType = "application/json",
                body = "{\"ok\":true,\"cmd\":264}".encodeToByteArray(),
            )
        }
    }

    private class BlockingTransport(
        private val gate: CompletableDeferred<Unit>,
    ) : AstroWebUiCaptureTransport {
        var calls = 0

        override suspend fun post(request: AstroWebUiCaptureHttpRequest): AstroWebUiHttpResponse {
            calls++
            withContext(NonCancellable) { gate.await() }
            return AstroWebUiHttpResponse(
                statusCode = 200,
                contentType = "application/json",
                body = "{\"ok\":true,\"cmd\":264}".encodeToByteArray(),
            )
        }
    }

    private fun vm(
        scope: CoroutineScope,
        connection: FakeConnection,
        client: AstroWebUiCaptureClient,
    ) = AppViewModel(
        scope = scope,
        connectionFactory = { connection },
        astroWebUiCaptureClient = client,
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

    @AfterTest
    fun resetFlags() { FeatureFlags.reset() }

    @Test
    fun manualAstroWebUiCaptureUsesEvidenceGateForCompletion() = runTest(UnconfinedTestDispatcher()) {
        FeatureFlags.enable("astroWebUiCapture")
        val transport = RecordingTransport()
        val connection = FakeConnection()
        val vm = vm(this, connection, AstroWebUiCaptureClient(transport))
        try {
            connect(this, vm)
            vm.testSetCameraParams(CameraController.Params(shutterIndex = 0, shutterOptions = listOf("M")))

            vm.captureAstroWebUi(CameraReleaseMode.MANUAL)
            advanceTimeBy(851)
            runCurrent()
            assertTrue(transport.requests.isNotEmpty(), "no Astro M request: ${vm.statusMessage}")
            assertEquals("cmd=264&args=state%3A1%3Bbulb%3A0%3Bc%3A-1%3B&confirm=1", transport.requests.single().body)
            assertTrue(vm.astroWebUiCaptureActive)
            assertIs<AppViewModel.CapturePhase.Requested>(vm.capturePhase)
            assertTrue(vm.astroWebUiCaptureStatus.orEmpty().contains("waiting for camera lifecycle"))

            vm.testOnCaptureFrame(frame("264@state:1;bulb:0;c:-1;#"))
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/ASTRO_M_0001.dng;size:42;#"))
            vm.testOnCaptureFrame(frame("264@state:0;bulb:0;c:-1;#"))
            assertIs<AppViewModel.CapturePhase.Completed>(vm.capturePhase)
            assertEquals("/app/sd/normal/ASTRO_M_0001.dng", vm.lastCapturePublication?.path)
            assertEquals(false, vm.astroWebUiCaptureActive)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun disconnectDropsLateAstroWebUiCaptureResult() = runTest(UnconfinedTestDispatcher()) {
        FeatureFlags.enable("astroWebUiCapture")
        val gate = CompletableDeferred<Unit>()
        val transport = BlockingTransport(gate)
        val vm = vm(this, FakeConnection(), AstroWebUiCaptureClient(transport))
        try {
            connect(this, vm)
            vm.testSetCameraParams(CameraController.Params(shutterIndex = 0, shutterOptions = listOf("M")))

            vm.captureAstroWebUi(CameraReleaseMode.MANUAL)
            advanceTimeBy(851)
            runCurrent()
            assertEquals(1, transport.calls)

            vm.disconnect()
            gate.complete(Unit)
            runCurrent()

            assertEquals(AppViewModel.CapturePhase.Idle, vm.capturePhase)
            assertEquals(false, vm.astroWebUiCaptureActive)
            assertEquals(false, vm.astroWebUiCaptureDispatching)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun bulbAstroWebUiCaptureUsesBStopAndKeepsEvidencePending() = runTest(UnconfinedTestDispatcher()) {
        FeatureFlags.enable("astroWebUiCapture")
        val transport = RecordingTransport()
        val connection = FakeConnection().apply {
            shutterFrame = "1&268&2&V:1;R:M,B;RD:1;#"
        }
        val vm = vm(this, connection, AstroWebUiCaptureClient(transport))
        try {
            connect(this, vm)
            vm.testSetCameraParams(CameraController.Params(shutterIndex = 0, shutterOptions = listOf("B")))

            vm.captureAstroWebUi(CameraReleaseMode.BULB, bulbSeconds = 12)
            advanceTimeBy(851)
            runCurrent()
            assertTrue(transport.requests.isNotEmpty(), "no Astro B request: ${vm.statusMessage}")
            assertEquals("cmd=264&args=state%3A1%3Bbulb%3A12%3Bc%3A-1%3B&confirm=1", transport.requests.single().body)
            assertTrue(vm.astroWebUiBulbActive)

            vm.stopAstroWebUiBulb()
            runCurrent()
            assertEquals("cmd=264&args=state%3A0%3Bbulb%3A0%3Bc%3A-1%3B&confirm=1", transport.requests[1].body)
            assertTrue(vm.astroWebUiBulbStopQueued)
            vm.stopAstroWebUiBulb()
            runCurrent()
            assertEquals(2, transport.requests.size, "a second Bulb stop must not be posted")
            assertIs<AppViewModel.CapturePhase.Requested>(vm.capturePhase)

            vm.testOnCaptureFrame(frame("264@state:1;bulb:12;c:-1;#"))
            vm.testOnCaptureFrame(frame("773@type:1;path:/app/sd/normal/ASTRO_B_0001.dng;size:42;#"))
            vm.testOnCaptureFrame(frame("264@state:0;bulb:0;c:-1;#"))
            assertIs<AppViewModel.CapturePhase.Completed>(vm.capturePhase)
            assertEquals(false, vm.astroWebUiBulbActive)
            assertEquals(false, vm.astroWebUiBulbStopQueued)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
