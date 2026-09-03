package dev.openpolaris.desktop

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.openpolaris.core.domain.JvmConnection
import dev.openpolaris.core.net.BridgeOrchestrator
import dev.openpolaris.ui.OpenPolarisApp
import java.io.File
import java.util.Properties

// Default mount AP profile name. The Benro Polaris broadcasts an open
// `polaris_<id>` SSID; the trailing id is the gimbal's last-three-of-MAC
// segment and varies per unit. We default to the one this module's original
// author was running, but expose overrides below.
private const val DEFAULT_POLARIS_PROFILE = "polaris_d13e86"

// Default Wi-Fi interface. The orchestrator calls out to NetworkManager and
// `iw` and needs to know which radio to bring the segregated network up on.
// wlp8s0 is the most common modern Intel Wi-Fi naming, but obviously varies
// per machine. Overridable.
private const val DEFAULT_POLARIS_IFNAME = "wlp8s0"

private fun resolveProfile(): String {
    System.getenv("OPENPOLARIS_PROFILE")?.takeIf { it.isNotBlank() }?.let { return it }
    // CLI flag wins over env. Convention: --profile=<name>
    val cli = System.getProperty("openpolaris.profile")
        ?: argsValue("--profile")
    if (!cli.isNullOrBlank()) return cli
    val props = readProperties() ?: return DEFAULT_POLARIS_PROFILE
    return props.getProperty("profile")?.takeIf { it.isNotBlank() }
        ?: DEFAULT_POLARIS_PROFILE
}

private fun resolveIfname(): String {
    System.getenv("OPENPOLARIS_IFNAME")?.takeIf { it.isNotBlank() }?.let { return it }
    val cli = System.getProperty("openpolaris.ifname")
        ?: argsValue("--ifname")
    if (!cli.isNullOrBlank()) return cli
    val props = readProperties() ?: return DEFAULT_POLARIS_IFNAME
    return props.getProperty("ifname")?.takeIf { it.isNotBlank() }
        ?: DEFAULT_POLARIS_IFNAME
}

private fun argsValue(flag: String): String? {
    val argv = System.getProperty("sun.java.command") ?: return null
    // Best-effort: argv is "mainclass [args...]" — only consulted for --flag
    // and --flag=value, which is good enough for a desktop GUI launcher.
    val parts = argv.split(' ', '\t', '\n').drop(1)
    for (p in parts) {
        if (p == flag) continue
        if (p.startsWith("$flag=")) return p.substring(flag.length + 1)
    }
    return null
}

private fun readProperties(): Properties? {
    val home = System.getProperty("user.home") ?: return null
    val file = File(home, ".config/openpolaris/desktop.properties")
    if (!file.isFile) return null
    return runCatching {
        Properties().apply { file.inputStream().use { load(it) } }
    }.getOrNull()
}

@OptIn(androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class)
fun main() = application {
    val state = rememberWindowState()
    val orchestrator = BridgeOrchestrator()
    val profile = resolveProfile()
    val ifname = resolveIfname()
    Window(onCloseRequest = ::exitApplication, title = "Open Polaris", state = state) {
        // Compact threshold: treat narrow windows as phone layout for testing.
        val widthClass =
            if (state.size.width < 700.dp) WindowWidthSizeClass.Compact
            else WindowWidthSizeClass.Medium
        val wsc = WindowSizeClass.calculateFromSize(
            DpSize(state.size.width, state.size.height)
        )
        val effective = if (widthClass == WindowWidthSizeClass.Compact) {
            WindowSizeClass.calculateFromSize(DpSize(360.dp, 800.dp))
        } else wsc

        // The desktop GUI is driven by the shared [AppViewModel]'s Wake and
        // Bridge buttons. Wiring [connectWifi] and [wakeProbe] into the VM
        // makes those buttons render and gives them the right progress flow
        // (status messages from the orchestrator stream into the pane's
        // status line via the VM's own reconnect-generation race handling).
        // Previously this only wired [onFindWifi] to the bridge, which both
        // hid the proper buttons and mislabelled a fallback that the
        // desktop does not have a system equivalent for.
        OpenPolarisApp(
            windowSizeClass = effective,
            connectionFactory = { JvmConnection() },
            connectWifi = { progress ->
                orchestrator.bridgeToMount(
                    profile = profile,
                    ifname = ifname,
                    progress = progress,
                )
            },
            wakeProbe = { progress ->
                orchestrator.wakeOnly(progress = progress)
            },
        )
    }
}

