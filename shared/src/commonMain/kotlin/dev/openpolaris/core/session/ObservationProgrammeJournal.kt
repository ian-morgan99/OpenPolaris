package dev.openpolaris.core.session

import dev.openpolaris.core.domain.ObservationProgrammePlanner
import dev.openpolaris.core.io.Utf8
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A durable checkpoint for a client-driven observation programme.
 *
 * RUNNING and OUTCOME_UNKNOWN are deliberately not resumable: a process or
 * link loss may have left the current shutter in an unknown physical state.
 * PAUSED is resumable only after the operator reconnects to the same mount and
 * explicitly chooses Resume.
 */
@Serializable
data class ObservationProgrammeJournal(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val host: String,
    val port: Int,
    val demoMode: Boolean,
    val plan: ObservationProgrammePlanner.Plan,
    val phase: Phase,
    val completedSteps: Int,
    val reason: String? = null,
    val updatedAtEpochMs: Long,
) {
    @Serializable
    enum class Phase {
        RUNNING,
        PAUSED,
        OUTCOME_UNKNOWN,
    }

    init {
        require(host.isNotBlank()) { "journal host must not be blank" }
        require(port in 1..65535) { "journal port must be in 1..65535" }
        require(schemaVersion == CURRENT_SCHEMA_VERSION) { "unsupported journal schema" }
        require(completedSteps in 0..plan.captureCount) {
            "journal completedSteps must be within the plan"
        }
        require(updatedAtEpochMs >= 0L) { "journal timestamp must be non-negative" }
    }

    fun belongsTo(currentHost: String, currentPort: Int, currentDemoMode: Boolean): Boolean =
        host == currentHost && port == currentPort && demoMode == currentDemoMode

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

/**
 * Small bounded file store for the observation recovery checkpoint.
 *
 * The store is intentionally separate from SessionStore: a saved mount
 * identity and an in-progress camera programme have different recovery and
 * deletion semantics.
 */
class ObservationProgrammeJournalStore(
    private val filePath: String,
    private val json: Json = DEFAULT_JSON,
    private val sizeLimitBytes: Int = DEFAULT_SIZE_LIMIT_BYTES,
) {
    private val file = PlatformFile(filePath)

    fun read(): ObservationProgrammeJournal? {
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
            json.decodeFromString<ObservationProgrammeJournal>(text)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        return parsed.takeIf {
            it.schemaVersion == ObservationProgrammeJournal.CURRENT_SCHEMA_VERSION
        }
    }

    fun write(journal: ObservationProgrammeJournal): Result<Unit> {
        val normalized = journal.copy(
            schemaVersion = ObservationProgrammeJournal.CURRENT_SCHEMA_VERSION,
        )
        val encoded = try {
            json.encodeToString(normalized)
        } catch (_: SerializationException) {
            return Result.failure(ObservationProgrammeJournalStoreException("journal failed to serialize"))
        }
        val bytes = Utf8.encode(encoded)
        if (bytes.size > sizeLimitBytes) {
            return Result.failure(ObservationProgrammeJournalStoreException("journal exceeds size limit"))
        }
        val parentPath = file.parentPath()
        if (parentPath != null) {
            val parent = PlatformFile(parentPath)
            if (!parent.exists()) {
                try {
                    parent.createDirectories()
                } catch (error: Exception) {
                    return Result.failure(
                        ObservationProgrammeJournalStoreException("could not create journal directory", error),
                    )
                }
            }
            if (parent.exists() && !parent.isWritable()) {
                return Result.failure(
                    ObservationProgrammeJournalStoreException("journal directory is not writable"),
                )
            }
        }
        return try {
            file.writeBytes(bytes)
            Result.success(Unit)
        } catch (error: Exception) {
            return Result.failure(ObservationProgrammeJournalStoreException("journal write failed", error))
        }
    }

    fun forget(): Boolean = file.deleteIfExists()

    companion object {
        const val DEFAULT_SIZE_LIMIT_BYTES: Int = 512 * 1024
        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = false
        }
    }
}

class ObservationProgrammeJournalStoreException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
