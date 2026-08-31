package dev.openpolaris.core.session

import dev.openpolaris.core.astro.SessionMarker
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [InMemorySessionStore] (the default [SessionStore] backing the
 * reconnect prompt in issue #27 / 3c.4). The contract is small:
 *
 *  - [save] appends a new marker; saving with an existing id overwrites.
 *  - [loadAll] returns every marker, oldest first (insertion order).
 *  - [delete] removes by id; missing id is a no-op.
 *  - [latest] returns the marker with the largest capturedAtMs, not the
 *    most recently inserted — a back-dated marker must not surface as
 *    "the latest target the user was on".
 *
 * Thread-safety under [Mutex] is exercised indirectly: the test does not
 * spawn multiple coroutines, but every call goes through `withLock`, so
 * a missing lock would cause the build to fail at runtime. The unit test
 * is enough to guarantee the in-process behaviour for the Android build.
 */
class SessionStoreTest {

    private fun marker(
        id: String,
        capturedAtMs: Long = 1_700_000_000_000L,
        name: String = "M$id",
    ): SessionMarker {
        // RA/Dec are arbitrary but in-range so the data class init {} passes.
        // RA 1h, Dec +10° corresponds to a plausible deep-sky target.
        return SessionMarker(
            id = id,
            name = name,
            raHours = 1.0,
            decDeg = 10.0,
            capturedAtMs = capturedAtMs,
        )
    }

    @Test
    fun saveThenLoadAllReturnsIt() = runTest {
        val store = InMemorySessionStore()
        val m = marker("a")
        store.save(m)
        assertEquals(listOf(m), store.loadAll())
    }

    @Test
    fun multipleSavesKeepInsertionOrder() = runTest {
        val store = InMemorySessionStore()
        val a = marker("a", capturedAtMs = 100L)
        val b = marker("b", capturedAtMs = 200L)
        val c = marker("c", capturedAtMs = 300L)
        store.save(a); store.save(b); store.save(c)
        // Insertion order, NOT capturedAtMs order — loadAll() is a log
        // (insertion-ordered), latest() picks by capturedAtMs.
        assertEquals(listOf(a, b, c), store.loadAll())
    }

    @Test
    fun saveWithExistingIdOverwrites() = runTest {
        val store = InMemorySessionStore()
        val v1 = marker("a", capturedAtMs = 100L, name = "M31")
        val v2 = marker("a", capturedAtMs = 200L, name = "M31 — revisit")
        store.save(v1)
        store.save(v2)
        val all = store.loadAll()
        assertEquals(1, all.size, "overwrite must not duplicate the id")
        assertEquals(v2, all.single(), "overwrite must keep the latest payload")
        // And latest() picks by capturedAtMs so v2 wins regardless of insertion order
        assertEquals(v2, store.latest())
    }

    @Test
    fun deleteRemovesById() = runTest {
        val store = InMemorySessionStore()
        store.save(marker("a"))
        store.save(marker("b"))
        store.delete("a")
        val remaining = store.loadAll().map { it.id }
        assertEquals(listOf("b"), remaining)
    }

    @Test
    fun deleteOfMissingIdIsNoOp() = runTest {
        val store = InMemorySessionStore()
        store.save(marker("a"))
        store.delete("not-here") // must not throw
        assertEquals(listOf("a"), store.loadAll().map { it.id })
        assertEquals(1, store.loadAll().size)
    }

    @Test
    fun latestPicksLargestCapturedAtMsNotInsertionOrder() = runTest {
        val store = InMemorySessionStore()
        // Insert a high-captured marker first, then a back-dated one.
        val new = marker("new", capturedAtMs = 2_000L)
        val old = marker("old", capturedAtMs = 1_000L)
        store.save(new)
        store.save(old) // insertion order: new, old — but latest must be new
        assertEquals(new, store.latest(), "latest() must pick the largest capturedAtMs")
    }

    @Test
    fun latestReturnsNullOnEmptyStore() = runTest {
        val store = InMemorySessionStore()
        assertNull(store.latest())
    }

    @Test
    fun loadAllOnEmptyStoreReturnsEmpty() = runTest {
        val store = InMemorySessionStore()
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun saveThenDeleteThenLatestIsNull() = runTest {
        val store = InMemorySessionStore()
        val m = marker("only")
        store.save(m)
        assertNotNull(store.latest())
        store.delete("only")
        assertNull(store.latest())
    }
}