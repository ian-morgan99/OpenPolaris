package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiFocalClient
import dev.openpolaris.core.domain.AstroWebUiFocalResult
import dev.openpolaris.core.domain.AstroWebUiFocalState
import dev.openpolaris.core.domain.AstroWebUiFocalUiState
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
class AppViewModelAstroWebUiFocalTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeFocalClient : AstroWebUiFocalClient {
        var reads = 0
        var writes = 0
        var lastPort: Int? = null
        var releaseWrite: CompletableDeferred<Unit>? = null

        private fun state() = AstroWebUiFocalResult.Success(
            AstroWebUiFocalState(
                overrideMm = null,
                exifCacheMm = 400.0,
                configMm = 400.0,
            ),
        )

        override suspend fun read(host: String, port: Int): AstroWebUiFocalResult {
            reads++
            lastPort = port
            return state()
        }

        override suspend fun setOverride(
            host: String,
            port: Int,
            focalMm: Double?,
        ): AstroWebUiFocalResult {
            writes++
            lastPort = port
            releaseWrite?.let { gate -> withContext(NonCancellable) { gate.await() } }
            return AstroWebUiFocalResult.Accepted(focalMm)
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
    fun focalReadbackUsesSafeDefaultAndPreviewPort() = runTest {
        val client = FakeFocalClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiFocalClient = client,
        )
        installSession(vm, this)
        vm.refreshAstroWebUiFocal()
        advanceUntilIdle()
        assertEquals(1, client.reads)
        assertEquals(8080, client.lastPort)
        assertIs<AstroWebUiFocalUiState.Ready>(vm.astroWebUiFocal)
        vm.disconnect()
        vm.preview.shutdown()
    }

    @Test
    fun focalWritesRemainBlockedByDefault() = runTest {
        val client = FakeFocalClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiFocalClient = client,
        )
        installSession(vm, this)
        vm.setAstroWebUiFocalOverride(1350.0)
        advanceUntilIdle()
        assertEquals(0, client.writes)
        assertIs<AstroWebUiFocalUiState.Failed>(vm.astroWebUiFocal)
        vm.disconnect()
        vm.preview.shutdown()
    }

    @Test
    fun enabledFocalWriteUsesPreviewPortAndConfirmsReadback() = runTest {
        FeatureFlags.enable("astroWebUiFocalWrite")
        val client = FakeFocalClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiFocalClient = client,
        )
        installSession(vm, this)
        try {
            vm.setAstroWebUiFocalOverride(1350.0)
            advanceUntilIdle()
            assertEquals(1, client.writes)
            assertEquals(8080, client.lastPort)
            assertIs<AstroWebUiFocalUiState.Ready>(vm.astroWebUiFocal)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun disconnectDropsLateFocalWrite() = runTest {
        FeatureFlags.enable("astroWebUiFocalWrite")
        val client = FakeFocalClient()
        val gate = CompletableDeferred<Unit>()
        client.releaseWrite = gate
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiFocalClient = client,
        )
        installSession(vm, this)
        try {
            vm.setAstroWebUiFocalOverride(1350.0)
            runCurrent()
            assertIs<AstroWebUiFocalUiState.Loading>(vm.astroWebUiFocal)
            vm.disconnect()
            gate.complete(Unit)
            advanceUntilIdle()
            assertIs<AstroWebUiFocalUiState.Idle>(vm.astroWebUiFocal)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
