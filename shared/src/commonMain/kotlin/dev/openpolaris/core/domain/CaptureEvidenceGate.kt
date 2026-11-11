package dev.openpolaris.core.domain

/**
 * Correlates one requested shutter with positive camera lifecycle and file
 * publication evidence.
 *
 * A file event without a post-request non-idle lifecycle is ignored, and an
 * idle lifecycle or command acknowledgement alone is not completion proof.
 * Completion also waits for the post-publication terminal idle edge. Benro
 * Connect emits the capture lifecycle as a sequence of active/processing
 * states followed by state 0; accepting the file before that edge would let
 * the UI report a completed image while the camera is still busy and would
 * let a sequence fire its next shutter too early.
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
    private var terminalIdleSeen = false

    fun begin() {
        state = State.REQUESTED
        publication = null
        lifecycleSeen = false
        fileSeen = false
        terminalIdleSeen = false
    }

    /**
     * Returns true only for the first positive non-idle lifecycle observation.
     * The terminal idle edge is recorded separately and never becomes
     * positive activity evidence by itself.
     */
    fun observeLifecycle(cameraState: Int): Boolean {
        if (state !in setOf(State.REQUESTED, State.ACTIVE, State.PUBLISHED)) return false
        if (cameraState == 0) {
            if (lifecycleSeen) {
                terminalIdleSeen = true
                if (fileSeen) state = State.CONFIRMED
            }
            return false
        }
        val first = !lifecycleSeen
        lifecycleSeen = true
        state = if (fileSeen && terminalIdleSeen) State.CONFIRMED else State.ACTIVE
        return first
    }

    /** Returns true only when a non-empty path is accepted and correlated. */
    fun observePublication(path: String, type: Int?, sizeBytes: Long?): Boolean {
        if (path.isBlank() || !lifecycleSeen || state !in setOf(State.ACTIVE, State.PUBLISHED)) return false
        if (fileSeen) return false
        fileSeen = true
        publication = Publication(path.trim(), type, sizeBytes)
        state = if (lifecycleSeen && terminalIdleSeen) State.CONFIRMED else State.PUBLISHED
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
        terminalIdleSeen = false
    }

    /** Prepare the same gate for the next step without accepting stale events. */
    fun resetForNext() {
        begin()
    }

    val isConfirmed: Boolean get() = state == State.CONFIRMED && publication != null
}
