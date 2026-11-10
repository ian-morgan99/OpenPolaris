package dev.openpolaris.core.session

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dev.openpolaris.core.io.Utf8

/**
 * Small, versioned store for user-facing preferences.
 *
 * Preferences are deliberately separate from [SessionMarker]: connection
 * identity and visual/user choices have different lifecycles and should not
 * be copied into reconnect prompts or sent to a mount. A malformed or
 * unknown
 * file is ignored, while write failures are returned to the host.
 */
@Serializable
data class UserPreferences(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val dimMode: Boolean = false,
    val reducedMotion: Boolean = false,
    val observerLatitudeDeg: Double? = null,
    val observerLongitudeDeg: Double? = null,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

class UserPreferencesStore(
    private val filePath: String,
    private val json: Json = DEFAULT_JSON,
    private val sizeLimitBytes: Int = DEFAULT_SIZE_LIMIT_BYTES,
) {
    private val file = PlatformFile(filePath)

    fun read(): UserPreferences? {
        if (!file.exists() || !file.isReadable()) return null
        val bytes = try {
            file.readBytes()
        } catch (_: Exception) {
            return null
        }
        if (bytes.isEmpty() || bytes.size > sizeLimitBytes) return null
        val text = try {
            Utf8.decode(bytes, 0, bytes.size)
        } catch (_: Exception) {
            return null
        }
        val parsed = try {
            json.decodeFromString<UserPreferences>(text)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        return parsed.takeIf { it.schemaVersion == UserPreferences.CURRENT_SCHEMA_VERSION }
    }

    fun write(preferences: UserPreferences): Result<Unit> {
        val encoded = try {
            json.encodeToString(preferences.copy(schemaVersion = UserPreferences.CURRENT_SCHEMA_VERSION))
        } catch (_: SerializationException) {
            return Result.failure(UserPreferencesStoreException("preferences failed to serialize"))
        }
        val bytes = Utf8.encode(encoded)
        if (bytes.size > sizeLimitBytes) {
            return Result.failure(UserPreferencesStoreException("preferences exceed size limit"))
        }
        val parentPath = file.parentPath()
        if (parentPath != null) {
            val parent = PlatformFile(parentPath)
            if (!parent.exists()) {
                try {
                    parent.createDirectories()
                } catch (error: Exception) {
                    return Result.failure(UserPreferencesStoreException("could not create preference directory", error))
                }
            }
            if (parent.exists() && !parent.isWritable()) {
                return Result.failure(UserPreferencesStoreException("preference directory is not writable"))
            }
        }
        return try {
            file.writeBytes(bytes)
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(UserPreferencesStoreException("preference write failed", error))
        }
    }

    fun forget(): Boolean = file.deleteIfExists()

    companion object {
        const val DEFAULT_SIZE_LIMIT_BYTES: Int = 2048
        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = false
        }
    }
}

class UserPreferencesStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
