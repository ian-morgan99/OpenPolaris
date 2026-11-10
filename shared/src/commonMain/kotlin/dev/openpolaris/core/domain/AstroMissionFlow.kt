package dev.openpolaris.core.domain

enum class AstroMissionStage(
    val label: String,
    val actionId: String,
) {
    CONNECT("Connect", "connection"),
    PREPARE("Prepare", "prepare"),
    ALIGN("Align", "alignment"),
    TRACK("Track", "tracking"),
    TARGET("Target", "target"),
    FOCUS("Focus", "focus"),
    GUIDE("Guide", "guide"),
    CAPTURE("Capture", "capture"),
    REVIEW("Review", "storage"),
}

enum class AstroMissionStageState {
    COMPLETE,
    CURRENT,
    BLOCKED,
    PENDING,
    OPTIONAL,
    UNKNOWN,
}

data class AstroMissionStageStatus(
    val stage: AstroMissionStage,
    val state: AstroMissionStageState,
    val summary: String,
    val actionLabel: String? = null,
)

data class AstroMissionInput(
    val controlReady: Boolean,
    val mountConnected: Boolean,
    val cameraAttached: Boolean?,
    val storageReady: Boolean?,
    val alignmentReady: Boolean?,
    val tracking: Boolean?,
    val targetSelected: Boolean,
    val focusReady: Boolean?,
    val guideActive: Boolean?,
    val captureReady: Boolean,
    val captureComplete: Boolean,
    val reviewReady: Boolean,
)

/**
 * Ordered Astro workflow model shared by the mobile and desktop shells.
 *
 * It is intentionally a presentation/state contract: actions are returned as
 * stable IDs and are routed by the shell to existing owners. The evaluator
 * never connects, slews, focuses, captures, or starts guiding itself.
 */
object AstroMissionFlow {
    fun evaluate(input: AstroMissionInput): List<AstroMissionStageStatus> {
        val connected = input.controlReady && input.mountConnected
        val prepared = connected && input.cameraAttached == true && input.storageReady == true
        val aligned = prepared && input.alignmentReady == true
        val tracking = aligned && input.tracking == true
        val target = tracking && input.targetSelected
        val focused = target && input.focusReady == true
        val capture = target && input.cameraAttached == true &&
            input.storageReady == true && input.captureComplete
        val review = capture && input.reviewReady

        return listOf(
            AstroMissionStageStatus(
                AstroMissionStage.CONNECT,
                if (connected) AstroMissionStageState.COMPLETE else AstroMissionStageState.CURRENT,
                if (connected) "Control link and mount are ready" else "Connect to the mount before continuing",
                if (connected) null else "Open connection",
            ),
            prepare(input, connected, prepared),
            gated(
                AstroMissionStage.ALIGN,
                prepared,
                input.alignmentReady,
                "Alignment evidence available",
                "Run alignment or plate solve",
                "Open alignment",
            ),
            gated(
                AstroMissionStage.TRACK,
                aligned,
                input.tracking,
                "Tracking is confirmed",
                "Start the selected tracking rate",
                "Open tracking",
            ),
            AstroMissionStageStatus(
                AstroMissionStage.TARGET,
                when {
                    !tracking -> AstroMissionStageState.PENDING
                    input.targetSelected -> AstroMissionStageState.COMPLETE
                    else -> AstroMissionStageState.CURRENT
                },
                when {
                    !tracking -> "Complete tracking first"
                    input.targetSelected -> "Target selected or solved"
                    else -> "Choose a target and slew to it"
                },
                if (!tracking || input.targetSelected) null else "Open slew",
            ),
            focus(input, target, focused),
            guide(input, target, focused),
            AstroMissionStageStatus(
                AstroMissionStage.CAPTURE,
                when {
                    !target -> AstroMissionStageState.PENDING
                    input.cameraAttached == false || input.storageReady == false -> AstroMissionStageState.BLOCKED
                    input.cameraAttached == null || input.storageReady == null -> AstroMissionStageState.UNKNOWN
                    input.captureComplete -> AstroMissionStageState.COMPLETE
                    input.captureReady -> AstroMissionStageState.CURRENT
                    else -> AstroMissionStageState.BLOCKED
                },
                when {
                    !target -> "Complete target selection first"
                    input.cameraAttached == false -> "Attach a camera before capture"
                    input.storageReady == false -> "Make storage available before capture"
                    input.cameraAttached == null || input.storageReady == null -> "Camera or storage status is not read"
                    input.captureComplete -> "Capture evidence is confirmed"
                    input.captureReady -> "Capture path is ready; take the next frame"
                    else -> "Capture readiness is not established"
                },
                if (target && !input.captureComplete) "Open camera" else null,
            ),
            AstroMissionStageStatus(
                AstroMissionStage.REVIEW,
                when {
                    !capture -> AstroMissionStageState.PENDING
                    review -> AstroMissionStageState.COMPLETE
                    else -> AstroMissionStageState.CURRENT
                },
                when {
                    !capture -> "A confirmed capture will appear here"
                    review -> "Latest capture is published"
                    else -> "Review the gallery after a confirmed capture"
                },
                if (capture && !review) "Open gallery" else null,
            ),
        )
    }

    private fun prepare(
        input: AstroMissionInput,
        connected: Boolean,
        prepared: Boolean,
    ): AstroMissionStageStatus {
        val state = when {
            !connected -> AstroMissionStageState.PENDING
            prepared -> AstroMissionStageState.COMPLETE
            input.cameraAttached == false || input.storageReady == false -> AstroMissionStageState.BLOCKED
            input.cameraAttached == null || input.storageReady == null -> AstroMissionStageState.UNKNOWN
            else -> AstroMissionStageState.CURRENT
        }
        val summary = when {
            !connected -> "Connect before preparing the kit"
            prepared -> "Camera and storage are ready"
            input.cameraAttached == false -> "Attach a camera before preparing"
            input.storageReady == false -> "Make storage available before preparing"
            input.cameraAttached == null || input.storageReady == null -> "Camera or storage status is not read"
            else -> "Check camera and storage status"
        }
        return AstroMissionStageStatus(
            AstroMissionStage.PREPARE,
            state,
            summary,
            if (state == AstroMissionStageState.COMPLETE || state == AstroMissionStageState.PENDING) null else "Open camera",
        )
    }

    private fun focus(
        input: AstroMissionInput,
        target: Boolean,
        focused: Boolean,
    ): AstroMissionStageStatus {
        val state = when {
            !target -> AstroMissionStageState.PENDING
            input.cameraAttached == false -> AstroMissionStageState.BLOCKED
            focused -> AstroMissionStageState.COMPLETE
            input.focusReady == null -> AstroMissionStageState.UNKNOWN
            else -> AstroMissionStageState.CURRENT
        }
        return AstroMissionStageStatus(
            AstroMissionStage.FOCUS,
            state,
            when {
                !target -> "Complete target selection first"
                input.cameraAttached == false -> "Attach a camera before focusing"
                focused -> "Focus evidence is available"
                input.focusReady == null -> "Focus evidence is not read"
                else -> "Use manual focus or a qualified autofocus workflow"
            },
            if (target && !focused) "Open camera focus" else null,
        )
    }

    private fun guide(
        input: AstroMissionInput,
        target: Boolean,
        focused: Boolean,
    ): AstroMissionStageStatus {
        val state = when {
            !target || !focused -> AstroMissionStageState.PENDING
            input.guideActive == true -> AstroMissionStageState.COMPLETE
            input.guideActive == null -> AstroMissionStageState.UNKNOWN
            else -> AstroMissionStageState.OPTIONAL
        }
        return AstroMissionStageStatus(
            AstroMissionStage.GUIDE,
            state,
            when {
                !target || !focused -> "Guiding follows target and focus"
                input.guideActive == true -> "Guiding is active"
                input.guideActive == null -> "Guiding status is not read"
                else -> "Optional: guiding is not active"
            },
            if (target && focused && input.guideActive != true) "Open Astro helpers" else null,
        )
    }

    private fun gated(
        stage: AstroMissionStage,
        prerequisite: Boolean,
        value: Boolean?,
        readySummary: String,
        currentSummary: String,
        actionLabel: String,
    ): AstroMissionStageStatus {
        val state = when {
            !prerequisite -> AstroMissionStageState.PENDING
            value == true -> AstroMissionStageState.COMPLETE
            value == null -> AstroMissionStageState.UNKNOWN
            else -> AstroMissionStageState.CURRENT
        }
        return AstroMissionStageStatus(
            stage,
            state,
            if (!prerequisite) "Complete the previous mission step first"
            else if (value == null) "Status is not read" else currentSummary,
            if (prerequisite && value != true) actionLabel else null,
        )
    }
}
