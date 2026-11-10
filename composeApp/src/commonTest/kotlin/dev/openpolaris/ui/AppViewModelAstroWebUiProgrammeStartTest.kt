package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiFocusParameters
import dev.openpolaris.core.domain.AstroWebUiHdrParameters
import dev.openpolaris.core.domain.AstroWebUiLapseParameters
import dev.openpolaris.core.domain.AstroWebUiPanoramaParameters
import dev.openpolaris.core.domain.AstroWebUiProgrammeStartClient
import dev.openpolaris.core.domain.AstroWebUiProgrammeStartResult
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
class AppViewModelAstroWebUiProgrammeStartTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeStartClient : AstroWebUiProgrammeStartClient {
        var calls = 0

        private fun accepted(kind: AstroWebUiProgrammeStartResult.Kind) =
            AstroWebUiProgrammeStartResult.Accepted(kind)

        override suspend fun startLapse(host: String, port: Int, parameters: AstroWebUiLapseParameters): AstroWebUiProgrammeStartResult {
            calls++
            return accepted(AstroWebUiProgrammeStartResult.Kind.LAPSE)
        }

        override suspend fun previewPanorama(host: String, port: Int, parameters: AstroWebUiPanoramaParameters): AstroWebUiProgrammeStartResult {
            calls++
            return AstroWebUiProgrammeStartResult.Preview(AstroWebUiProgrammeStartResult.Kind.PANORAMA)
        }

        override suspend fun startPanorama(host: String, port: Int, parameters: AstroWebUiPanoramaParameters): AstroWebUiProgrammeStartResult {
            calls++
            return accepted(AstroWebUiProgrammeStartResult.Kind.PANORAMA)
        }

        override suspend fun previewHdr(host: String, port: Int, parameters: AstroWebUiHdrParameters): AstroWebUiProgrammeStartResult {
            calls++
            return AstroWebUiProgrammeStartResult.Preview(AstroWebUiProgrammeStartResult.Kind.HDR)
        }

        override suspend fun startHdr(host: String, port: Int, parameters: AstroWebUiHdrParameters): AstroWebUiProgrammeStartResult {
            calls++
            return accepted(AstroWebUiProgrammeStartResult.Kind.HDR)
        }

        override suspend fun validateFocus(host: String, port: Int, parameters: AstroWebUiFocusParameters): AstroWebUiProgrammeStartResult {
            calls++
            return AstroWebUiProgrammeStartResult.Preview(AstroWebUiProgrammeStartResult.Kind.FOCUS)
        }

        override suspend fun previewFocus(host: String, port: Int, parameters: AstroWebUiFocusParameters): AstroWebUiProgrammeStartResult {
            calls++
            return AstroWebUiProgrammeStartResult.Preview(AstroWebUiProgrammeStartResult.Kind.FOCUS)
        }

        override suspend fun startFocus(host: String, port: Int, parameters: AstroWebUiFocusParameters): AstroWebUiProgrammeStartResult {
            calls++
            return accepted(AstroWebUiProgrammeStartResult.Kind.FOCUS)
        }
    }

    @AfterTest
    fun resetFlags() {
        FeatureFlags.reset()
    }

    @Test
    fun programmeStartIsBlockedByDefaultWithoutCallingClient() = runTest {
        val client = FakeStartClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiProgrammeStartClient = client,
        )

        vm.startAstroWebUiLapse(AstroWebUiLapseParameters(intervalSeconds = 5.0, shots = 2))

        assertEquals(0, client.calls)
        assertIs<dev.openpolaris.core.domain.AstroWebUiProgrammeStartState.Failed>(
            vm.astroWebUiProgrammeStartState,
        )
        vm.disconnect()
        vm.preview.shutdown()
    }

    @Test
    fun enabledStartUsesPreviewPortAndReportsAcceptance() = runTest {
        FeatureFlags.enable("astroWebUiProgrammeStarts")
        val client = FakeStartClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiProgrammeStartClient = client,
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
            vm.startAstroWebUiLapse(AstroWebUiLapseParameters(intervalSeconds = 5.0, shots = 2))
            advanceUntilIdle()

            assertEquals(1, client.calls)
            assertIs<dev.openpolaris.core.domain.AstroWebUiProgrammeStartState.Accepted>(
                vm.astroWebUiProgrammeStartState,
            )
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
