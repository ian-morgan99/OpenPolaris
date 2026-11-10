package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiSolveClient
import dev.openpolaris.core.domain.AstroWebUiSolveResult
import dev.openpolaris.core.domain.AstroWebUiSolveState
import dev.openpolaris.core.domain.AstroWebUiSolveUiState
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MountSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelAstroWebUiSolveTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeSolveClient : AstroWebUiSolveClient {
        var calls = 0
        var host: String? = null
        var port: Int? = null

        override suspend fun read(host: String, port: Int): AstroWebUiSolveResult {
            calls += 1
            this.host = host
            this.port = port
            return AstroWebUiSolveResult.Success(AstroWebUiSolveState())
        }
    }

    @AfterTest
    fun resetFlags() {
        FeatureFlags.reset()
    }

    @Test
    fun solveRefreshUsesDedicatedPreviewPort() = runTest {
        val client = FakeSolveClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiSolveClient = client,
        )
        val session = MountSession(
            connectionFactory = { NoopConnection() },
            host = "192.168.0.1",
            port = 9090,
            readerScope = this,
        )
        vm.testInstallSession(session)

        try {
            vm.refreshAstroWebUiSolve()
            advanceUntilIdle()

            assertEquals(1, client.calls)
            assertEquals("192.168.0.1", client.host)
            assertEquals(8080, client.port)
            assertIs<AstroWebUiSolveUiState.Ready>(vm.astroWebUiSolve)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun visibleSolvePollingUsesTwoSecondCadenceAndStops() = runTest {
        val client = FakeSolveClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiSolveClient = client,
        )
        val session = MountSession(
            connectionFactory = { NoopConnection() },
            host = "192.168.0.1",
            port = 9090,
            readerScope = this,
        )
        vm.testInstallSession(session)

        try {
            vm.startAstroWebUiSolvePolling()
            runCurrent()
            assertEquals(1, client.calls)

            advanceTimeBy(1_999L)
            runCurrent()
            assertEquals(1, client.calls)

            advanceTimeBy(1L)
            runCurrent()
            assertEquals(2, client.calls)

            vm.stopAstroWebUiSolvePolling()
            advanceTimeBy(4_000L)
            runCurrent()
            assertEquals(2, client.calls)
        } finally {
            vm.stopAstroWebUiSolvePolling()
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
