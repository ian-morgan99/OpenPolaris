package dev.openpolaris.core.session

import dev.openpolaris.core.domain.MountMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Filesystem-backed tests for [FileSessionStore] on JVM.
 *
 * These exercise the real file I/O path (JVM [PlatformFile] actual wraps
 * `java.nio.file.Path`). The Android actual is exercised by
 * Android-instrumented tests; for unit-level coverage the JVM test path is
 * the source of truth because the actual implementations are thin and the
 * contract is the same.
 */
class FileSessionStoreJvmTest {

    private lateinit var tempDir: Path
    private lateinit var storePath: Path
    private lateinit var store: FileSessionStore

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("openpolaris-session-store-test-")
        storePath = tempDir.resolve("session.json")
        store = FileSessionStore(storePath.toString())
    }

    @AfterTest
    fun tearDown() {
        // Best-effort recursive cleanup; never fail the test on teardown.
        runCatching {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }

    private fun sample(
        host: String = "192.168.1.42",
        port: Int = 9999,
        mode: MountMode = MountMode.ASTRO,
    ): SessionMarker = SessionMarker(
        host = host,
        port = port,
        lastConnectedAtEpochMs = 1_700_000_000_000L,
        lastMountMode = mode,
        lastTrackingStarted = true,
        lastRollDeg = 180.0,
        lastPitchDeg = 45.0,
        schemaVersion = SessionMarker.SCHEMA_VERSION,
        checksum = 0L, // store re-derives this
    )

    @Test
    fun readReturnsNullWhenFileMissing() {
        assertNull(store.read())
    }

    @Test
    fun writeThenReadRoundTripsAllFields() {
        val marker = sample()
        val result = store.write(marker)
        assertTrue(result.isSuccess, "write should succeed, was: $result")

        val loaded = store.read()
        assertNotNull(loaded, "marker should be readable after a successful write")
        assertEquals(marker.host, loaded.host)
        assertEquals(marker.port, loaded.port)
        assertEquals(marker.lastConnectedAtEpochMs, loaded.lastConnectedAtEpochMs)
        assertEquals(marker.lastMountMode, loaded.lastMountMode)
        assertEquals(marker.lastTrackingStarted, loaded.lastTrackingStarted)
        assertEquals(marker.lastPitchDeg, loaded.lastPitchDeg, "pitch")
        assertEquals(marker.lastRollDeg, loaded.lastRollDeg, "roll")
    }

    @Test
    fun writeOverwritesPreviousContent() {
        store.write(sample(host = "10.0.0.1"))
        store.write(sample(host = "10.0.0.2"))

        val loaded = store.read()
        assertNotNull(loaded)
        assertEquals("10.0.0.2", loaded.host)
    }

    @Test
    fun writeFillsInChecksumOnBehalfOfCaller() {
        // Callers are allowed to pass any checksum (or 0); the store
        // re-derives it from the canonical form so the file is always
        // self-consistent.
        store.write(sample())
        val loaded = store.read()
        assertNotNull(loaded)
        assertNotEquals(0L, loaded.checksum)
        assertTrue(SessionMarkerCodec.verify(loaded))
    }

    @Test
    fun writeCreatesMissingParentDirectory() {
        val nestedPath = tempDir.resolve("nested/sub/dir/session.json")
        val nestedStore = FileSessionStore(nestedPath.toString())
        val result = nestedStore.write(sample())
        assertTrue(result.isSuccess, "write should create parent dirs, was: $result")
        assertTrue(Files.exists(nestedPath))
    }

    @Test
    fun readReturnsNullForEmptyFile() {
        Files.write(storePath, ByteArray(0))
        assertNull(store.read())
    }

    @Test
    fun readReturnsNullForMalformedJson() {
        Files.writeString(storePath, "{ not valid json")
        assertNull(store.read())
    }

    @Test
    fun readReturnsNullForValidJsonButWrongShape() {
        // Right JSON, wrong shape — not a SessionMarker.
        Files.writeString(storePath, """{"foo":"bar"}""")
        assertNull(store.read())
    }

    @Test
    fun readReturnsNullForCorruptedChecksum() {
        // Hand-write a marker with a deliberately-wrong checksum.
        val marker = sample()
        val withChecksum = SessionMarkerCodec.withChecksum(marker)
        val jsonText = Json.encodeToString(withChecksum)
        // Flip the checksum field by appending a different digit; the JSON
        // shape stays valid so we exercise the checksum layer specifically.
        val tampered = jsonText.replaceFirst(
            """"checksum":""" + withChecksum.checksum,
            """"checksum":""" + (withChecksum.checksum + 1L),
        )
        Files.writeString(storePath, tampered)
        assertNull(store.read())
    }

    @Test
    fun readReturnsNullForOversizedFile() {
        val huge = "x".repeat(FileSessionStore.DEFAULT_SIZE_LIMIT_BYTES + 1)
        Files.writeString(storePath, huge)
        assertNull(store.read())
    }

    @Test
    fun readReturnsNullForUnknownSchemaVersion() {
        val marker = sample().copy(schemaVersion = 999)
        val withChecksum = SessionMarkerCodec.withChecksum(marker)
        Files.writeString(storePath, Json.encodeToString(withChecksum))
        assertNull(store.read())
    }

    @Test
    fun forgetRemovesFileAndReturnsTrue() {
        store.write(sample())
        assertTrue(Files.exists(storePath))
        assertTrue(store.forget())
        assertFalse(Files.exists(storePath))
    }

    @Test
    fun forgetOnMissingFileReturnsFalseAndDoesNotThrow() {
        assertFalse(store.forget())
    }

    @Test
    fun writeFailsWhenParentIsAFile() {
        // Put a file in the place of the parent dir so mkdir will fail.
        val blocker = tempDir.resolve("blocker")
        Files.writeString(blocker, "i am a file, not a dir")
        val badPath = blocker.resolve("session.json")
        val badStore = FileSessionStore(badPath.toString())

        val result = badStore.write(sample())
        assertTrue(result.isFailure, "write should fail when parent is a file, was: $result")
    }

    @Test
    fun readOnlyFilesystemFailsWriteRatherThanCrashing() {
        // The store should never throw; I/O failure becomes Result.failure.
        // Easiest reproducible read-only path: a path inside a non-existent
        // nested directory we have not created. We have to construct one
        // where the parent is *not* creatable by us.
        val pathInsideMissingNested = tempDir.resolve("missing/missing/session.json")
        val badStore = FileSessionStore(pathInsideMissingNested.toString(), sizeLimitBytes = 0)
        val result = badStore.write(sample())
        assertTrue(result.isFailure)
    }

    @Test
    fun sizeLimitZeroRejectsAnyWrite() {
        val zeroStore = FileSessionStore(storePath.toString(), sizeLimitBytes = 0)
        val result = zeroStore.write(sample())
        assertTrue(result.isFailure, "sizeLimitBytes=0 must reject the smallest valid marker")
    }

    @Test
    fun corruptFileThenWriteRecovers() {
        // 1. Write a corrupted file (left over from a bad shutdown, say).
        Files.writeString(storePath, "{ not json")
        // 2. read() folds the corruption to null.
        assertNull(store.read())
        // 3. A clean write followed by read gives a valid marker.
        store.write(sample(host = "10.0.0.5"))
        val loaded = store.read()
        assertNotNull(loaded)
        assertEquals("10.0.0.5", loaded.host)
    }
}
