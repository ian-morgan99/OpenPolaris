package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AstroMissionEvidenceTest {
    @Test
    fun autofocusRequiresCompletedResidentRunAndBestStep() {
        val complete = AstroWebUiAutofocusUiState.Ready(
            AstroWebUiAutofocusState(
                status = "done",
                progress = AstroWebUiAutofocusProgress(
                    state = "done",
                    step = 12,
                    steps = 12,
                    best = AstroWebUiAutofocusBest(7, 0.9, 1.2),
                    samples = emptyList(),
                    error = null,
                ),
            ),
        )
        assertEquals(true, AstroMissionEvidence.focusReadiness(complete))
        assertEquals(null, AstroMissionEvidence.focusReadiness(complete.copy(
            state = complete.state.copy(status = "running"),
        )))
        assertEquals(false, AstroMissionEvidence.focusReadiness(complete.copy(
            state = complete.state.copy(
                status = "failed",
                progress = complete.state.progress.copy(state = "failed"),
            ),
        )))
    }

    @Test
    fun autofocusWithoutReadbackOrBestStepRemainsUnknown() {
        assertEquals(null, AstroMissionEvidence.focusReadiness(AstroWebUiAutofocusUiState.Idle))
        assertEquals(null, AstroMissionEvidence.focusReadiness(AstroWebUiAutofocusUiState.Ready(
            AstroWebUiAutofocusState(
                status = "done",
                progress = AstroWebUiAutofocusProgress(
                    state = "done",
                    step = 12,
                    steps = 12,
                    best = null,
                    samples = emptyList(),
                    error = null,
                ),
            ),
        )))
    }

    @Test
    fun guideUsesOnlyPositiveResidentReadback() {
        val state = AstroWebUiGuideState(
            running = true,
            thresholdArcsec = 2.0,
            intervalSeconds = 5.0,
            lastDriftArcsec = null,
            points = emptyList(),
            log = "",
        )
        assertEquals(true, AstroMissionEvidence.guideReadiness(AstroWebUiGuideUiState.Ready(state)))
        assertEquals(false, AstroMissionEvidence.guideReadiness(AstroWebUiGuideUiState.Ready(state.copy(running = false))))
        assertEquals(null, AstroMissionEvidence.guideReadiness(AstroWebUiGuideUiState.Idle))
        assertEquals(null, AstroMissionEvidence.guideReadiness(AstroWebUiGuideUiState.Failed("not read")))
    }
}
