package dev.openpolaris.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.openpolaris.core.domain.Connection

/**
 * Root app surface. Single-pane (phone) stacks panes vertically; two-pane
 * (tablet/iPad/desktop-wide) puts status+jog beside the connection pane.
 */
@Composable
fun OpenPolarisApp(
    windowSizeClass: WindowSizeClass,
    connectionFactory: () -> Connection,
    onFindWifi: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val vm = AppViewModel(scope, connectionFactory)

    OpenPolarisTheme {
        Surface(Modifier.fillMaxSize()) {
            when (windowSizeClass.widthSizeClass) {
                WindowWidthSizeClass.Compact -> {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    ) {
                        ConnectionPane(vm, Modifier.fillMaxWidth(), onFindWifi)
                        StatusPane(vm, Modifier.fillMaxWidth())
                        JogPane(vm, Modifier.fillMaxWidth())
                        GotoPane(vm, Modifier.fillMaxWidth())
                        CameraPane(vm, Modifier.fillMaxWidth())
                        ReadmePane(Modifier.fillMaxWidth())
                    }
                }
                else -> {
                    Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Column(Modifier.weight(1f)) {
                            ConnectionPane(vm, Modifier.fillMaxWidth())
                            StatusPane(vm, Modifier.fillMaxWidth())
                            GotoPane(vm, Modifier.fillMaxWidth())
                        }
                        Column(Modifier.weight(1f)) {
                            JogPane(vm, Modifier.fillMaxWidth())
                            CameraPane(vm, Modifier.fillMaxWidth())
                            ReadmePane(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
