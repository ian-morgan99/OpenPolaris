package dev.openpolaris.ui

import dev.openpolaris.core.domain.Connection
import dev.openpolaris.core.domain.MountSession
import dev.openpolaris.core.domain.ObservationProgrammeExecutor
import dev.openpolaris.core.domain.ObservationProgrammePlanner
import dev.openpolaris.core.domain.PreviewTransport
import dev.openpolaris.core.session.ObservationProgrammeJournal
import dev.openpolaris.core.session.ObservationProgrammeJournalStore
import dev.openpolaris.core.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelObservationRecoveryTest {
    private class IdleConnection : Connection {
        private val frames = Channel<ByteArray>(Channel.BUFFERED)

        override suspend fun connect(host: String, port: Int, timeoutMs: Int) = Unit
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val bytes = frames.receive()
            bytes.copyInto(buffer)
            return bytes.size
        }
        override fun close() {
            frames.close()
        }
    }

    private var tempDir = Files.createTempDirectory("openpolaris-observation-vm-")

    @AfterTest
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun pausedJournalLoadsAndRequiresExplicitRecoveryBeforeResume() = runTest(UnconfinedTestDispatcher()) {
        val journalStore = ObservationProgrammeJournalStore(tempDir.resolve("programme.json").toString())
        val sessionStore = SessionStore(tempDir.resolve("session.json").toString())
        val plan = ObservationProgrammePlanner.Plan(
            ObservationProgrammePlanner.Kind.LAPSE,
            listOf(
                ObservationProgrammePlanner.Step(1, "Frame 1/2", 0L),
                ObservationProgrammePlanner.Step(2, "Frame 2/2", 0L),
            ),
        )
        journalStore.write(
            ObservationProgrammeJournal(
                host = "192.168.0.1",
                port = 9090,
                demoMode = false,
                plan = plan,
                phase = ObservationProgrammeJournal.Phase.PAUSED,
                completedSteps = 1,
                updatedAtEpochMs = 100L,
            ),
        )

        val dispatcher = UnconfinedTestDispatcher(coroutineContext[TestCoroutineScheduler])
        val vm = AppViewModel(
            scope = backgroundScope,
            connectionFactory = { IdleConnection() },
            sessionStore = sessionStore,
            observationJournalStore = journalStore,
            ioDispatcher = dispatcher,
            sessionReaderScope = backgroundScope,
            previewTransportFactory = { _, _ ->
                object : PreviewTransport {
                    override fun start(host: String, port: Int, path: String) = Unit
                    override fun stop() = Unit
                }
            },
        )
        val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = MountSession(
            connectionFactory = { IdleConnection() },
            readerScope = sessionScope,
        )
        try {
            vm.testInstallCameraAndIntervalometer(session)
            runCurrent()
            assertNotNull(vm.observationProgrammeRecovery)
            assertTrue(vm.restoreObservationProgramme())
            assertNull(vm.observationProgrammeRecovery)
            assertIs<ObservationProgrammeExecutor.State.Paused>(vm.observationProgrammeState)
        } finally {
            vm.disconnect()
            vm.preview.shutdown()
            session.shutdown()
        }
    }
}
