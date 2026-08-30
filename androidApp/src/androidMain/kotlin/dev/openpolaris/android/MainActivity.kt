package dev.openpolaris.android

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import dev.openpolaris.core.domain.JvmConnection
import dev.openpolaris.core.domain.installResourceContext
import dev.openpolaris.ui.OpenPolarisApp

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make bundled `commonMain/resources/*.json` shards visible
        // to the readResourceText() expect/actual so the Tonight pane
        // can load the embedded catalog + comet shards.
        installResourceContext(applicationContext)
        setContent {
            val wsc = calculateWindowSizeClass(this)
            OpenPolarisApp(wsc, { JvmConnection() }) {
                // Opens the system Wi-Fi picker so the user can join Polaris_XXXX.
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        }
    }
}
