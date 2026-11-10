package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiSolveClient
import dev.openpolaris.core.domain.AstroWebUiSolveControlClient
import dev.openpolaris.core.domain.AstroWebUiSolveControlResult
import dev.openpolaris.core.domain.AstroWebUiSolveControlUiState
import dev.openpolaris.core.domain.AstroWebUiSolveMode
import dev.openpolaris.core.domain.AstroWebUiSolveResult
import dev.openpolaris.core.domain.AstroWebUiSolveState
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MountSession
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppViewModelAstroWebUiSolveControlTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeSolveClient : AstroWebUiSolveClient {
        override suspend fun read(host: String, port: Int): AstroWebUiSolveResult =
            AstroWebUiSolveResult.Success(AstroWebUiSolveState())
    }

    private class FakeControlClient : AstroWebUiSolveControlClient {
        var starts = 0
        var applies = 0
        var cancels = 0
        var lastPort: Int? = null

        override suspend fun start(host: String, port: Int, request: dev.openpolaris.core.domain.AstroWebUiSolveRequest): AstroWebUiSolveControlResult {
            starts++
            lastPort = port
            return AstroWebUiSolveControlResult.Started(request)
        }

        override suspend fun apply(host: String, port: Int): AstroWebUiSolveControlResult {
            applies++
            lastPort = port
            return AstroWebUiSolveControlResult.Applied(3, "aligned")
        }

        override suspend fun cancel(host: String, port: Int): AstroWebUiSolveControlResult {
            cancels++
            lastPort = port
            return AstroWebUiSolveControlResult.Cancelled
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
    fun solveControlsRemainBlockedByDefault() = runTest {
        val client = FakeControlClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiSolveControlClient = client,
        )
        installSession(vm, this)
        vm.startAstroWebUiSolve(AstroWebUiSolveMode.LATEST)
        advanceUntilIdle()
        assertEquals(0, client.starts)
        assertIs<AstroWebUiSolveControlUiState.Failed>(vm.astroWebUiSolveControl)
        vm.disconnect()
        vm.preview.shutdown()
    }

    @Test
    fun enabledControlsUsePreviewPortAndKeepActionsSeparate() = runTest {
        FeatureFlags.enable("astroWebUiSolveControls")
        val client = FakeControlClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiSolveClient = FakeSolveClient(),
            astroWebUiSolveControlClient = client,
        )
        installSession(vm, this)
        vm.startAstroWebUiSolve(AstroWebUiSolveMode.LATEST)
        advanceUntilIdle()
        vm.cancelAstroWebUiSolve()
        advanceUntilIdle()
        vm.applyAstroWebUiSolve()
        advanceUntilIdle()
        assertEquals(1, client.starts)
        assertEquals(1, client.cancels)
        assertEquals(1, client.applies)
        assertEquals(8080, client.lastPort)
        assertIs<AstroWebUiSolveControlUiState.Accepted>(vm.astroWebUiSolveControl)
        vm.disconnect()
        vm.preview.shutdown()
    }
}
