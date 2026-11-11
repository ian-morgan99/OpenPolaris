package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiFocusParameters
import dev.openpolaris.core.domain.AstroWebUiHdrParameters
import dev.openpolaris.core.domain.AstroWebUiLapseParameters
import dev.openpolaris.core.domain.AstroWebUiPanoramaParameters
import dev.openpolaris.core.domain.AstroWebUiProgramme
import dev.openpolaris.core.domain.AstroWebUiProgrammeClient
import dev.openpolaris.core.domain.AstroWebUiProgrammeControlClient
import dev.openpolaris.core.domain.AstroWebUiProgrammeControlResult
import dev.openpolaris.core.domain.AstroWebUiProgrammeResult
import dev.openpolaris.core.domain.AstroWebUiProgrammeStartClient
import dev.openpolaris.core.domain.AstroWebUiProgrammeStartResult
import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MotionOwner
import dev.openpolaris.core.domain.MotionState
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
class AppViewModelAstroWebUiProgrammeStartTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeProgrammeClient : AstroWebUiProgrammeClient {
        var running = true
        var calls = 0

        override suspend fun read(host: String, port: Int): AstroWebUiProgrammeResult {
            calls++
            return AstroWebUiProgrammeResult.Success(
                if (running) AstroWebUiProgramme(kind = "timelapse")
                else AstroWebUiProgramme(kind = "none"),
            )
        }
    }

    private class FakeProgrammeControlClient(
        private val programme: FakeProgrammeClient,
    ) : AstroWebUiProgrammeControlClient {
        var cancelCalls = 0

        override suspend fun cancel(host: String, port: Int): AstroWebUiProgrammeControlResult {
            cancelCalls++
            programme.running = false
            return AstroWebUiProgrammeControlResult.Accepted
        }

        override suspend fun setPanoramaPaused(
            host: String,
            port: Int,
            paused: Boolean,
        ) = AstroWebUiProgrammeControlResult.Accepted

        override suspend fun setPanoramaInterval(
            host: String,
            port: Int,
            seconds: Int,
        ) = AstroWebUiProgrammeControlResult.Accepted
    }

    private class FakeStartClient : AstroWebUiProgrammeStartClient {
        var calls = 0
        var releaseLapse: CompletableDeferred<Unit>? = null

        private fun accepted(kind: AstroWebUiProgrammeStartResult.Kind) =
            AstroWebUiProgrammeStartResult.Accepted(kind)

        override suspend fun startLapse(host: String, port: Int, parameters: AstroWebUiLapseParameters): AstroWebUiProgrammeStartResult {
            calls++
            releaseLapse?.let { gate ->
                withContext(NonCancellable) { gate.await() }
            }
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
    fun acceptedResidentStartClaimsSharedMotionAndBlocksAnotherStart() = runTest {
        FeatureFlags.enable("astroWebUiProgrammeStarts")
        FeatureFlags.enable("astroWebUiProgrammeStatus")
        val startClient = FakeStartClient()
        val programmeClient = FakeProgrammeClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiProgrammeClient = programmeClient,
            astroWebUiProgrammeStartClient = startClient,
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

            assertEquals(1, startClient.calls)
            assertEquals(
                MotionOwner.PROGRAMME,
                assertIs<MotionState.Owned>(vm.motionState.value).lease.owner,
            )

            vm.startAstroWebUiPanorama(AstroWebUiPanoramaParameters(2, 2, 1.0, 1.0))
            advanceUntilIdle()

            assertEquals(1, startClient.calls)
            assertIs<dev.openpolaris.core.domain.AstroWebUiProgrammeStartState.Failed>(
                vm.astroWebUiProgrammeStartState,
            )
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun disconnectCancelsResidentProgrammeAndRequiresIdleReadback() = runTest {
        FeatureFlags.enable("astroWebUiProgrammeStarts")
        FeatureFlags.enable("astroWebUiProgrammeStatus")
        val startClient = FakeStartClient()
        val programmeClient = FakeProgrammeClient()
        val controlClient = FakeProgrammeControlClient(programmeClient)
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiProgrammeClient = programmeClient,
            astroWebUiProgrammeControlClient = controlClient,
            astroWebUiProgrammeStartClient = startClient,
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
            assertEquals(MotionOwner.PROGRAMME, assertIs<MotionState.Owned>(vm.motionState.value).lease.owner)

            vm.disconnect()

            assertEquals(1, controlClient.cancelCalls)
            assertEquals(MotionState.Idle, vm.motionState.value)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun disconnectDropsLateProgrammeStartResult() = runTest {
        FeatureFlags.enable("astroWebUiProgrammeStarts")
        val client = FakeStartClient()
        val gate = CompletableDeferred<Unit>()
        client.releaseLapse = gate
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
            runCurrent()
            assertEquals(1, client.calls)

            vm.disconnect()
            gate.complete(Unit)
            advanceUntilIdle()

            assertIs<dev.openpolaris.core.domain.AstroWebUiProgrammeStartState.Idle>(
                vm.astroWebUiProgrammeStartState,
            )
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
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
