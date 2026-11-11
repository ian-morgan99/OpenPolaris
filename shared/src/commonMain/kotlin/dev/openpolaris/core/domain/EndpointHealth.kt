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

/** User-selected network intent; selecting it never changes host networking by itself. */
enum class ConnectionMode {
    DIRECT_AP,
    HOME_LAN,
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

/**
 * A bounded, user-facing classification of the last endpoint failure.
 * The detail string remains available for evidence; this kind prevents a
 * refused TCP listener, a route timeout and a protocol handshake failure from
 * collapsing into one generic Home-LAN error.
 */
enum class EndpointFailureKind(
    val label: String,
    val guidance: String,
) {
    TCP_REFUSED(
        label = "TCP refused",
        guidance = "The host answered but the control port rejected the connection; verify the port and that polestar_app is listening on this interface.",
    ),
    TCP_TIMEOUT(
        label = "TCP timeout",
        guidance = "The control endpoint did not answer in time; check Wi-Fi association, subnet isolation and routing.",
    ),
    NETWORK_UNREACHABLE(
        label = "Network unreachable",
        guidance = "The client cannot route to this host; check the station IP, client network and mesh/VLAN isolation.",
    ),
    PROTOCOL_HANDSHAKE(
        label = "Protocol or handshake failure",
        guidance = "TCP reached the endpoint but the Polaris protocol did not complete; verify firmware/app compatibility and authentication.",
    ),
    AUTHENTICATION(
        label = "Authentication rejected",
        guidance = "The mount requires a password or rejected it; update the in-memory password and retry.",
    ),
    UNKNOWN(
        label = "Unknown connection failure",
        guidance = "Keep the exact failure detail and verify the destination host, port and network path.",
    ),
}

/**
 * Best-effort classification of transport evidence. It never upgrades a
 * connection to healthy; callers still require a successful control handshake.
 */
fun classifyEndpointFailure(detail: String): EndpointFailureKind {
    val text = detail.lowercase()
    return when {
        "wrong password" in text ||
            "requires connection password" in text ||
            "password" in text && ("auth" in text || "token" in text || "821" in text || "rejected" in text) ->
            EndpointFailureKind.AUTHENTICATION
        "connection refused" in text || "econnrefused" in text ->
            EndpointFailureKind.TCP_REFUSED
        "handshake" in text || "auth probe" in text || "auth token" in text ||
            "app hello" in text || "protocol" in text || "ret!=" in text ->
            EndpointFailureKind.PROTOCOL_HANDSHAKE
        "timed out" in text || "timeout" in text ->
            EndpointFailureKind.TCP_TIMEOUT
        "network is unreachable" in text || "no route" in text ||
            "unknown host" in text || "unresolved" in text ->
            EndpointFailureKind.NETWORK_UNREACHABLE
        else -> EndpointFailureKind.UNKNOWN
    }
}

data class EndpointHealth(
    val profile: EndpointProfile?,
    val phase: EndpointPhase,
    val control: EndpointService = EndpointService.UNKNOWN,
    val preview: EndpointService = EndpointService.UNKNOWN,
    val phaseSinceMs: Long? = null,
    val lastFailure: String? = null,
    val lastFailureKind: EndpointFailureKind? = null,
) {
    init {
        require(phaseSinceMs == null || phaseSinceMs >= 0L) { "phase age must be non-negative" }
        require(lastFailure == null || lastFailure.isNotBlank()) { "failure detail must not be blank" }
        require(lastFailureKind == null || lastFailure != null) {
            "failure kind requires failure detail"
        }
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
