package dev.openpolaris.core.session

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dev.openpolaris.core.io.Utf8

/**
 * File-backed persistence for [SessionMarker]. 3c.2/3c.3 from issue #7.
 *
 * Design notes:
 *
 *  - The store takes a path [String] in the constructor, never derives it. The
 *    `expect/actual` `defaultSessionPath()` lives in `dev.openpolaris.core.session.path`
 *    and is injected by the platform (JVM: `~/.openpolaris/session.json`;
 *    Android: `${filesDir}/openpolaris/session.json` once 3c.4 wires
 *    `Context`). This makes JVM tests trivial: pass a temp-dir path string
 *    and never touch the real user home.
 *  - [read] returns `null` for any "not a usable marker" case — missing file,
 *    empty file, oversized, wrong shape, wrong schema, wrong checksum. It
 *    never throws. This is a contract: callers can write `store.read()?.let
 *    { ... } ?: freshStart()` and trust the right-hand branch.
 *  - [write] returns a [Result] so a read-only filesystem, full disk, or
 *    permission denial surfaces as a value, not a crash. The marker's
 *    [SessionMarker.checksum] is filled in by [FileSessionStore] from the
 *    canonical form, so callers do not need to remember to call
 *    [SessionMarkerCodec.withChecksum] themselves.
 *  - A 4 KiB cap on the file (well above the ~250-byte serialized form)
 *    rejects a malicious or accidental 100 MB JSON blob. The cap is
 *    overridable in tests via [sizeLimitBytes] but the default is final.
 *  - Concurrent writes from two app instances racing on the same file are
 *    best-effort: the second writer wins, the first reader sees a fresh
 *    file. We do not take a file lock; the OS layer (user home, filesDir)
 *    is single-app by construction.
 *
 * The I/O itself is delegated to [PlatformFile], an `expect class` that
 * `actual`s onto `java.nio.file.Path` on JVM and `android.content.Context`
 * on Android. We do not pull in `kotlin.io.path` because it is JVM-only.
 */
class FileSessionStore(
    private val filePath: String,
    private val json: Json = DEFAULT_JSON,
    private val sizeLimitBytes: Int = DEFAULT_SIZE_LIMIT_BYTES,
) {
    private val file: PlatformFile = PlatformFile(filePath)

    /** Read the persisted marker, or `null` if there isn't a usable one. */
    fun read(): SessionMarker? {
        if (!file.exists()) return null
        if (!file.isReadable()) return null
        val bytes = try {
            file.readBytes()
        } catch (_: Exception) {
            // Permission denied, file disappeared between exists() and readBytes(), etc.
            return null
        }
        if (bytes.size > sizeLimitBytes) return null
        if (bytes.isEmpty()) return null

        val text = try {
            Utf8.decode(bytes, 0, bytes.size)
        } catch (_: Exception) {
            return null
        }

        val parsed = try {
            json.decodeFromString<SessionMarker>(text)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            // kotlinx.serialization can wrap unexpected values in IAE.
            return null
        }

        // Reject unknown schema versions even if the JSON parsed.
        if (parsed.schemaVersion != SessionMarker.SCHEMA_VERSION) return null

        // Reject checksum mismatch — the corruption detector.
        if (!SessionMarkerCodec.verify(parsed)) return null

        return parsed
    }

    /**
     * Persist [marker]. Returns `Result.failure` on any I/O error; `Result.success`
     * on a clean write. The [marker]'s [SessionMarker.checksum] is overwritten
     * with the freshly-computed value so the file is always self-consistent.
     */
    fun write(marker: SessionMarker): Result<Unit> {
        val withChecksum = SessionMarkerCodec.withChecksum(marker)
        val encoded = try {
            json.encodeToString(withChecksum)
        } catch (_: SerializationException) {
            return Result.failure(SessionStoreException("marker failed to serialize"))
        }

        // Byte size cap on the encoded form, too — symmetric with the read cap.
        if (Utf8.encode(encoded).size > sizeLimitBytes) {
            return Result.failure(SessionStoreException("marker exceeds size limit"))
        }

        val parentPath = file.parentPath()
        if (parentPath != null) {
            val parent = PlatformFile(parentPath)
            if (!parent.exists()) {
                try {
                    parent.createDirectories()
                } catch (e: Exception) {
                    return Result.failure(SessionStoreException("could not create parent dir", e))
                }
            }
            // Re-check writability after mkdir (Files.isWritable can return false
            // for a non-existent directory; once it exists the check is meaningful).
            if (parent.exists() && !parent.isWritable()) {
                return Result.failure(SessionStoreException("parent dir is not writable"))
            }
        }

        return try {
            file.writeBytes(Utf8.encode(encoded))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(SessionStoreException("write failed", e))
        }
    }

    /**
     * Delete the file. Used by "forget this mount" in the UI and by tests for
     * setup/teardown. Returns `true` if a file was actually removed; `false`
     * if there was nothing to remove (also a non-error case).
     */
    fun forget(): Boolean = file.deleteIfExists()

    companion object {
        /** Symmetric with the read cap. 4 KiB is ~16× the typical serialized form. */
        const val DEFAULT_SIZE_LIMIT_BYTES: Int = 4096

        /**
         * Default [Json] used by [FileSessionStore] when the caller does not pass
         * one. `ignoreUnknownKeys = true` so a v1 reader does not crash on a
         * v2 marker's extra field; `isLenient = false` so a hand-typed
         * `{"foo":}` does not silently parse.
         */
        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            prettyPrint = false
            isLenient = false
            encodeDefaults = true
        }
    }
}

/** Throws nothing. Read-side failures all fold into `null`; write-side fold into [Result.failure]. */
class SessionStoreException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
