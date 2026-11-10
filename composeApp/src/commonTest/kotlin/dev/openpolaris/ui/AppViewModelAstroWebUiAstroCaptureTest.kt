package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiAstroCaptureClient
import dev.openpolaris.core.domain.AstroWebUiAstroCaptureParameters
import dev.openpolaris.core.domain.AstroWebUiAstroCaptureResult
import dev.openpolaris.core.domain.AstroWebUiAstroCaptureState
import dev.openpolaris.core.domain.AstroWebUiProgramme
import dev.openpolaris.core.domain.AstroWebUiProgrammeClient
import dev.openpolaris.core.domain.AstroWebUiProgrammeResult
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MountSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelAstroWebUiAstroCaptureTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeAstroCaptureClient : AstroWebUiAstroCaptureClient {
        var calls = 0
        var lastHost: String? = null
        var lastPort: Int? = null
        var lastParameters: AstroWebUiAstroCaptureParameters? = null
        var releaseStart: CompletableDeferred<Unit>? = null

        override suspend fun start(
            host: String,
            port: Int,
            parameters: AstroWebUiAstroCaptureParameters,
        ): AstroWebUiAstroCaptureResult {
            calls++
            lastHost = host
            lastPort = port
            lastParameters = parameters
            releaseStart?.let { gate -> withContext(NonCancellable) { gate.await() } }
            return AstroWebUiAstroCaptureResult.Accepted()
        }
    }

    private class FakeProgrammeClient : AstroWebUiProgrammeClient {
        var calls = 0

        override suspend fun read(host: String, port: Int): AstroWebUiProgrammeResult {
            calls++
            return AstroWebUiProgrammeResult.Success(AstroWebUiProgramme("lapse", remaining = 2))
        }
    }

    @AfterTest
    fun resetFlags() = FeatureFlags.reset()

    private fun installSession(vm: AppViewModel, scope: kotlinx.coroutines.test.TestScope) {
        vm.testInstallSession(
            MountSession(
                connectionFactory = { NoopConnection() },
                host = "192.168.0.1",
                port = 9090,
                readerScope = scope,
            ),
        )
    }

    @Test
    fun inHeadCaptureRemainsBlockedByDefault() = runTest {
        val client = FakeAstroCaptureClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiAstroCaptureClient = client,
        )
        vm.startAstroWebUiAstroCapture(AstroWebUiAstroCaptureParameters(8))

        assertEquals(0, client.calls)
        assertIs<AstroWebUiAstroCaptureState.Failed>(vm.astroWebUiAstroCapture)
        vm.disconnect()
        vm.preview.shutdown()
    }

    @Test
    fun enabledCaptureUsesPreviewPortAndConfirmsResidentStatus() = runTest {
        FeatureFlags.enable("astroWebUiAstroCapture")
        val client = FakeAstroCaptureClient()
        val programmeClient = FakeProgrammeClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiAstroCaptureClient = client,
            astroWebUiProgrammeClient = programmeClient,
        )
        installSession(vm, this)
        try {
            val parameters = AstroWebUiAstroCaptureParameters(intervalSeconds = 8, shots = 12, bulbSeconds = 2.0)
            vm.startAstroWebUiAstroCapture(parameters)
            advanceUntilIdle()

            assertEquals(1, client.calls)
            assertEquals("192.168.0.1", client.lastHost)
            assertEquals(8080, client.lastPort)
            assertEquals(parameters, client.lastParameters)
            assertIs<AstroWebUiAstroCaptureState.Accepted>(vm.astroWebUiAstroCapture)
            assertEquals(1, programmeClient.calls)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun disconnectDropsLateInHeadCaptureResult() = runTest {
        FeatureFlags.enable("astroWebUiAstroCapture")
        val gate = CompletableDeferred<Unit>()
        val client = FakeAstroCaptureClient().also { it.releaseStart = gate }
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiAstroCaptureClient = client,
        )
        installSession(vm, this)
        try {
            vm.startAstroWebUiAstroCapture(AstroWebUiAstroCaptureParameters(8))
            runCurrent()
            assertIs<AstroWebUiAstroCaptureState.Loading>(vm.astroWebUiAstroCapture)

            vm.disconnect()
            gate.complete(Unit)
            advanceUntilIdle()

            assertIs<AstroWebUiAstroCaptureState.Idle>(vm.astroWebUiAstroCapture)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
