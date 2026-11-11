package dev.openpolaris.core.session

import dev.openpolaris.core.domain.ObservationProgrammePlanner
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObservationProgrammeJournalStoreTest {
    private lateinit var tempDir: Path
    private lateinit var store: ObservationProgrammeJournalStore

    private val plan = ObservationProgrammePlanner.Plan(
        kind = ObservationProgrammePlanner.Kind.LAPSE,
        steps = listOf(
            ObservationProgrammePlanner.Step(1, "Frame 1/2", 0L),
            ObservationProgrammePlanner.Step(2, "Frame 2/2", 5_000L),
        ),
    )

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("openpolaris-observation-journal-")
        store = ObservationProgrammeJournalStore(tempDir.resolve("programme.json").toString())
    }

    @AfterTest
    fun tearDown() {
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.deleteIfExists(it) }
    }

    private fun journal(
        phase: ObservationProgrammeJournal.Phase = ObservationProgrammeJournal.Phase.PAUSED,
        completedSteps: Int = 1,
    ) = ObservationProgrammeJournal(
        host = "192.168.0.1",
        port = 9090,
        demoMode = false,
        plan = plan,
        phase = phase,
        completedSteps = completedSteps,
        reason = "test",
        updatedAtEpochMs = 1234L,
    )

    @Test
    fun roundTripPreservesExactPlanAndRecoveryIdentity() {
        val expected = journal()
        assertTrue(store.write(expected).isSuccess)
        assertEquals(expected, store.read())
        assertTrue(expected.belongsTo("192.168.0.1", 9090, false))
        assertFalse(expected.belongsTo("192.168.0.2", 9090, false))
        assertFalse(expected.belongsTo("192.168.0.1", 9090, true))
    }

    @Test
    fun malformedUnknownAndOversizedFilesAreIgnored() {
        val path = tempDir.resolve("programme.json")
        Files.writeString(path, "{bad")
        assertNull(store.read())

        assertTrue(store.write(journal()).isSuccess)
        val valid = Files.readString(path)
        Files.writeString(path, valid.replace("\"schemaVersion\":1", "\"schemaVersion\":99"))
        assertNull(store.read())

        Files.writeString(path, "x".repeat(ObservationProgrammeJournalStore.DEFAULT_SIZE_LIMIT_BYTES + 1))
        assertNull(store.read())
    }

    @Test
    fun forgetIsIdempotent() {
        assertFalse(store.forget())
        assertTrue(store.write(journal()).isSuccess)
        assertTrue(store.forget())
        assertFalse(store.forget())
        assertNull(store.read())
    }
}
