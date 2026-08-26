package dev.openpolaris.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import dev.openpolaris.core.domain.Connection

enum class FitMode { Scroll, Fit }

/**
 * Root app surface. Compact (phone portrait) stacks panes in a scrollable
 * column; wide/landscape arranges panes side-by-side. A Scroll/Fit toggle lets
 * the user either scroll normally or scale everything down so it fits the
 * screen with no scrolling (useful in landscape).
 */
@Composable
fun OpenPolarisApp(
    windowSizeClass: WindowSizeClass,
    connectionFactory: () -> Connection,
    onFindWifi: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val vm = AppViewModel(scope, connectionFactory)
    var fitMode by remember { mutableStateOf(FitMode.Scroll) }
    val wide = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    OpenPolarisTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                FitModeBar(fitMode, wide, Modifier.fillMaxWidth()) { fitMode = it }
                when {
                    wide && fitMode == FitMode.Fit -> ScaledLandscape(vm, onFindWifi)
                    wide -> LandscapeColumns(vm, onFindWifi)
                    fitMode == FitMode.Fit -> ScaledColumn(vm, onFindWifi)
                    else -> ScrollingColumn(vm, onFindWifi)
                }
            }
        }
    }
}

@Composable
private fun FitModeBar(mode: FitMode, wide: Boolean, modifier: Modifier = Modifier, onChange: (FitMode) -> Unit) {
    Row(modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = mode == FitMode.Scroll, onClick = { onChange(FitMode.Scroll) }, label = { Text("Scroll") })
        FilterChip(
            selected = mode == FitMode.Fit,
            onClick = { onChange(FitMode.Fit) },
            label = { Text(if (wide) "Fit screen" else "Fit (shrink)") },
        )
    }
}

/** Phone portrait, scroll mode. */
@Composable
private fun ScrollingColumn(vm: AppViewModel, onFindWifi: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ConnectionPane(vm, Modifier.fillMaxWidth(), onFindWifi)
        StatusPane(vm, Modifier.fillMaxWidth())
        JogPane(vm, Modifier.fillMaxWidth())
        GotoPane(vm, Modifier.fillMaxWidth())
        CameraPane(vm, Modifier.fillMaxWidth())
        ReadmePane(Modifier.fillMaxWidth())
    }
}

/** Landscape / wide, scroll mode: two columns of panes. */
@Composable
private fun LandscapeColumns(vm: AppViewModel, onFindWifi: (() -> Unit)?) {
    Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.weight(1f)) {
            ConnectionPane(vm, Modifier.fillMaxWidth(), onFindWifi)
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

/**
 * Renders [content] at natural size, measures its height against the available
 * container height, then draws it scaled down to fit exactly — no scrolling.
 */
@Composable
private fun FitToScreen(content: @Composable () -> Unit) {
    var contentHeightPx by remember { mutableStateOf(0f) }
    var containerHeightPx by remember { mutableStateOf(0f) }
    val scaleFactor =
        if (contentHeightPx > 0f && containerHeightPx > 0f) {
            minOf(1f, containerHeightPx / contentHeightPx)
        } else 1f

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { containerHeightPx = it.size.height.toFloat() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { contentHeightPx = it.size.height.toFloat() }
                .scale(scaleFactor)
                .align(Alignment.TopCenter),
        ) {
            content()
        }
    }
}

/** Phone portrait, fit mode: single column shrunk to fit. */
@Composable
private fun ScaledColumn(vm: AppViewModel, onFindWifi: (() -> Unit)?) {
    FitToScreen {
        ConnectionPane(vm, Modifier.fillMaxWidth(), onFindWifi)
        StatusPane(vm, Modifier.fillMaxWidth())
        JogPane(vm, Modifier.fillMaxWidth())
        GotoPane(vm, Modifier.fillMaxWidth())
        CameraPane(vm, Modifier.fillMaxWidth())
        ReadmePane(Modifier.fillMaxWidth())
    }
}

/** Landscape, fit mode: two columns shrunk to fit — everything visible at once. */
@Composable
private fun ScaledLandscape(vm: AppViewModel, onFindWifi: (() -> Unit)?) {
    FitToScreen {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                ConnectionPane(vm, Modifier.fillMaxWidth(), onFindWifi)
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

