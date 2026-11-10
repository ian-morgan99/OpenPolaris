package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiGuideClient
import dev.openpolaris.core.domain.AstroWebUiGuidePoint
import dev.openpolaris.core.domain.AstroWebUiGuideResult
import dev.openpolaris.core.domain.AstroWebUiGuideState
import dev.openpolaris.core.domain.AstroWebUiGuideUiState
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
class AppViewModelAstroWebUiGuideTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeGuideClient : AstroWebUiGuideClient {
        var reads = 0
        var writes = 0
        var lastPort: Int? = null
        var releaseWrite: CompletableDeferred<Unit>? = null

        override suspend fun read(host: String, port: Int): AstroWebUiGuideResult {
            reads++
            lastPort = port
            return AstroWebUiGuideResult.Success(
                AstroWebUiGuideState(
                    running = false,
                    thresholdArcsec = 60.0,
                    intervalSeconds = 30.0,
                    lastDriftArcsec = null,
                    points = listOf(AstroWebUiGuidePoint("14:02:11", 12.0, false)),
                    log = "",
                ),
            )
        }

        override suspend fun setRunning(host: String, port: Int, running: Boolean): AstroWebUiGuideResult {
            writes++
            lastPort = port
            releaseWrite?.let { gate -> withContext(NonCancellable) { gate.await() } }
            return AstroWebUiGuideResult.Accepted(running)
        }
    }

    @AfterTest
    fun resetFlags() = FeatureFlags.reset()

    private fun installSession(vm: AppViewModel, scope: kotlinx.coroutines.CoroutineScope) {
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
    fun guideReadbackUsesSafeDefault() = runTest {
        val client = FakeGuideClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiGuideClient = client,
        )
        installSession(vm, this)
        vm.refreshAstroWebUiGuide()
        advanceUntilIdle()
        assertEquals(1, client.reads)
        assertIs<AstroWebUiGuideUiState.Ready>(vm.astroWebUiGuide)
        vm.disconnect()
        vm.preview.shutdown()
    }

    @Test
    fun guideWritesRemainBlockedByDefault() = runTest {
        val client = FakeGuideClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiGuideClient = client,
        )
        installSession(vm, this)
        vm.setAstroWebUiGuiding(true)
        advanceUntilIdle()
        assertEquals(0, client.writes)
        assertIs<AstroWebUiGuideUiState.Failed>(vm.astroWebUiGuide)
        vm.disconnect()
        vm.preview.shutdown()
    }

    @Test
    fun enabledGuideReadbackUsesPreviewPort() = runTest {
        FeatureFlags.enable("astroWebUiGuiding")
        val client = FakeGuideClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiGuideClient = client,
        )
        installSession(vm, this)
        try {
            vm.refreshAstroWebUiGuide()
            advanceUntilIdle()
            assertEquals(1, client.reads)
            assertEquals(8080, client.lastPort)
            assertIs<AstroWebUiGuideUiState.Ready>(vm.astroWebUiGuide)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun disconnectDropsLateGuideWrite() = runTest {
        FeatureFlags.enable("astroWebUiGuiding")
        val client = FakeGuideClient()
        val gate = CompletableDeferred<Unit>()
        client.releaseWrite = gate
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiGuideClient = client,
        )
        installSession(vm, this)
        try {
            vm.setAstroWebUiGuiding(true)
            runCurrent()
            assertIs<AstroWebUiGuideUiState.Loading>(vm.astroWebUiGuide)
            vm.disconnect()
            gate.complete(Unit)
            advanceUntilIdle()
            assertIs<AstroWebUiGuideUiState.Idle>(vm.astroWebUiGuide)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
