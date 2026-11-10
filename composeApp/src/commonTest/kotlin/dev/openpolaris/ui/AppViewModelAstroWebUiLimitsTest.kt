package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiLimitsClient
import dev.openpolaris.core.domain.AstroWebUiLimitsResult
import dev.openpolaris.core.domain.AstroWebUiLimitsState
import dev.openpolaris.core.domain.AstroWebUiLimitsUiState
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
class AppViewModelAstroWebUiLimitsTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeLimitsClient : AstroWebUiLimitsClient {
        var reads = 0
        var lastPort: Int? = null
        var releaseRead: CompletableDeferred<Unit>? = null

        override suspend fun read(host: String, port: Int): AstroWebUiLimitsResult {
            reads++
            lastPort = port
            releaseRead?.let { gate -> withContext(NonCancellable) { gate.await() } }
            return AstroWebUiLimitsResult.Success(AstroWebUiLimitsState(released = false))
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
    fun limitsReadbackUsesSafeDefaultAndPreviewPort() = runTest {
        val client = FakeLimitsClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiLimitsClient = client,
        )
        installSession(vm, this)
        vm.refreshAstroWebUiLimits()
        advanceUntilIdle()
        assertEquals(1, client.reads)
        assertEquals(8080, client.lastPort)
        assertIs<AstroWebUiLimitsUiState.Ready>(vm.astroWebUiLimits)
        vm.disconnect()
        vm.preview.shutdown()
    }

    @Test
    fun disconnectDropsLateLimitsRead() = runTest {
        val client = FakeLimitsClient()
        val gate = CompletableDeferred<Unit>()
        client.releaseRead = gate
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiLimitsClient = client,
        )
        installSession(vm, this)
        try {
            vm.refreshAstroWebUiLimits()
            runCurrent()
            assertIs<AstroWebUiLimitsUiState.Loading>(vm.astroWebUiLimits)
            vm.disconnect()
            gate.complete(Unit)
            advanceUntilIdle()
            assertIs<AstroWebUiLimitsUiState.Idle>(vm.astroWebUiLimits)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
