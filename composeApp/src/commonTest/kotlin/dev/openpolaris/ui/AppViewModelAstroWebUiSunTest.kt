package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiProgramme
import dev.openpolaris.core.domain.AstroWebUiProgrammeClient
import dev.openpolaris.core.domain.AstroWebUiProgrammeResult
import dev.openpolaris.core.domain.AstroWebUiSunParameters
import dev.openpolaris.core.domain.AstroWebUiSunProgrammeClient
import dev.openpolaris.core.domain.AstroWebUiSunResult
import dev.openpolaris.core.domain.AstroWebUiSunState
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
class AppViewModelAstroWebUiSunTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeSunClient : AstroWebUiSunProgrammeClient {
        var previewCalls = 0
        var startCalls = 0
        var lastPort: Int? = null
        var releaseStart: CompletableDeferred<Unit>? = null

        override suspend fun preview(host: String, port: Int, parameters: AstroWebUiSunParameters): AstroWebUiSunResult {
            previewCalls++
            lastPort = port
            return AstroWebUiSunResult.Preview("@277;preview;")
        }

        override suspend fun start(host: String, port: Int, parameters: AstroWebUiSunParameters): AstroWebUiSunResult {
            startCalls++
            lastPort = port
            releaseStart?.let { gate -> withContext(NonCancellable) { gate.await() } }
            return AstroWebUiSunResult.Accepted
        }
    }

    private class FakeProgrammeClient : AstroWebUiProgrammeClient {
        var calls = 0

        override suspend fun read(host: String, port: Int): AstroWebUiProgrammeResult {
            calls++
            return AstroWebUiProgrammeResult.Success(AstroWebUiProgramme("sun"))
        }
    }

    @AfterTest
    fun resetFlags() {
        FeatureFlags.reset()
    }

    private fun parameters() = AstroWebUiSunParameters(
        sunset = false,
        intervalSeconds = 10.0,
        startEpochSeconds = 1_700_000_000L,
        endEpochSeconds = 1_700_003_600L,
        nowEpochSeconds = 1_700_000_100L,
    )

    @Test
    fun sunProgrammeRemainsBlockedByDefault() = runTest {
        val client = FakeSunClient()
        val vm = AppViewModel(scope = this, connectionFactory = { NoopConnection() }, astroWebUiSunProgrammeClient = client)
        vm.startAstroWebUiSun(parameters())
        assertEquals(0, client.startCalls)
        assertIs<AstroWebUiSunState.Failed>(vm.astroWebUiSunState)
        vm.disconnect()
        vm.preview.shutdown()
    }

    @Test
    fun enabledPreviewUsesPreviewPortAndKeepsRunUnstarted() = runTest {
        FeatureFlags.enable("astroWebUiProgrammeStarts")
        val client = FakeSunClient()
        val vm = AppViewModel(scope = this, connectionFactory = { NoopConnection() }, astroWebUiSunProgrammeClient = client)
        vm.testInstallSession(
            MountSession(
                connectionFactory = { NoopConnection() },
                host = "192.168.0.1",
                port = 9090,
                readerScope = this,
            ),
        )
        try {
            vm.previewAstroWebUiSun(parameters())
            advanceUntilIdle()
            assertEquals(1, client.previewCalls)
            assertEquals(0, client.startCalls)
            assertEquals(8080, client.lastPort)
            assertIs<AstroWebUiSunState.Preview>(vm.astroWebUiSunState)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun disconnectDropsLateSolarStartAndSkipsStatusReadback() = runTest {
        FeatureFlags.enable("astroWebUiProgrammeStarts")
        val client = FakeSunClient()
        val gate = CompletableDeferred<Unit>()
        client.releaseStart = gate
        val programmeClient = FakeProgrammeClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiSunProgrammeClient = client,
            astroWebUiProgrammeClient = programmeClient,
        )
        vm.testInstallSession(
            MountSession(
                connectionFactory = { NoopConnection() },
                host = "192.168.0.1",
                port = 9090,
                readerScope = this,
            ),
        )
        try {
            vm.startAstroWebUiSun(parameters())
            runCurrent()
            assertIs<AstroWebUiSunState.Loading>(vm.astroWebUiSunState)
            vm.disconnect()
            gate.complete(Unit)
            advanceUntilIdle()
            assertIs<AstroWebUiSunState.Idle>(vm.astroWebUiSunState)
            assertEquals(0, programmeClient.calls)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
