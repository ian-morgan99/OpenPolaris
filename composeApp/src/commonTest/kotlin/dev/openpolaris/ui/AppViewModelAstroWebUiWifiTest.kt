package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiWifiClient
import dev.openpolaris.core.domain.AstroWebUiWifiResult
import dev.openpolaris.core.domain.AstroWebUiWifiState
import dev.openpolaris.core.domain.AstroWebUiWifiUiState
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
class AppViewModelAstroWebUiWifiTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeWifiClient : AstroWebUiWifiClient {
        var reads = 0
        var lastPort: Int? = null
        var releaseRead: CompletableDeferred<Unit>? = null

        override suspend fun read(host: String, port: Int): AstroWebUiWifiResult {
            reads++
            lastPort = port
            releaseRead?.let { gate -> withContext(NonCancellable) { gate.await() } }
            return AstroWebUiWifiResult.Success(
                AstroWebUiWifiState(
                    apIp = "192.168.0.1",
                    stationIp = "",
                    ssid = "Polaris_1234",
                    configured = false,
                    autojoin = false,
                ),
            )
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
    fun wifiReadbackUsesSafeDefaultAndPreviewPort() = runTest {
        val client = FakeWifiClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiWifiClient = client,
        )
        installSession(vm, this)
        vm.refreshAstroWebUiWifi()
        advanceUntilIdle()
        assertEquals(1, client.reads)
        assertEquals(8080, client.lastPort)
        assertIs<AstroWebUiWifiUiState.Ready>(vm.astroWebUiWifi)
        vm.disconnect()
        vm.preview.shutdown()
    }

    @Test
    fun disconnectDropsLateWifiRead() = runTest {
        val client = FakeWifiClient()
        val gate = CompletableDeferred<Unit>()
        client.releaseRead = gate
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiWifiClient = client,
        )
        installSession(vm, this)
        try {
            vm.refreshAstroWebUiWifi()
            runCurrent()
            assertIs<AstroWebUiWifiUiState.Loading>(vm.astroWebUiWifi)
            vm.disconnect()
            gate.complete(Unit)
            advanceUntilIdle()
            assertIs<AstroWebUiWifiUiState.Idle>(vm.astroWebUiWifi)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
