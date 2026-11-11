package dev.openpolaris.core.domain

/**
 * Maps resident Astro WebUI readback into the ordered mission flow without
 * treating an absent or failed read as successful readiness.
 *
 * These helpers are deliberately read-only. They do not enable a write route,
 * start a guider/autofocus job, or claim that the mount physically moved.
 */
object AstroMissionEvidence {
    fun focusReadiness(state: AstroWebUiAutofocusUiState): Boolean? = when (state) {
        AstroWebUiAutofocusUiState.Idle,
        AstroWebUiAutofocusUiState.Loading,
        is AstroWebUiAutofocusUiState.Accepted,
        is AstroWebUiAutofocusUiState.Failed,
        -> null
        is AstroWebUiAutofocusUiState.Ready -> {
            val progress = state.state.progress
            when {
                state.state.status == "done" && progress.state == "done" && progress.best != null -> true
                state.state.status == "failed" || progress.state == "failed" -> false
                else -> null
            }
        }
    }

    fun guideReadiness(state: AstroWebUiGuideUiState): Boolean? = when (state) {
        is AstroWebUiGuideUiState.Ready -> state.state.running
        AstroWebUiGuideUiState.Idle,
        AstroWebUiGuideUiState.Loading,
        is AstroWebUiGuideUiState.Accepted,
        is AstroWebUiGuideUiState.Failed,
        -> null
    }
}
