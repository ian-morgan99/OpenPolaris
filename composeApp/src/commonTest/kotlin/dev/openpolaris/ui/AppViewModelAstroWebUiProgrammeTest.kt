package dev.openpolaris.ui

import dev.openpolaris.core.domain.AstroWebUiProgramme
import dev.openpolaris.core.domain.AstroWebUiProgrammeClient
import dev.openpolaris.core.domain.AstroWebUiProgrammeResult
import dev.openpolaris.core.domain.AstroWebUiProgrammeState
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
class AppViewModelAstroWebUiProgrammeTest {
    private class NoopConnection : Connection {
        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int = 0
        override fun close() = Unit
    }

    private class FakeProgrammeClient : AstroWebUiProgrammeClient {
        var calls = 0
        var port: Int? = null
        var running = true

        override suspend fun read(host: String, port: Int): AstroWebUiProgrammeResult {
            calls += 1
            this.port = port
            return AstroWebUiProgrammeResult.Success(
                if (running) {
                    AstroWebUiProgramme(
                        kind = "timelapse",
                        total = 10,
                        remaining = 7,
                        taken = 3,
                        stale = false,
                    )
                } else {
                    AstroWebUiProgramme(kind = "none")
                },
            )
        }
    }

    @AfterTest
    fun resetFlags() {
        dev.openpolaris.core.config.FeatureFlags.reset()
    }

    @Test
    fun programmeUsesDedicatedPreviewPortAndReportsProgress() = runTest {
        val client = FakeProgrammeClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiProgrammeClient = client,
        )
        val session = MountSession(
            connectionFactory = { NoopConnection() },
            host = "192.168.0.1",
            port = 9090,
            readerScope = this,
        )
        vm.testInstallSession(session)

        try {
            vm.refreshAstroWebUiProgramme()
            advanceUntilIdle()

            assertEquals(1, client.calls)
            assertEquals(8080, client.port)
            val state = assertIs<AstroWebUiProgrammeState.Ready>(vm.astroWebUiProgramme)
            assertEquals("timelapse", state.programme.kind)
            assertEquals(3L, state.programme.taken)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun programmeDoesNotCallClientWithoutAnInstalledSession() = runTest {
        val client = FakeProgrammeClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiProgrammeClient = client,
        )

        try {
            vm.refreshAstroWebUiProgramme()
            advanceUntilIdle()

            assertEquals(0, client.calls)
            assertIs<AstroWebUiProgrammeState.Failed>(vm.astroWebUiProgramme)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun visibleProgrammePollUsesFastCadenceForRunningProgramme() = runTest {
        val client = FakeProgrammeClient()
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiProgrammeClient = client,
        )
        val session = MountSession(
            connectionFactory = { NoopConnection() },
            host = "192.168.0.1",
            port = 9090,
            readerScope = this,
        )
        vm.testInstallSession(session)

        try {
            vm.startAstroWebUiProgrammePolling()
            runCurrent()
            assertEquals(1, client.calls)
            advanceTimeBy(999)
            runCurrent()
            assertEquals(1, client.calls)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, client.calls)
        } finally {
            vm.stopAstroWebUiProgrammePolling()
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

    @Test
    fun visibleProgrammePollUsesQuietCadenceWhenIdle() = runTest {
        val client = FakeProgrammeClient().apply { running = false }
        val vm = AppViewModel(
            scope = this,
            connectionFactory = { NoopConnection() },
            astroWebUiProgrammeClient = client,
        )
        val session = MountSession(
            connectionFactory = { NoopConnection() },
            host = "192.168.0.1",
            port = 9090,
            readerScope = this,
        )
        vm.testInstallSession(session)

        try {
            vm.startAstroWebUiProgrammePolling()
            runCurrent()
            assertEquals(1, client.calls)
            advanceTimeBy(3_999)
            runCurrent()
            assertEquals(1, client.calls)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, client.calls)
        } finally {
            vm.stopAstroWebUiProgrammePolling()
            vm.disconnect()
            vm.preview.shutdown()
        }
    }

}
