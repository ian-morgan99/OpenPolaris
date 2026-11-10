package dev.openpolaris.ui

import dev.openpolaris.core.config.FeatureFlags
import dev.openpolaris.core.domain.AstroWebUiGrailParameters
import dev.openpolaris.core.domain.AstroWebUiMotionProgrammeClient
import dev.openpolaris.core.domain.AstroWebUiMotionProgrammeResult
import dev.openpolaris.core.domain.AstroWebUiMotionProgrammeState
import dev.openpolaris.core.domain.AstroWebUiPlcKeyRequest
import dev.openpolaris.core.domain.AstroWebUiPlcParameters
import dev.openpolaris.core.domain.AstroWebUiPathLapseParameters
import dev.openpolaris.core.domain.AstroWebUiPathWaypointRequest
import dev.openpolaris.core.domain.AstroWebUiProgramme
import dev.openpolaris.core.domain.AstroWebUiProgrammeClient
import dev.openpolaris.core.domain.AstroWebUiProgrammeResult
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
class AppViewModelAstroWebUiMotionProgrammesTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeMotionClient : AstroWebUiMotionProgrammeClient {
        var previewCalls = 0
        var startCalls = 0
        var lastPort: Int? = null
        var releaseStart: CompletableDeferred<Unit>? = null

        override suspend fun addPlcKey(host: String, port: Int, request: AstroWebUiPlcKeyRequest) = count(host, port)
        override suspend fun clearPlcKeys(host: String, port: Int) = count(host, port)
        override suspend fun previewPlc(host: String, port: Int, parameters: AstroWebUiPlcParameters) = preview(host, port)
        override suspend fun startPlc(host: String, port: Int, parameters: AstroWebUiPlcParameters): AstroWebUiMotionProgrammeResult {
            startCalls++
            lastPort = port
            releaseStart?.let { gate -> withContext(NonCancellable) { gate.await() } }
            return AstroWebUiMotionProgrammeResult.Accepted
        }
        override suspend fun addPathWaypoint(host: String, port: Int, request: AstroWebUiPathWaypointRequest) = count(host, port)
        override suspend fun clearPathWaypoints(host: String, port: Int) = count(host, port)
        override suspend fun previewPathLapse(host: String, port: Int, parameters: AstroWebUiPathLapseParameters) = preview(host, port)
        override suspend fun startPathLapse(host: String, port: Int, parameters: AstroWebUiPathLapseParameters) = accepted(host, port)
        override suspend fun previewGrail(host: String, port: Int, parameters: AstroWebUiGrailParameters) = preview(host, port)
        override suspend fun applyGrail(host: String, port: Int, parameters: AstroWebUiGrailParameters) = accepted(host, port)
        override suspend fun disableGrail(host: String, port: Int) = AstroWebUiMotionProgrammeResult.Disabled(false)
        override suspend fun grailStatus(host: String, port: Int) = AstroWebUiMotionProgrammeResult.Status(false, null)

        private fun count(host: String, port: Int): AstroWebUiMotionProgrammeResult {
            lastPort = port
            return AstroWebUiMotionProgrammeResult.Count(1)
        }

        private fun preview(host: String, port: Int): AstroWebUiMotionProgrammeResult {
            previewCalls++
            lastPort = port
            return AstroWebUiMotionProgrammeResult.Preview("@272;", 1, inferred = true)
        }

        private fun accepted(host: String, port: Int): AstroWebUiMotionProgrammeResult {
            lastPort = port
            return AstroWebUiMotionProgrammeResult.Accepted
        }
    }

    private class FakeProgrammeClient : AstroWebUiProgrammeClient {
        var calls = 0
        override suspend fun read(host: String, port: Int): AstroWebUiProgrammeResult {
            calls++
            return AstroWebUiProgrammeResult.Success(AstroWebUiProgramme("plc"))
        }
    }

    @AfterTest
    fun resetFlags() = FeatureFlags.reset()

    private fun plc() = AstroWebUiPlcParameters(5.0, 2, hold = false)

    @Test
    fun extendedProgrammeWritesRemainBlockedByDefault() = runTest {
        val client = FakeMotionClient()
        val vm = AppViewModel(scope = this, connectionFactory = { NoopConnection() }, astroWebUiMotionProgrammeClient = client)
        vm.previewAstroWebUiPlc(plc())
        assertEquals(0, client.previewCalls)
        assertIs<AstroWebUiMotionProgrammeState.Failed>(vm.astroWebUiMotionProgrammeState)
        vm.disconnect()
        vm.preview.shutdown()
    }

    @Test
    fun enabledPreviewUsesPreviewPortAndPreservesInferredEvidence() = runTest {
        FeatureFlags.enable("astroWebUiProgrammeStarts")
        val client = FakeMotionClient()
        val vm = AppViewModel(scope = this, connectionFactory = { NoopConnection() }, astroWebUiMotionProgrammeClient = client)
        vm.testInstallSession(
            MountSession(
                connectionFactory = { NoopConnection() },
                host = "192.168.0.1",
                port = 9090,
                readerScope = this,
            ),
        )
        try {
            vm.previewAstroWebUiPlc(plc())
            advanceUntilIdle()
            assertEquals(1, client.previewCalls)
            assertEquals(8080, client.lastPort)
            val state = assertIs<AstroWebUiMotionProgrammeState.Preview>(vm.astroWebUiMotionProgrammeState)
            assertEquals(true, state.inferred)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun disconnectDropsLateExtendedProgrammeStart() = runTest {
        FeatureFlags.enable("astroWebUiProgrammeStarts")
        val client = FakeMotionClient()
        val gate = CompletableDeferred<Unit>()
        client.releaseStart = gate
        val programmeClient = FakeProgrammeClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiMotionProgrammeClient = client,
            astroWebUiProgrammeClient = programmeClient,
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
            vm.startAstroWebUiPlc(plc())
            runCurrent()
            assertIs<AstroWebUiMotionProgrammeState.Loading>(vm.astroWebUiMotionProgrammeState)
            vm.disconnect()
            gate.complete(Unit)
            advanceUntilIdle()
            assertIs<AstroWebUiMotionProgrammeState.Idle>(vm.astroWebUiMotionProgrammeState)
            assertEquals(0, programmeClient.calls)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }
}
