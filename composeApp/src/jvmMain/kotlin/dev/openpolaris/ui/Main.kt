package dev.openpolaris.ui

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.openpolaris.core.domain.JvmConnection
import dev.openpolaris.core.net.BridgeOrchestrator
import dev.openpolaris.core.session.SessionStore
import dev.openpolaris.core.session.path.defaultSessionPath

/**
 * Desktop entry point for OpenPolaris.
 *
 * Doubles as a "can I run this without an Android device?" smoke test: a
 * developer on a laptop joined to the gimbal's WiFi can `gradle run` or
 * double-click the produced jar and have a full window-size-aware
 * Material 3 UI open against `192.168.0.1:9090`.
 *
 * Mirrors the role of `dev.openpolaris.android.MainActivity` but without
 * the Android permission / lifecycle dance. The same shared VM, the same
 * shared protocol, the same [OpenPolarisApp] composable.
 *
 * Resource context is not installed here because the JVM actual reads
 * `commonMain/resources/` from the classpath directly (no Android
 * `Context` is needed). The JVM actual of `dev.openpolaris.core.io.FilePicker`
 * uses `java.awt.FileDialog` so no further wiring is required.
 *
 * Bridge and Wake are wired to a real [BridgeOrchestrator] on the desktop.
 * The orchestrator's [BridgeOrchestrator.bridgeToMount] needs a saved
 * `profile` and an `ifname` — defaults are set below to the typical
 * Benro Polaris AP profile name and a common Intel Wi-Fi interface name.
 * Edit [POLARIS_WIFI_PROFILE] and [POLARIS_WIFI_IFNAME] if your setup
 * differs, or move them to a config file.
 */
private const val POLARIS_WIFI_PROFILE = "polaris_d13e86"
private const val POLARIS_WIFI_IFNAME = "wlp8s0"

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
fun main() = application {
    val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)
    val bridge = remember { BridgeOrchestrator() }
    Window(onCloseRequest = ::exitApplication, state = windowState, title = "OpenPolaris") {
        OpenPolarisApp(
            windowSizeClass = calculateWindowSizeClass(),
            connectionFactory = { JvmConnection() },
            sessionStore = SessionStore(defaultSessionPath()),
            connectWifi = { progress ->
                bridge.bridgeToMount(
                    profile = POLARIS_WIFI_PROFILE,
                    ifname = POLARIS_WIFI_IFNAME,
                    progress = progress,
                )
            },
            wakeProbe = { progress ->
                bridge.wakeOnly(progress = progress)
            },
        )
    }
}
