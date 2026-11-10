package dev.openpolaris.core.domain

/**
 * The route through which OpenPolaris reaches a mount. A profile is identity
 * and intent only; it does not open a socket or change host networking.
 */
enum class EndpointKind {
    DIRECT_AP,
    HOME_LAN,
    PC_BRIDGE,
    DEMO,
}

data class EndpointAddress(
    val host: String,
    val controlPort: Int = 9090,
    val previewPort: Int = 8080,
) {
    init {
        require(host.isNotBlank()) { "endpoint host must not be blank" }
        require(controlPort in 1..65535) { "control port must be in 1..65535" }
        require(previewPort in 1..65535) { "preview port must be in 1..65535" }
    }
}

data class EndpointProfile(
    val id: String,
    val label: String,
    val kind: EndpointKind,
    val address: EndpointAddress,
) {
    init {
        require(id.isNotBlank()) { "endpoint id must not be blank" }
        require(label.isNotBlank()) { "endpoint label must not be blank" }
    }
}

/** Individual service evidence, kept separate from the route phase. */
enum class EndpointService {
    UNKNOWN,
    UNREACHABLE,
    REACHABLE,
}

/**
 * Connection lifecycle state shown to the user. A reachable control port is
 * not silently upgraded to a healthy preview: the two services are reported
 * independently because Astro WebUI treats control and MJPEG preview as
 * separate endpoints.
 */
enum class EndpointPhase {
    DISCONNECTED,
    DISCOVERING,
    WAKING,
    WIFI_READY,
    CONTROL_CHECKING,
    READY,
    DEGRADED,
    FAILED,
}

data class EndpointHealth(
    val profile: EndpointProfile?,
    val phase: EndpointPhase,
    val control: EndpointService = EndpointService.UNKNOWN,
    val preview: EndpointService = EndpointService.UNKNOWN,
    val phaseSinceMs: Long? = null,
    val lastFailure: String? = null,
) {
    init {
        require(phaseSinceMs == null || phaseSinceMs >= 0L) { "phase age must be non-negative" }
        require(lastFailure == null || lastFailure.isNotBlank()) { "failure detail must not be blank" }
        if (phase == EndpointPhase.DISCONNECTED) {
            require(control != EndpointService.REACHABLE) { "disconnected endpoint cannot have reachable control" }
            require(preview != EndpointService.REACHABLE) { "disconnected endpoint cannot have reachable preview" }
        }
    }

    /** The minimum boundary for mount commands. Preview is intentionally not required. */
    val controlReady: Boolean
        get() = profile != null && control == EndpointService.REACHABLE && phase != EndpointPhase.FAILED

    /** A live image stream is a separate, optional service. */
    val previewReady: Boolean
        get() = profile != null && preview == EndpointService.REACHABLE

    /** A failed/unknown route must never be presented as connected. */
    val isUsable: Boolean
        get() = controlReady

    /**
     * Age of the current phase at an observed clock value. A clock that moved
     * backwards is treated as zero age so the UI cannot display a negative
     * freshness value.
     */
    fun phaseAgeMs(atMs: Long): Long? = phaseSinceMs?.let { (atMs - it).coerceAtLeast(0L) }

    companion object {
        fun disconnected(): EndpointHealth = EndpointHealth(
            profile = null,
            phase = EndpointPhase.DISCONNECTED,
        )
    }
}
