package dev.openpolaris.desktop

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.openpolaris.core.domain.JvmConnection
import dev.openpolaris.core.net.BridgeOrchestrator
import dev.openpolaris.ui.OpenPolarisApp
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class)
fun main() = application {
    val state = rememberWindowState()
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

        val orchestrator = BridgeOrchestrator()
        val scope = rememberCoroutineScope()
        OpenPolarisApp(
            windowSizeClass = effective,
            connectionFactory = { JvmConnection() },
            connectWifi = { progress ->
                scope.launch {
                    orchestrator.bridgeToMount(
                        profile = "polaris_d13e86",
                        ifname = "wlp8s0",
                        progress = progress,
                    )
                }
            },
        )
    }
}

