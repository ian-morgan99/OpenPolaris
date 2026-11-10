package dev.openpolaris.core.domain

/**
 * Correlates one requested shutter with positive camera lifecycle and file
 * publication evidence.
 *
 * A file event without a post-request non-idle lifecycle is ignored, and an
 * idle lifecycle or command acknowledgement alone is not completion proof.
 * The gate is intentionally transport-neutral so intervalometers and future
 * observation programmes share one capture truth contract.
 */
class CaptureEvidenceGate {
    enum class State {
        IDLE,
        REQUESTED,
        ACTIVE,
        PUBLISHED,
        CONFIRMED,
        FAILED,
        UNKNOWN,
    }

    data class Publication(
        val path: String,
        val type: Int?,
        val sizeBytes: Long?,
    )

    var state: State = State.IDLE
        private set

    var publication: Publication? = null
        private set

    private var lifecycleSeen = false
    private var fileSeen = false

    fun begin() {
        state = State.REQUESTED
        publication = null
        lifecycleSeen = false
        fileSeen = false
    }

    /** Returns true only for the first positive non-idle lifecycle observation. */
    fun observeLifecycle(cameraState: Int): Boolean {
        if (cameraState == 0 || state !in setOf(State.REQUESTED, State.ACTIVE, State.PUBLISHED)) return false
        val first = !lifecycleSeen
        lifecycleSeen = true
        state = if (fileSeen) State.CONFIRMED else State.ACTIVE
        return first
    }

    /** Returns true only when a non-empty path is accepted and correlated. */
    fun observePublication(path: String, type: Int?, sizeBytes: Long?): Boolean {
        if (path.isBlank() || !lifecycleSeen || state !in setOf(State.ACTIVE, State.PUBLISHED)) return false
        if (fileSeen) return false
        fileSeen = true
        publication = Publication(path.trim(), type, sizeBytes)
        state = if (lifecycleSeen) State.CONFIRMED else State.PUBLISHED
        return true
    }

    fun fail(reason: String) {
        require(reason.isNotBlank()) { "failure reason must not be blank" }
        state = State.FAILED
    }

    fun unknown(reason: String) {
        require(reason.isNotBlank()) { "unknown reason must not be blank" }
        state = State.UNKNOWN
    }

    /** Clear all evidence when the owning session is disconnected or abandoned. */
    fun clear() {
        state = State.IDLE
        publication = null
        lifecycleSeen = false
        fileSeen = false
    }

    /** Prepare the same gate for the next step without accepting stale events. */
    fun resetForNext() {
        begin()
    }

    val isConfirmed: Boolean get() = state == State.CONFIRMED && publication != null
}
