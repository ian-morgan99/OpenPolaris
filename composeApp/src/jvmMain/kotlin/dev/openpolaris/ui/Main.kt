package dev.openpolaris.ui

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.openpolaris.core.domain.JvmConnection
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
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
fun main() = application {
    val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)
    Window(onCloseRequest = ::exitApplication, state = windowState, title = "OpenPolaris") {
        OpenPolarisApp(
            windowSizeClass = calculateWindowSizeClass(),
            connectionFactory = { JvmConnection() },
            sessionStore = SessionStore(defaultSessionPath()),
        )
    }
}
