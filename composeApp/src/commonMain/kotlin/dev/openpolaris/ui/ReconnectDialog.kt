package dev.openpolaris.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * "Reconnect to last mount?" prompt — Material3 [AlertDialog] wired to
 * [AppViewModel.reconnectPrompt]. 3c.4 + 3c.5 of issue #7.
 *
 * Surfaces when the user reopens the app and a usable
 * [dev.openpolaris.core.session.SessionMarker] was found on disk (i.e.
 * the previous session's `connect()` succeeded and persisted state, and
 * the user has not asked to "Forget this mount" since).
 *
 * **Two states:**
 *  - **Idle** (no in-flight reconnect): three actions, with a host editor
 *    so the user can change the host without dismissing the dialog.
 *  - **Connecting** ([AppViewModel.reconnecting] is true): a single
 *    "Cancel" action, a spinner, and a "Reconnecting to X…" body. The
 *    dialog cannot be dismissed by tapping outside while in this state
 *    (the underlying `connect()` is in flight and any change of mind
 *    must go through [AppViewModel.cancelReconnect]).
 *
 * Actions in the idle state:
 *  - **Reconnect** — `acceptReconnect()`: writes a fresh marker if the
 *    host was edited, sets the host back to the edited value, kicks off
 *    `connect()`. The dialog stays visible while the connect is in
 *    flight — it morphs to the in-flight state above.
 *  - **Different mount** — `dismissReconnect()`: clears the prompt for
 *    this launch only. The marker stays on disk; the prompt returns on
 *    the next launch. Host edits are preserved in
 *    [AppViewModel.draftHost] so the next resume does not re-prompt
 *    with the old host.
 *  - **Forget this mount** — `forgetMarker()`: deletes the marker file
 *    permanently and clears the host edit buffer. The next launch will
 *    not prompt.
 *
 * Hosted at the root [OpenPolarisApp] surface so it floats above the
 * call-out stack and survives navigating between Connection/Slew/Helpers
 * dialogs. Renders nothing when the flow is `null` (the common case).
 */
@Composable
fun ReconnectDialog(vm: AppViewModel) {
    val prompt by vm.reconnectPrompt.collectAsState()
    if (prompt == null) return
    val p = prompt!!
    val reconnecting by vm.reconnecting.collectAsState()
    val draftHost by vm.draftHost.collectAsState()
    val targetHost = draftHost.ifBlank { p.host }

    if (reconnecting) {
        // 3c.5 in-flight state: single Cancel action so the user cannot
        // accidentally double-fire accept/forget while a connect is
        // pending. The dialog cannot be dismissed by tapping outside
        // (onDismissRequest no-op) — the only path out is Cancel.
        AlertDialog(
            onDismissRequest = { /* no-op: Cancel is the only exit */ },
            title = { Text("Reconnecting to $targetHost…") },
            text = {
                Column {
                    Text("Connect attempt in progress.")
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.cancelReconnect() }) { Text("Cancel") }
            },
        )
        return
    }

    // Idle / prompt state.
    AlertDialog(
        onDismissRequest = { vm.dismissReconnect() },
        title = { Text("Reconnect to last mount?") },
        text = {
            Column {
                Text(buildString {
                    append("Saved mount was last seen ")
                    append(formatAge(p.ageMs))
                    append(" ago")
                    if (p.trackingStarted) append(", tracking was on")
                    append(".")
                })
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = draftHost,
                    onValueChange = { vm.updateDraftHost(it) },
                    label = { Text("Mount address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.acceptReconnect() },
                enabled = draftHost.isNotBlank(),
            ) { Text("Reconnect") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { vm.dismissReconnect() }) {
                    Text("Different mount")
                }
                TextButton(onClick = { vm.forgetMarker() }) {
                    Text("Forget")
                }
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
