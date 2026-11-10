package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiAutofocusClient
import dev.openpolaris.core.domain.AstroWebUiAutofocusParameters
import dev.openpolaris.core.domain.AstroWebUiAutofocusProgress
import dev.openpolaris.core.domain.AstroWebUiAutofocusResult
import dev.openpolaris.core.domain.AstroWebUiAutofocusState
import dev.openpolaris.core.domain.AstroWebUiAutofocusUiState
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
class AppViewModelAstroWebUiAutofocusTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeAutofocusClient : AstroWebUiAutofocusClient {
        var reads = 0
        var starts = 0
        var stops = 0
        var lastPort: Int? = null
        var releaseStart: CompletableDeferred<Unit>? = null

        private fun state() = AstroWebUiAutofocusResult.Success(
            AstroWebUiAutofocusState(
                status = "idle",
                progress = AstroWebUiAutofocusProgress(
                    state = "idle",
                    step = 0,
                    steps = 0,
                    best = null,
                    samples = emptyList(),
                    error = null,
                ),
            ),
        )

        override suspend fun read(host: String, port: Int): AstroWebUiAutofocusResult {
            reads++
            lastPort = port
            return state()
        }

        override suspend fun start(
            host: String,
            port: Int,
            parameters: AstroWebUiAutofocusParameters,
        ): AstroWebUiAutofocusResult {
            starts++
            lastPort = port
            releaseStart?.let { gate -> withContext(NonCancellable) { gate.await() } }
            return AstroWebUiAutofocusResult.Accepted("start")
        }

        override suspend fun stop(host: String, port: Int): AstroWebUiAutofocusResult {
            stops++
            lastPort = port
            return AstroWebUiAutofocusResult.Accepted("stop")
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
    fun autofocusProgressReadbackUsesSafeDefault() = runTest {
        val client = FakeAutofocusClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiAutofocusClient = client,
        )
        installSession(vm, this)
        vm.refreshAstroWebUiAutofocus()
        advanceUntilIdle()
        assertEquals(1, client.reads)
        assertIs<AstroWebUiAutofocusUiState.Ready>(vm.astroWebUiAutofocus)
        vm.disconnect()
        vm.preview.shutdown()
    }

    @Test
    fun autofocusWritesRemainBlockedByDefault() = runTest {
        val client = FakeAutofocusClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiAutofocusClient = client,
        )
        installSession(vm, this)
        vm.startAstroWebUiAutofocus(AstroWebUiAutofocusParameters())
        advanceUntilIdle()
        assertEquals(0, client.starts)
        assertIs<AstroWebUiAutofocusUiState.Failed>(vm.astroWebUiAutofocus)
        vm.disconnect()
        vm.preview.shutdown()
    }

    @Test
    fun enabledAutofocusStartUsesPreviewPort() = runTest {
        FeatureFlags.enable("astroWebUiAutofocus")
        val client = FakeAutofocusClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiAutofocusClient = client,
        )
        installSession(vm, this)
        try {
            vm.startAstroWebUiAutofocus(AstroWebUiAutofocusParameters())
            advanceUntilIdle()
            assertEquals(1, client.starts)
            assertEquals(8080, client.lastPort)
            assertIs<AstroWebUiAutofocusUiState.Ready>(vm.astroWebUiAutofocus)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun disconnectDropsLateAutofocusStart() = runTest {
        FeatureFlags.enable("astroWebUiAutofocus")
        val client = FakeAutofocusClient()
        val gate = CompletableDeferred<Unit>()
        client.releaseStart = gate
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiAutofocusClient = client,
        )
        installSession(vm, this)
        try {
            vm.startAstroWebUiAutofocus(AstroWebUiAutofocusParameters())
            runCurrent()
            assertIs<AstroWebUiAutofocusUiState.Loading>(vm.astroWebUiAutofocus)
            vm.disconnect()
            gate.complete(Unit)
            advanceUntilIdle()
            assertIs<AstroWebUiAutofocusUiState.Idle>(vm.astroWebUiAutofocus)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
