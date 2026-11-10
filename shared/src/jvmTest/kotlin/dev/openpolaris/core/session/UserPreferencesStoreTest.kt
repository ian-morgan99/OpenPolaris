package dev.openpolaris.core.session

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserPreferencesStoreTest {
    private lateinit var tempDir: Path
    private lateinit var storePath: Path
    private lateinit var store: UserPreferencesStore

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("openpolaris-preferences-")
        storePath = tempDir.resolve("preferences.json")
        store = UserPreferencesStore(storePath.toString())
    }

    @AfterTest
    fun tearDown() {
        runCatching {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun missingAndMalformedFilesAreUnknown() {
        assertNull(store.read())
        Files.writeString(storePath, "{bad")
        assertNull(store.read())
    }

    @Test
    fun roundTripPreservesUserChoicesAndCreatesParents() {
        val nested = UserPreferencesStore(tempDir.resolve("nested/preferences.json").toString())
        val expected = UserPreferences(dimMode = true, reducedMotion = true)
        assertTrue(nested.write(expected).isSuccess)
        assertEquals(expected, nested.read())
    }

    @Test
    fun unknownSchemaIsIgnored() {
        Files.writeString(storePath, """{"schemaVersion":99,"dimMode":true,"reducedMotion":true}""")
        assertNull(store.read())
    }

    @Test
    fun oversizedAndZeroLimitWritesFailClosed() {
        val limited = UserPreferencesStore(storePath.toString(), sizeLimitBytes = 0)
        assertTrue(limited.write(UserPreferences(dimMode = true)).isFailure)
        Files.writeString(storePath, "x".repeat(UserPreferencesStore.DEFAULT_SIZE_LIMIT_BYTES + 1))
        assertNull(store.read())
    }

    @Test
    fun forgetIsIdempotent() {
        assertFalse(store.forget())
        assertTrue(store.write(UserPreferences(dimMode = true)).isSuccess)
        assertTrue(store.forget())
        assertFalse(store.forget())
        assertNull(store.read())
    }

    @Test
    fun writeFailureIsReturnedWhenParentIsAFile() {
        val blocker = tempDir.resolve("blocker")
        Files.writeString(blocker, "not a directory")
        val bad = UserPreferencesStore(blocker.resolve("preferences.json").toString())
        assertTrue(bad.write(UserPreferences()).isFailure)
        assertNotNull(bad)
    }
}
