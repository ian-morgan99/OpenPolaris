package dev.openpolaris.core.domain

/**
 * Adapts a programme step to the shared capture-evidence contract.
 *
 * [trigger] may issue exactly one shutter/capture request for the step. It
 * must not claim physical completion; [awaitConfirmation] returns only after
 * the event owner has correlated the post-request non-idle 264 lifecycle with
 * a non-empty 773 publication and the terminal idle edge. This adapter is
 * the intended function passed
 * to [ObservationProgrammeExecutor].
 */
class ObservationProgrammeCaptureBridge(
    private val evidence: CaptureEvidenceGate,
    private val trigger: suspend (ObservationProgrammePlanner.Step) -> TriggerResult,
    private val awaitConfirmation: suspend () -> CaptureEvidenceGate.Publication?,
) {
    sealed interface TriggerResult {
        data object Accepted : TriggerResult
        data class Rejected(val reason: String) : TriggerResult
        data class OutcomeUnknown(val reason: String) : TriggerResult
    }

    suspend fun execute(step: ObservationProgrammePlanner.Step): ObservationProgrammeExecutor.StepResult {
        evidence.begin()
        return when (val result = trigger(step)) {
            TriggerResult.Accepted -> confirm()
            is TriggerResult.Rejected -> {
                evidence.fail(result.reason)
                ObservationProgrammeExecutor.StepResult.Rejected(result.reason)
            }
            is TriggerResult.OutcomeUnknown -> {
                evidence.unknown(result.reason)
                ObservationProgrammeExecutor.StepResult.OutcomeUnknown(result.reason)
            }
        }
    }

    private suspend fun confirm(): ObservationProgrammeExecutor.StepResult {
        val publication = try {
            awaitConfirmation()
        } catch (error: Throwable) {
            evidence.unknown(error.message ?: "capture confirmation failed")
            return ObservationProgrammeExecutor.StepResult.OutcomeUnknown(
                error.message ?: "capture confirmation failed",
            )
        }
        return if (publication != null && evidence.isConfirmed && evidence.publication == publication) {
            ObservationProgrammeExecutor.StepResult.Confirmed
        } else {
            val reason = "capture confirmation did not include correlated lifecycle, file and terminal-idle evidence"
            evidence.unknown(reason)
            ObservationProgrammeExecutor.StepResult.OutcomeUnknown(reason)
        }
    }
}
