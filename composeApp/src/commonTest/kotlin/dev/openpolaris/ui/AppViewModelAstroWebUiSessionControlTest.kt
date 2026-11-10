package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiSessionControlClient
import dev.openpolaris.core.domain.AstroWebUiSessionControlResult
import dev.openpolaris.core.domain.AstroWebUiSessionControlState
import dev.openpolaris.core.domain.AstroWebUiStatus
import dev.openpolaris.core.domain.AstroWebUiStatusClient
import dev.openpolaris.core.domain.AstroWebUiStatusResult
import dev.openpolaris.core.domain.AstroWebUiTrackRequest
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
class AppViewModelAstroWebUiSessionControlTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeControlClient : AstroWebUiSessionControlClient {
        val actions = mutableListOf<String>()
        var releaseEnter: CompletableDeferred<Unit>? = null

        override suspend fun enter(host: String, port: Int): AstroWebUiSessionControlResult {
            actions += "enter:" + host + ":" + port
            releaseEnter?.let { gate ->
                withContext(NonCancellable) { gate.await() }
            }
            return AstroWebUiSessionControlResult.Accepted("enter Astro mode")
        }

        override suspend fun leave(host: String, port: Int): AstroWebUiSessionControlResult {
            actions += "leave:" + host + ":" + port
            return AstroWebUiSessionControlResult.Accepted("leave Astro mode")
        }

        override suspend fun setTracking(
            host: String,
            port: Int,
            request: AstroWebUiTrackRequest,
        ): AstroWebUiSessionControlResult {
            actions += "track:" + request.on + ":" + request.rate + ":" + request.halfSpeed
            return AstroWebUiSessionControlResult.Accepted(
                if (request.on) "start Astro tracking" else "stop Astro tracking",
            )
        }
    }

    private class FakeStatusClient : AstroWebUiStatusClient {
        var calls = 0
        var lastPort: Int? = null

        override suspend fun read(host: String, port: Int): AstroWebUiStatusResult {
            calls += 1
            lastPort = port
            return AstroWebUiStatusResult.Success(AstroWebUiStatus(active = true))
        }
    }

    @AfterTest
    fun resetFlags() {
        FeatureFlags.reset()
    }

    @Test
    fun sessionWritesRemainBlockedByDefault() = runTest {
        val client = FakeControlClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiSessionControlClient = client,
        )
        val session = MountSession(
            connectionFactory = { NoopConnection() },
            host = "192.168.0.1",
            port = 9090,
            readerScope = this,
        )
        vm.testInstallSession(session)

        try {
            vm.enterAstroWebUi()
            advanceUntilIdle()

            assertEquals(emptyList(), client.actions)
            val state = assertIs<AstroWebUiSessionControlState.Failed>(vm.astroWebUiSessionControl)
            assertEquals(
                "Astro WebUI session controls are disabled by config",
                state.reason,
            )
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun disconnectDropsLateSessionControlResult() = runTest {
        FeatureFlags.enable("astroWebUiSessionControls")
        val client = FakeControlClient()
        val gate = CompletableDeferred<Unit>()
        client.releaseEnter = gate
        val statusClient = FakeStatusClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiSessionControlClient = client,
            astroWebUiStatusClient = statusClient,
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
            vm.enterAstroWebUi()
            runCurrent()
            assertEquals(listOf("enter:192.168.0.1:8080"), client.actions)
            assertIs<AstroWebUiSessionControlState.Working>(vm.astroWebUiSessionControl)

            vm.disconnect()
            gate.complete(Unit)
            advanceUntilIdle()

            assertIs<AstroWebUiSessionControlState.Idle>(vm.astroWebUiSessionControl)
            assertEquals(0, statusClient.calls)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun enabledSessionWriteUsesPreviewPortAndConfirmsByStatusReadback() = runTest {
        FeatureFlags.enable("astroWebUiSessionControls")
        val client = FakeControlClient()
        val statusClient = FakeStatusClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiSessionControlClient = client,
            astroWebUiStatusClient = statusClient,
        )
        val session = MountSession(
            connectionFactory = { NoopConnection() },
            host = "192.168.0.1",
            port = 9090,
            readerScope = this,
        )
        vm.testInstallSession(session)

        try {
            vm.setAstroWebUiTracking(
                AstroWebUiTrackRequest(on = true, rate = 0, halfSpeed = false),
            )
            advanceUntilIdle()

            assertEquals(listOf("track:true:0:false"), client.actions)
            assertEquals(1, statusClient.calls)
            assertEquals(8080, statusClient.lastPort)
            assertIs<AstroWebUiSessionControlState.Accepted>(vm.astroWebUiSessionControl)
            assertIs<dev.openpolaris.core.domain.AstroWebUiStatusState.Ready>(vm.astroWebUiStatus)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
