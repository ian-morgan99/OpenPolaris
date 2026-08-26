package dev.openpolaris.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import dev.openpolaris.core.domain.JvmConnection
import dev.openpolaris.ui.OpenPolarisApp

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val wsc = calculateWindowSizeClass(this)
            OpenPolarisApp(wsc) { JvmConnection() }
        }
    }
}
