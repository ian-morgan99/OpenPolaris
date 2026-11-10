package dev.openpolaris.core.domain

/** User-facing severity for a mission readiness card. */
enum class ReadinessLevel {
    READY,
    ATTENTION,
    BLOCKED,
    UNKNOWN,
}

data class ReadinessCard(
    val id: String,
    val label: String,
    val level: ReadinessLevel,
    val summary: String,
    val actionLabel: String? = null,
)

data class MissionReadinessInput(
    val control: EndpointService,
    val mountConnected: Boolean,
    val cameraAttached: Boolean?,
    val preview: EndpointService,
    val tracking: Boolean?,
    val captureInProgress: Boolean,
    val activeMotionOwner: MotionOwner? = null,
)

/**
 * Stable, platform-neutral readiness ordering for the Mission dashboard.
 *
 * This function intentionally reports unknown separately from blocked. A user
 * must be told whether a capability is unavailable or simply not checked yet.
 * It also keeps preview independent from the control socket and keeps motion
 * ownership visible even when the current owner is not a live controller yet.
 */
object MissionReadiness {
    fun evaluate(input: MissionReadinessInput): List<ReadinessCard> = listOf(
        connection(input),
        mount(input),
        camera(input),
        preview(input),
        tracking(input),
        capture(input),
        motion(input),
    )

    private fun connection(input: MissionReadinessInput): ReadinessCard = when (input.control) {
        EndpointService.REACHABLE -> ReadinessCard(
            id = "connection",
            label = "Connection",
            level = ReadinessLevel.READY,
            summary = "Control link ready",
        )
        EndpointService.UNREACHABLE -> ReadinessCard(
            id = "connection",
            label = "Connection",
            level = ReadinessLevel.BLOCKED,
            summary = "Control link unavailable",
            actionLabel = "Connect to mount",
        )
        EndpointService.UNKNOWN -> ReadinessCard(
            id = "connection",
            label = "Connection",
            level = ReadinessLevel.UNKNOWN,
            summary = "Control link not checked",
            actionLabel = "Check connection",
        )
    }

    private fun mount(input: MissionReadinessInput): ReadinessCard = if (input.mountConnected) {
        ReadinessCard(
            id = "mount",
            label = "Mount",
            level = ReadinessLevel.READY,
            summary = "Mount status available",
        )
    } else {
        ReadinessCard(
            id = "mount",
            label = "Mount",
            level = ReadinessLevel.BLOCKED,
            summary = "Mount is not connected",
            actionLabel = "Open connection",
        )
    }

    private fun camera(input: MissionReadinessInput): ReadinessCard = when (input.cameraAttached) {
        true -> ReadinessCard(
            id = "camera",
            label = "Camera",
            level = ReadinessLevel.READY,
            summary = "Camera attached",
        )
        false -> ReadinessCard(
            id = "camera",
            label = "Camera",
            level = ReadinessLevel.BLOCKED,
            summary = "No camera detected",
            actionLabel = "Attach camera",
        )
        null -> ReadinessCard(
            id = "camera",
            label = "Camera",
            level = ReadinessLevel.UNKNOWN,
            summary = "Camera status not read",
            actionLabel = "Refresh camera status",
        )
    }

    private fun preview(input: MissionReadinessInput): ReadinessCard = when {
        input.preview == EndpointService.REACHABLE -> ReadinessCard(
            id = "preview",
            label = "Preview",
            level = ReadinessLevel.READY,
            summary = "Live preview reachable",
        )
        input.cameraAttached == false -> ReadinessCard(
            id = "preview",
            label = "Preview",
            level = ReadinessLevel.ATTENTION,
            summary = "Preview waits for a camera",
        )
        input.preview == EndpointService.UNKNOWN -> ReadinessCard(
            id = "preview",
            label = "Preview",
            level = ReadinessLevel.UNKNOWN,
            summary = "Preview not checked",
            actionLabel = "Open preview",
        )
        else -> ReadinessCard(
            id = "preview",
            label = "Preview",
            level = ReadinessLevel.ATTENTION,
            summary = "Preview unavailable",
            actionLabel = "Open preview",
        )
    }

    private fun tracking(input: MissionReadinessInput): ReadinessCard = when (input.tracking) {
        true -> ReadinessCard(
            id = "tracking",
            label = "Tracking",
            level = ReadinessLevel.READY,
            summary = "Tracking active",
        )
        false -> ReadinessCard(
            id = "tracking",
            label = "Tracking",
            level = ReadinessLevel.ATTENTION,
            summary = "Tracking is off",
            actionLabel = "Start tracking",
        )
        null -> ReadinessCard(
            id = "tracking",
            label = "Tracking",
            level = ReadinessLevel.UNKNOWN,
            summary = "Tracking state not read",
        )
    }

    private fun capture(input: MissionReadinessInput): ReadinessCard = when {
        input.captureInProgress -> ReadinessCard(
            id = "capture",
            label = "Capture",
            level = ReadinessLevel.ATTENTION,
            summary = "Capture in progress",
        )
        input.cameraAttached == true && input.mountConnected -> ReadinessCard(
            id = "capture",
            label = "Capture",
            level = ReadinessLevel.READY,
            summary = "Capture path ready",
        )
        input.cameraAttached == false -> ReadinessCard(
            id = "capture",
            label = "Capture",
            level = ReadinessLevel.BLOCKED,
            summary = "Attach a camera before capture",
            actionLabel = "Open camera",
        )
        else -> ReadinessCard(
            id = "capture",
            label = "Capture",
            level = ReadinessLevel.UNKNOWN,
            summary = "Capture readiness not established",
        )
    }

    private fun motion(input: MissionReadinessInput): ReadinessCard = when {
        !input.mountConnected -> ReadinessCard(
            id = "motion",
            label = "Motion",
            level = ReadinessLevel.BLOCKED,
            summary = "Connect the mount before motion",
            actionLabel = "Open connection",
        )
        input.activeMotionOwner != null -> ReadinessCard(
            id = "motion",
            label = "Motion",
            level = ReadinessLevel.ATTENTION,
            summary = "Owned by ${input.activeMotionOwner}",
        )
        else -> ReadinessCard(
            id = "motion",
            label = "Motion",
            level = ReadinessLevel.READY,
            summary = "No active motion owner",
        )
    }
}
