package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiAlignRequest
import dev.openpolaris.core.domain.AstroWebUiGotoRequest
import dev.openpolaris.core.domain.AstroWebUiTargetClient
import dev.openpolaris.core.domain.AstroWebUiTargetResult
import dev.openpolaris.core.domain.AstroWebUiTargetUiState
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
class AppViewModelAstroWebUiTargetTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeTargetClient : AstroWebUiTargetClient {
        var previews = 0
        var starts = 0
        var aligns = 0
        var lastPort: Int? = null

        override suspend fun align(host: String, port: Int, request: AstroWebUiAlignRequest): AstroWebUiTargetResult {
            aligns++
            lastPort = port
            return AstroWebUiTargetResult.AlignmentAccepted(request)
        }

        override suspend fun previewGoto(host: String, port: Int, request: AstroWebUiGotoRequest): AstroWebUiTargetResult {
            previews++
            lastPort = port
            return AstroWebUiTargetResult.GotoPreview(request)
        }

        override suspend fun startGoto(host: String, port: Int, request: AstroWebUiGotoRequest): AstroWebUiTargetResult {
            starts++
            lastPort = port
            return AstroWebUiTargetResult.GotoAccepted(request)
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
    fun targetPreviewIsEnabledByDefaultAndUsesPreviewPort() = runTest {
        val client = FakeTargetClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiTargetClient = client,
        )
        installSession(vm, this)
        val request = AstroWebUiGotoRequest(12.0, -4.0, "Target")
        vm.previewAstroWebUiGoto(request)
        advanceUntilIdle()
        assertEquals(1, client.previews)
        assertEquals(8080, client.lastPort)
        assertIs<AstroWebUiTargetUiState.Preview>(vm.astroWebUiTarget)
        vm.disconnect()
        vm.preview.shutdown()
    }

    @Test
    fun confirmedGotoRequiresWriteGateAndExactPreview() = runTest {
        val client = FakeTargetClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiTargetClient = client,
        )
        installSession(vm, this)
        val request = AstroWebUiGotoRequest(12.0, -4.0, "Target")
        vm.startAstroWebUiGoto(request)
        advanceUntilIdle()
        assertEquals(0, client.starts)
        assertIs<AstroWebUiTargetUiState.Failed>(vm.astroWebUiTarget)
        FeatureFlags.enable("astroWebUiTargetWrites")
        vm.previewAstroWebUiGoto(request)
        advanceUntilIdle()
        vm.startAstroWebUiGoto(request)
        advanceUntilIdle()
        assertEquals(1, client.starts)
        vm.disconnect()
        vm.preview.shutdown()
    }

    @Test
    fun compassAlignmentRemainsBlockedByDefault() = runTest {
        val client = FakeTargetClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiTargetClient = client,
        )
        installSession(vm, this)
        vm.alignAstroWebUiCompass(AstroWebUiAlignRequest(90.0))
        advanceUntilIdle()
        assertEquals(0, client.aligns)
        assertIs<AstroWebUiTargetUiState.Failed>(vm.astroWebUiTarget)
        vm.disconnect()
        vm.preview.shutdown()
    }
}
