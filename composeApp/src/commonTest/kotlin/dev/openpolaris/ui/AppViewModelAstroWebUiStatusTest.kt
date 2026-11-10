package dev.openpolaris.ui

import dev.openpolaris.core.domain.AstroWebUiStatus
import dev.openpolaris.core.domain.AstroWebUiStatusClient
import dev.openpolaris.core.domain.AstroWebUiStatusResult
import dev.openpolaris.core.domain.AstroWebUiStatusState
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MountSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelAstroWebUiStatusTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeStatusClient : AstroWebUiStatusClient {
        var calls = 0
        var host: String? = null
        var port: Int? = null
        var releaseRead: CompletableDeferred<Unit>? = null

        override suspend fun read(host: String, port: Int): AstroWebUiStatusResult {
            calls += 1
            releaseRead?.let { gate ->
                withContext(NonCancellable) { gate.await() }
            }
            this.host = host
            this.port = port
            return AstroWebUiStatusResult.Success(AstroWebUiStatus(active = true))
        }
    }

    @AfterTest
    fun resetFlags() {
        dev.openpolaris.core.config.FeatureFlags.reset()
    }

    @Test
    fun statusUsesDedicatedPreviewPortAndReportsReadOnlyAstroMode() = runTest {
        val client = FakeStatusClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiStatusClient = client,
        )
        val session = MountSession(
            connectionFactory = { NoopConnection() },
            host = "192.168.0.1",
            port = 9090,
            readerScope = this,
        )
        vm.testInstallSession(session)

        try {
            vm.refreshAstroWebUiStatus()
            advanceUntilIdle()

            assertEquals(1, client.calls)
            assertEquals("192.168.0.1", client.host)
            assertEquals(8080, client.port)
            val state = assertIs<AstroWebUiStatusState.Ready>(vm.astroWebUiStatus)
            assertEquals(true, state.status.active)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun disconnectDropsLateStatusReadResult() = runTest {
        val client = FakeStatusClient()
        val gate = CompletableDeferred<Unit>()
        client.releaseRead = gate
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiStatusClient = client,
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
            vm.refreshAstroWebUiStatus()
            runCurrent()
            assertEquals(1, client.calls)
            assertIs<AstroWebUiStatusState.Loading>(vm.astroWebUiStatus)

            vm.disconnect()
            gate.complete(Unit)
            advanceUntilIdle()

            assertIs<AstroWebUiStatusState.Idle>(vm.astroWebUiStatus)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun visibleAstroStatusPollingUsesTheSourceTwoSecondCadenceAndStops() = runTest {
        val client = FakeStatusClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiStatusClient = client,
        )
        val session = MountSession(
            connectionFactory = { NoopConnection() },
            host = "192.168.0.1",
            port = 9090,
            readerScope = this,
        )
        vm.testInstallSession(session)

        try {
            vm.startAstroWebUiStatusPolling()
            runCurrent()
            assertEquals(1, client.calls)

            advanceTimeBy(1_999L)
            runCurrent()
            assertEquals(1, client.calls)

            advanceTimeBy(1L)
            runCurrent()
            assertEquals(2, client.calls)

            vm.stopAstroWebUiStatusPolling()
            advanceTimeBy(4_000L)
            runCurrent()
            assertEquals(2, client.calls)
        } finally {
            vm.stopAstroWebUiStatusPolling()
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun statusDoesNotCallClientWithoutAnInstalledSession() = runTest {
        val client = FakeStatusClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiStatusClient = client,
        )

        try {
            vm.refreshAstroWebUiStatus()
            advanceUntilIdle()

            assertEquals(0, client.calls)
            assertIs<AstroWebUiStatusState.Failed>(vm.astroWebUiStatus)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
