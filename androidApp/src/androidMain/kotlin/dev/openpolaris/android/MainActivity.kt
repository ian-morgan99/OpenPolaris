package dev.openpolaris.android

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.rememberCoroutineScope
import dev.openpolaris.core.domain.JvmConnection
import dev.openpolaris.core.session.SessionStore
import dev.openpolaris.core.session.path.sessionStorePathForFilesDir
import dev.openpolaris.ui.AppViewModel
import dev.openpolaris.ui.OpenPolarisApp

/**
 * 3c.4 of issue #7: this is the Android production host for the multiplatform
 * [OpenPolarisApp] composable. The activity owns the [AppViewModel] for its
 * own lifecycle (so the same VM is reachable from `onResume`) and hands it
 * down into the composable instead of letting the composable build its own.
 *
 * The VM is constructed with a [SessionStore] rooted at
 * `Context.filesDir/openpolaris/session.json` — the per-app sandbox
 * documented in [sessionStorePathForFilesDir]. The default
 * `defaultSessionPath()` would land in `user.home`, which on Android is
 * `/data/user/0` shared by every app and not safe to write to without
 * root, so we always inject the real path here.
 *
 * `onResume` triggers [AppViewModel.tryReconnectIfMarkerExists]. That call
 * is idempotent: if there is no marker, the dialog stays closed; if there
 * is one, the "Reconnect to <host>?" dialog appears on top of the
 * composable (rendered by `ReconnectDialog(vm)` inside
 * [OpenPolarisApp]). The user can pick Reconnect, Different mount, or
 * Forget.
 */
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AppViewModel

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Build the per-app SessionStore path BEFORE the composable so the
        // VM uses the right one from its first read. The composable still
        // owns the VM (and thus the connect/disconnect lifecycle), but the
        // marker file is rooted in the app sandbox.
        val sessionStore = SessionStore(
            filePath = sessionStorePathForFilesDir(applicationContext.filesDir),
        )

        setContent {
            val wsc = calculateWindowSizeClass(this)
            val scope = rememberCoroutineScope()
            viewModel = AppViewModel(
                scope = scope,
                connectionFactory = { JvmConnection() },
                sessionStore = sessionStore,
            )
            OpenPolarisApp(
                windowSizeClass = wsc,
                connectionFactory = { JvmConnection() },
                onFindWifi = {
                    // Opens the system Wi-Fi picker so the user can join Polaris_XXXX.
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                },
                onLaunchVr = {
                    // 3h-BUG: pass host and port so the VR activity connects
                    // to the same endpoint the user just configured (instead
                    // of hard-coding 192.168.43.1:8080 in VRActivity's
                    // companion defaults). Reading from viewModel.{host,port}
                    // keeps the two screens in sync.
                    val intent = Intent(this, VRActivity::class.java).apply {
                        putExtra(VRActivity.EXTRA_HOST, viewModel.host)
                        putExtra(VRActivity.EXTRA_PORT, viewModel.port)
                    }
                    startActivity(intent)
                },
                viewModel = viewModel,
            )
        }
    }

    /**
     * 3c.4 reconnect trigger. Called whenever the activity returns to the
     * foreground (initial launch, after the user backgrounds and reopens,
     * after dismissing the system Wi-Fi picker, etc.). Cheap when there
     * is no marker — the VM short-circuits before any I/O.
     */
    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.tryReconnectIfMarkerExists()
        }
    }
}
