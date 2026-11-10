package dev.openpolaris.core.domain

/** Individual evidence-backed checks shown before an Astro session starts. */
enum class AstroPrepareCheckId(val label: String) {
    CONNECTION("Control link"),
    CAMERA("Camera"),
    STORAGE("Storage"),
    LEVEL("Level"),
    TILT_COMPENSATION("Tilt compensation"),
    LIMITS("Mount limits"),
}

enum class AstroPrepareCheckState {
    READY,
    BLOCKED,
    UNKNOWN,
    PENDING,
    OPTIONAL,
}

data class AstroPrepareCheck(
    val id: AstroPrepareCheckId,
    val state: AstroPrepareCheckState,
    val summary: String,
)

/**
 * Read-only preparation checklist for the shared mobile/desktop mission UX.
 *
 * This model only classifies evidence already read by the app. It does not
 * enable auto-level, limits, tracking, capture or any other physical action.
 * `null` is intentionally kept as UNKNOWN rather than being treated as ready.
 */
data class AstroPrepareInput(
    val controlReady: Boolean,
    val mountConnected: Boolean,
    val cameraAttached: Boolean?,
    val storageReady: Boolean?,
    val levelWithinTolerance: Boolean?,
    val tiltCompensationEnabled: Boolean?,
    val limitsEnabled: Boolean?,
)

object AstroPrepareChecklist {
    fun evaluate(input: AstroPrepareInput): List<AstroPrepareCheck> {
        val connected = input.controlReady && input.mountConnected
        return listOf(
            AstroPrepareCheck(
                AstroPrepareCheckId.CONNECTION,
                if (connected) AstroPrepareCheckState.READY else AstroPrepareCheckState.BLOCKED,
                if (connected) "Control link and mount are connected" else "Connect to the mount before preparing",
            ),
            booleanCheck(
                AstroPrepareCheckId.CAMERA,
                connected,
                input.cameraAttached,
                ready = "Camera attachment is confirmed",
                blocked = "Attach a camera before continuing",
                unknown = "Camera attachment has not been read",
            ),
            booleanCheck(
                AstroPrepareCheckId.STORAGE,
                connected,
                input.storageReady,
                ready = "Storage is available",
                blocked = "Make mount storage available before capture",
                unknown = "Storage status has not been read",
            ),
            booleanCheck(
                AstroPrepareCheckId.LEVEL,
                connected,
                input.levelWithinTolerance,
                ready = "Tilt is within the level envelope",
                blocked = "Level the mount before an Astro session",
                unknown = "Level evidence has not been read",
            ),
            booleanCheck(
                AstroPrepareCheckId.TILT_COMPENSATION,
                connected,
                input.tiltCompensationEnabled,
                ready = "Tilt compensation is enabled",
                blocked = "Enable tilt compensation or verify the manual setup",
                unknown = "Tilt-compensation state has not been read",
            ),
            limitsCheck(connected, input.limitsEnabled),
        )
    }

    private fun booleanCheck(
        id: AstroPrepareCheckId,
        connected: Boolean,
        value: Boolean?,
        ready: String,
        blocked: String,
        unknown: String,
    ): AstroPrepareCheck = when {
        !connected -> AstroPrepareCheck(id, AstroPrepareCheckState.PENDING, "Connect before checking ${id.label.lowercase()}")
        value == true -> AstroPrepareCheck(id, AstroPrepareCheckState.READY, ready)
        value == false -> AstroPrepareCheck(id, AstroPrepareCheckState.BLOCKED, blocked)
        else -> AstroPrepareCheck(id, AstroPrepareCheckState.UNKNOWN, unknown)
    }

    private fun limitsCheck(connected: Boolean, value: Boolean?): AstroPrepareCheck = when {
        !connected -> AstroPrepareCheck(AstroPrepareCheckId.LIMITS, AstroPrepareCheckState.PENDING, "Connect before checking mount limits")
        value == true -> AstroPrepareCheck(AstroPrepareCheckId.LIMITS, AstroPrepareCheckState.READY, "Mount limits are enabled")
        value == false -> AstroPrepareCheck(AstroPrepareCheckId.LIMITS, AstroPrepareCheckState.OPTIONAL, "Limits are off; verify clearance manually before moving")
        else -> AstroPrepareCheck(AstroPrepareCheckId.LIMITS, AstroPrepareCheckState.UNKNOWN, "Mount-limit state has not been read")
    }
}
