package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiKeepWifiClient
import dev.openpolaris.core.domain.AstroWebUiKeepWifiResult
import dev.openpolaris.core.domain.AstroWebUiKeepWifiState
import dev.openpolaris.core.domain.AstroWebUiKeepWifiUiState
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
class AppViewModelAstroWebUiKeepWifiTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeKeepWifiClient : AstroWebUiKeepWifiClient {
        var reads = 0
        var writes = 0
        var lastPort: Int? = null
        var releaseWrite: CompletableDeferred<Unit>? = null

        private fun state() = AstroWebUiKeepWifiResult.Success(
            AstroWebUiKeepWifiState(
                enabled = true,
                running = true,
                aligned = true,
                forced = false,
            ),
        )

        override suspend fun read(host: String, port: Int): AstroWebUiKeepWifiResult {
            reads++
            lastPort = port
            return state()
        }

        override suspend fun set(
            host: String,
            port: Int,
            enabled: Boolean,
            forced: Boolean,
        ): AstroWebUiKeepWifiResult {
            writes++
            lastPort = port
            releaseWrite?.let { gate -> withContext(NonCancellable) { gate.await() } }
            return AstroWebUiKeepWifiResult.Accepted(enabled, forced && enabled)
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
    fun keepWifiReadbackUsesSafeDefaultAndPreviewPort() = runTest {
        val client = FakeKeepWifiClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiKeepWifiClient = client,
        )
        installSession(vm, this)
        vm.refreshAstroWebUiKeepWifi()
        advanceUntilIdle()
        assertEquals(1, client.reads)
        assertEquals(8080, client.lastPort)
        assertIs<AstroWebUiKeepWifiUiState.Ready>(vm.astroWebUiKeepWifi)
        vm.disconnect()
        vm.preview.shutdown()
    }

    @Test
    fun keepWifiWritesRemainBlockedByDefault() = runTest {
        val client = FakeKeepWifiClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiKeepWifiClient = client,
        )
        installSession(vm, this)
        vm.setAstroWebUiKeepWifi(true, false)
        advanceUntilIdle()
        assertEquals(0, client.writes)
        assertIs<AstroWebUiKeepWifiUiState.Failed>(vm.astroWebUiKeepWifi)
        vm.disconnect()
        vm.preview.shutdown()
    }

    @Test
    fun enabledKeepWifiWriteConfirmsReadback() = runTest {
        FeatureFlags.enable("astroWebUiKeepWifiWrite")
        val client = FakeKeepWifiClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiKeepWifiClient = client,
        )
        installSession(vm, this)
        try {
            vm.setAstroWebUiKeepWifi(true, true)
            advanceUntilIdle()
            assertEquals(1, client.writes)
            assertEquals(8080, client.lastPort)
            assertIs<AstroWebUiKeepWifiUiState.Ready>(vm.astroWebUiKeepWifi)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun disconnectDropsLateKeepWifiWrite() = runTest {
        FeatureFlags.enable("astroWebUiKeepWifiWrite")
        val client = FakeKeepWifiClient()
        val gate = CompletableDeferred<Unit>()
        client.releaseWrite = gate
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiKeepWifiClient = client,
        )
        installSession(vm, this)
        try {
            vm.setAstroWebUiKeepWifi(true, false)
            runCurrent()
            assertIs<AstroWebUiKeepWifiUiState.Loading>(vm.astroWebUiKeepWifi)
            vm.disconnect()
            gate.complete(Unit)
            advanceUntilIdle()
            assertIs<AstroWebUiKeepWifiUiState.Idle>(vm.astroWebUiKeepWifi)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
