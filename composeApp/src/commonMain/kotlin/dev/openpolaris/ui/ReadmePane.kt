package dev.openpolaris.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * In-app quick-start guide ("Readme"). Kept short and scannable — the full
 * documentation lives in the repo docs.
 */
@Composable
fun ReadmePane(modifier: Modifier = Modifier) {
    val h = MaterialTheme.typography.titleSmall
    val body = MaterialTheme.typography.bodySmall

    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Readme", style = MaterialTheme.typography.titleMedium)

            Text("1. Connect", style = h)
            Text(
                "Join the mount's Wi-Fi network (Polaris_XXXX), then tap Connect. " +
                    "The host is usually 192.168.43.1. No hardware? Tap Demo mode to explore.",
                style = body,
            )

            HorizontalDivider()

            Text("2. Set your location", style = h)
            Text(
                "Enter latitude (N positive) and longitude (E positive) in the Slew pane. " +
                    "This is required for RA/Dec slews and correct pointing math.",
                style = body,
            )

            HorizontalDivider()

            Text("3. Slew to a target", style = h)
            Text(
                "Az/Alt mode: type azimuth and altitude in degrees and tap Slew. " +
                    "RA/Dec mode: enter coordinates as HH MM SS / ±DD MM SS — the app converts them for you. " +
                    "Cancel slew stops a move in progress; Reset position re-zeros the gimbal.",
                style = body,
            )

            HorizontalDivider()

            Text("4. Track", style = h)
            Text(
                "Toggle Tracking in Status to follow the stars at sidereal rate. " +
                    "Half speed is for lunar/planetary tracking. AHRS improves position reporting.",
                style = body,
            )

            HorizontalDivider()

            Text("5. Align for accuracy", style = h)
            Text(
                "For accurate GoTo: jog to center 2–3 bright stars spread across the sky, tapping " +
                    "Record star after each. The app builds a pointing model that corrects subsequent slews. " +
                    "Reset alignment clears it.",
                style = body,
            )

            HorizontalDivider()

            Text("Safety", style = h)
            Text(
                "Hardware limits are read from the mount and enforced before every slew — commands that " +
                    "would overwind a motor are refused. Auto-level can be run or toggled from the Slew pane. " +
                    "Watch the gimbal during first use.",
                style = body,
            )

            HorizontalDivider()

            Text("Camera", style = h)
            Text(
                "Camera controls are experimental until validated against real hardware — " +
                    "use Demo mode to try them safely.",
                style = body,
            )

            HorizontalDivider()

            Text(
                "Full documentation: SPEC, PROTOCOL, PLAN and OVERVIEW docs in the project repository.",
                style = body,
            )
        }
    }
}
