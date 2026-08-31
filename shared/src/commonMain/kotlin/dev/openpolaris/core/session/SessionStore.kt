package dev.openpolaris.core.session

import dev.openpolaris.core.astro.SessionMarker
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persistent record of the targets a user has chosen. Defined by issue #25
 * (3c.2, sub-claim of #7 "session persistence"). Persists across process
 * restarts so that #27 (3c.4, "Return to M31?") can re-offer the most
 * recent target after a Wi-Fi drop + reconnect.
 *
 * Implementations are **not** required to be thread-safe; the [Mutex]
 * below is the shared default for in-memory stores. A real on-disk
 * store (Android `DataStore`, JVM flat file, iOS `NSUserDefaults`)
 * should serialise writes itself and rethrow IO errors.
 *
 * All methods are `suspend` so that an implementation can do IO without
 * blocking the calling dispatcher. Reads should be cheap (cached or
 * in-memory). Writes must be durable before returning.
 */
interface SessionStore {
    /** Append-or-replace [marker] by [SessionMarker.id]. */
    suspend fun save(marker: SessionMarker)

    /** All markers, oldest first. Empty list if the store is empty. */
    suspend fun loadAll(): List<SessionMarker>

    /** Remove the marker with the given [id]. No-op if not present. */
    suspend fun delete(id: String)

    /**
     * The most recently captured marker, or `null` if the store is empty.
     * "Most recent" is by [SessionMarker.capturedAtMs], not by insertion
     * order — a back-dated marker is not a "latest" candidate.
     */
    suspend fun latest(): SessionMarker?
}

/**
 * Process-local fallback. Used in tests and in production when no
 * platform store is wired. List is held behind a [Mutex] so that
 * concurrent [save]/[loadAll] calls do not race.
 *
 * Not durable: a process restart loses the data. The reconnect-prompt
 * acceptance criterion in #27 talks about a "previous session" in the
 * same process for the unit test, and a persistent store is the next
 * sub-issue.
 */
class InMemorySessionStore : SessionStore {
    private val mutex = Mutex()
    private val byId = LinkedHashMap<String, SessionMarker>()

    override suspend fun save(marker: SessionMarker) = mutex.withLock {
        byId[marker.id] = marker
    }

    override suspend fun loadAll(): List<SessionMarker> = mutex.withLock {
        byId.values.toList()
    }

    override suspend fun delete(id: String) {
        mutex.withLock {
            byId.remove(id)
        }
    }

    override suspend fun latest(): SessionMarker? = mutex.withLock {
        byId.values.maxByOrNull { it.capturedAtMs }
    }
}
