package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiLinkClient
import dev.openpolaris.core.domain.AstroWebUiLinkPhase
import dev.openpolaris.core.domain.AstroWebUiLinkResult
import dev.openpolaris.core.domain.AstroWebUiLinkState
import dev.openpolaris.core.domain.AstroWebUiLinkUiState
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MountSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelAstroWebUiLinkTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeLinkClient : AstroWebUiLinkClient {
        var calls = 0
        var port: Int? = null

        override suspend fun read(host: String, port: Int): AstroWebUiLinkResult {
            calls++
            this.port = port
            return AstroWebUiLinkResult.Success(
                AstroWebUiLinkState(
                    phase = AstroWebUiLinkPhase.UP,
                    wanted = true,
                    connects = 1,
                    drops = 0,
                    receivedFrames = 2,
                    transmittedFrames = 3,
                    parseErrors = 0,
                    evictions = 0,
                    upMs = 100,
                    lastRxAgeMs = 4,
                    error = "",
                    opcodes = emptyList(),
                ),
            )
        }
    }

    @AfterTest
    fun resetFlags() = FeatureFlags.reset()

    @Test
    fun linkRefreshUsesPreviewPortAndReadOnlyDefault() = runTest {
        val client = FakeLinkClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiLinkClient = client,
        )
        vm.testInstallSession(
            MountSession(
                connectionFactory = { NoopConnection() },
                host = "192.168.0.1",
                port = 9090,
                readerScope = this,
            ),
        )
        vm.refreshAstroWebUiLink()
        advanceUntilIdle()
        assertEquals(1, client.calls)
        assertEquals(8080, client.port)
        assertIs<AstroWebUiLinkUiState.Ready>(vm.astroWebUiLink)
        vm.disconnect()
        vm.preview.shutdown()
    }
}
