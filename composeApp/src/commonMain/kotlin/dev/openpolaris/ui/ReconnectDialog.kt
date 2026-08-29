package dev.openpolaris.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp

/**
 * "Reconnect to last mount?" prompt — Material3 [AlertDialog] wired to
 * [AppViewModel.reconnectPrompt]. 3c.4 of issue #7.
 *
 * Surfaces when the user reopens the app and a usable
 * [dev.openpolaris.core.session.SessionMarker] was found on disk (i.e.
 * the previous session's `connect()` succeeded and persisted state, and
 * the user has not asked to "Forget this mount" since). Three actions:
 *
 *  - **Reconnect** — `acceptReconnect()`: sets the host back to the
 *    persisted one, kicks off `connect()`. Clears the prompt first so
 *    the dialog closes before the connection attempt starts.
 *  - **Different mount** — `dismissReconnect()`: clears the prompt for
 *    this launch only. The marker stays on disk; the prompt returns on
 *    the next launch until the user accepts or chooses Forget.
 *  - **Forget this mount** — `forgetMarker()`: deletes the marker file
 *    permanently. The next launch will not prompt.
 *
 * Hosted at the root [OpenPolarisApp] surface so it floats above the
 * call-out stack and survives navigating between Connection/Slew/Helpers
 * dialogs. Renders nothing when the flow is `null` (the common case).
 *
 * Implementation note: Material3's [AlertDialog] only exposes
 * `confirmButton` + `dismissButton`. The third action ("Forget") shares
 * the dismiss slot via a small [Row] of two TextButtons — that is the
 * canonical way to host three actions without dropping to a custom
 * Dialog composable.
 */
@Composable
fun ReconnectDialog(vm: AppViewModel) {
    val prompt by vm.reconnectPrompt.collectAsState()
    if (prompt == null) return
    val p = prompt!!
    AlertDialog(
        onDismissRequest = { vm.dismissReconnect() },
        title = { Text("Reconnect to ${p.host}?") },
        text = {
            Text(buildString {
                append("Saved mount was last seen ")
                append(formatAge(p.ageMs))
                append(" ago")
                if (p.trackingStarted) append(", tracking was on")
                append(". Reconnect, pick a different mount, or forget this one.")
            })
        },
        confirmButton = {
            TextButton(onClick = { vm.acceptReconnect() }) { Text("Reconnect") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { vm.dismissReconnect() }) { Text("Different mount") }
                TextButton(onClick = { vm.forgetMarker() }) { Text("Forget") }
            }
        },
    )
}

private fun formatAge(ageMs: Long): String {
    val s = (ageMs / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60}m"
        s < 86_400 -> "${s / 3600}h"
        else -> "${s / 86_400}d"
    }
}
