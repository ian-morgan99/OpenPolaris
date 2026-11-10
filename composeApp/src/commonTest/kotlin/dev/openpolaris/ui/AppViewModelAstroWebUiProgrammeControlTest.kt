package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiProgrammeControlClient
import dev.openpolaris.core.domain.AstroWebUiProgrammeControlResult
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MountSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelAstroWebUiProgrammeControlTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeControlClient : AstroWebUiProgrammeControlClient {
        var intervalCalls = 0
        var lastPort: Int? = null
        var lastSeconds: Int? = null
        var release: CompletableDeferred<Unit>? = null

        override suspend fun cancel(host: String, port: Int) = AstroWebUiProgrammeControlResult.Accepted

        override suspend fun setPanoramaPaused(
            host: String,
            port: Int,
            paused: Boolean,
        ) = AstroWebUiProgrammeControlResult.Accepted

        override suspend fun setPanoramaInterval(
            host: String,
            port: Int,
            seconds: Int,
        ): AstroWebUiProgrammeControlResult {
            intervalCalls++
            lastPort = port
            lastSeconds = seconds
            release?.let { gate -> withContext(NonCancellable) { gate.await() } }
            return AstroWebUiProgrammeControlResult.Accepted
        }
    }

    @AfterTest
    fun resetFlags() = FeatureFlags.reset()

    @Test
    fun panoramaIntervalRemainsBlockedByDefault() = runTest {
        val client = FakeControlClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiProgrammeControlClient = client,
        )

        vm.setAstroWebUiPanoramaInterval(12)

        assertEquals(0, client.intervalCalls)
        vm.disconnect()
        vm.preview.shutdown()
    }

    @Test
    fun enabledPanoramaIntervalUsesPreviewPort() = runTest {
        FeatureFlags.enable("astroWebUiProgrammeControls")
        val client = FakeControlClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiProgrammeControlClient = client,
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
            vm.setAstroWebUiPanoramaInterval(12)
            advanceUntilIdle()

            assertEquals(1, client.intervalCalls)
            assertEquals(8080, client.lastPort)
            assertEquals(12, client.lastSeconds)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
