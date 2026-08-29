package dev.openpolaris.ui

import dev.openpolaris.core.domain.MountMode

/**
 * Transient prompt surfaced by [AppViewModel.reconnectPrompt] when a
 * previously-persisted [dev.openpolaris.core.session.SessionMarker] is
 * discovered at startup. The UI is expected to render this as a
 * Material3 `AlertDialog` with two actions:
 *
 *  - "Reconnect" → [AppViewModel.acceptReconnect] (sets `host` and calls
 *    the existing [AppViewModel.connect] flow, which writes a fresh
 *    marker on success).
 *  - "Different mount" → [AppViewModel.dismissReconnect] (clears the
 *    prompt for this launch; the underlying marker is preserved so the
 *    prompt returns next launch).
 *
 * The UI also offers a third action via the mount settings sheet:
 *  - "Forget this mount" → [AppViewModel.forgetMarker] (deletes the
 *    marker file so the prompt never appears again).
 *
 * Kept as a small value type, not a Compose state object, so the
 * underlying [kotlinx.coroutines.flow.MutableStateFlow] only fires
 * updates when this value changes.
 *
 * @param host The host string last seen in the marker (typically a
 *   phone-tethered IP like `192.168.43.1`).
 * @param port The TCP port the mount was last addressed on. Hard-coded
 *   to `9090` today but carried in the marker so a future port change
 *   is forward-compatible.
 * @param mountMode The mount's mode at the moment the marker was
 *   written. Best-effort: may be [MountMode.UNKNOWN] if the mount was
 *   in a transitional state.
 * @param trackingStarted `true` if the user had tracking on when the
 *   session was last connected. The reconnect-confirmation dialog
 *   surfaces this so the user knows whether tracking will resume
 *   automatically.
 * @param ageMs How long ago the marker was written, computed at
 *   read-time from the marker's `lastConnectedAtEpochMs` and
 *   `Clock.System.now()`. The UI can show "Last connected 2 hours
 *   ago" without a separate timer.
 * @param lastRollDeg Roll (tilt about the lens axis) at the moment the
 *   marker was written. `null` if the mount never reported a 517 frame
 *   before disconnect.
 * @param lastPitchDeg Pitch (tilt up/down) at the moment the marker
 *   was written. `null` if the mount never reported a 517 frame.
 */
data class ReconnectPrompt(
    val host: String,
    val port: Int,
    val mountMode: MountMode,
    val trackingStarted: Boolean,
    val ageMs: Long,
    val lastRollDeg: Double?,
    val lastPitchDeg: Double?,
)
