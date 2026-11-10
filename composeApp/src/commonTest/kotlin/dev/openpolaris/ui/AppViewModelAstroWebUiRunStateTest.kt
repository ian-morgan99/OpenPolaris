package dev.openpolaris.ui

import dev.openpolaris.core.domain.AstroWebUiRunState
import dev.openpolaris.core.domain.AstroWebUiRunStateClient
import dev.openpolaris.core.domain.AstroWebUiRunStateResult
import dev.openpolaris.core.domain.AstroWebUiRunStateUiState
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MountSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelAstroWebUiRunStateTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeRunStateClient : AstroWebUiRunStateClient {
        var calls = 0
        var host: String? = null
        var port: Int? = null
        val readMounts = mutableListOf<Boolean>()

        override suspend fun read(host: String, port: Int, readMount: Boolean): AstroWebUiRunStateResult {
            calls += 1
            this.host = host
            this.port = port
            readMounts += readMount
            return AstroWebUiRunStateResult.Success(
                AstroWebUiRunState(
                    status = "tracking",
                    elapsedSeconds = 2,
                    mountRead = readMount,
                    mountBlocked = false,
                    altitudeDeg = 10.0,
                    azimuthDeg = 20.0,
                    aligned = true,
                    tracking = true,
                ),
            )
        }
    }

    private fun viewModel(client: FakeRunStateClient, scope: kotlinx.coroutines.CoroutineScope): AppViewModel {
        val vm = AppViewModel(
            scope = scope,
            connectionFactory = { NoopConnection() },
            astroWebUiRunStateClient = client,
        )
        vm.testInstallSession(
            MountSession(
                connectionFactory = { NoopConnection() },
                host = "192.168.0.1",
                port = 9090,
                readerScope = scope,
            ),
        )
        return vm
    }

    @Test
    fun refreshUsesPreviewPortAndDoesNotReadMount() = runTest {
        val client = FakeRunStateClient()
        val vm = viewModel(client, this)
        try {
            vm.refreshAstroWebUiRunState()
            advanceUntilIdle()
            assertEquals(1, client.calls)
            assertEquals("192.168.0.1", client.host)
            assertEquals(8080, client.port)
            assertEquals(listOf(false), client.readMounts)
            assertIs<AstroWebUiRunStateUiState.Ready>(vm.astroWebUiRunState)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun visiblePollingUsesTwoSecondCadenceAndNeverReadsMount() = runTest {
        val client = FakeRunStateClient()
        val vm = viewModel(client, this)
        try {
            vm.startAstroWebUiRunStatePolling()
            runCurrent()
            assertEquals(1, client.calls)
            advanceTimeBy(1_999L)
            runCurrent()
            assertEquals(1, client.calls)
            advanceTimeBy(1L)
            runCurrent()
            assertEquals(2, client.calls)
            assertEquals(listOf(false, false), client.readMounts)
            vm.stopAstroWebUiRunStatePolling()
            advanceTimeBy(4_000L)
            runCurrent()
            assertEquals(2, client.calls)
        } finally {
            vm.stopAstroWebUiRunStatePolling()
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun mountPositionIsOnlyRequestedByExplicitAction() = runTest {
        val client = FakeRunStateClient()
        val vm = viewModel(client, this)
        try {
            vm.readAstroWebUiMountPosition()
            advanceUntilIdle()
            assertEquals(listOf(true), client.readMounts)
            assertIs<AstroWebUiRunStateUiState.Ready>(vm.astroWebUiRunState)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
