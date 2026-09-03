package dev.openpolaris.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.DialogProperties
import dev.openpolaris.core.astro.AstroMath
import dev.openpolaris.core.domain.JvmConnection
import dev.openpolaris.core.domain.installResourceContext
import dev.openpolaris.core.io.FilePicker
import dev.openpolaris.core.io.FilePickerRegistry
import dev.openpolaris.core.session.SessionStore
import dev.openpolaris.core.session.path.sessionStorePathForFilesDir
import dev.openpolaris.ui.AppViewModel
import dev.openpolaris.ui.OpenPolarisApp
import dev.openpolaris.android.BuildConfig
import kotlinx.coroutines.launch

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

    // FilePickerRegistry is populated in onCreate and cleared in onDestroy
    // so `FilePicker` (which lives in the `shared` module and so has no
    // direct access to this Activity) can hand a picked Uri back to us for
    // the OpenDocument flow. The launcher must be registered before the
    // activity reaches STARTED; doing it here in onCreate is the safe spot.
    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        // Hand the Uri to the registry, which copies it into the cache
        // directory and returns a real absolute path the caller can read.
        val path = FilePickerRegistry.handleResult(uri)
        // Fire the one-shot callback the picker was launched with. Always
        // clear it, even on cancel / error, so a leaked callback can't
        // fire twice.
        val cb = FilePickerRegistry.pendingCallback
        FilePickerRegistry.pendingCallback = null
        cb?.invoke(path)
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Publish this Activity as the host for FilePicker so the shared
        // module's expect/actual can launch the SAF chooser without taking
        // a hard reference to `ComponentActivity`. Cleared in onDestroy.
        FilePickerRegistry.appContext = applicationContext
        FilePickerRegistry.launcher = openDocumentLauncher
        // Make bundled `commonMain/resources/*.json` shards visible
        // to the readResourceText() expect/actual so the Tonight pane
        // can load the embedded catalog + comet shards.
        installResourceContext(applicationContext)

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

            // The dialog that lists Polaris APs the latest scan found.
            // Hoisted to the activity so it persists across recomposition
            // and so the click handler on the dialog buttons has a stable
            // place to clear state.
            var foundAps by remember { mutableStateOf<List<MountAp>>(emptyList()) }
            var showFoundAps by remember { mutableStateOf(false) }
            val scanFlow = viewModel.scanning.collectAsState()

            // "Find & wake Polaris…" — mirrors the Benro app's
            // first-tap flow. The host owns the coroutine scope and the
            // permission dance; the composable just hands us a
            // progress sink so we can write into `vm.statusMessage`.
            //
            // This is the fix for the regression where the Android
            // version of the button was hard-wired to
            // `Settings.ACTION_WIFI_SETTINGS`, which routes to the
            // system Wi-Fi settings root instead of pulsing the BT
            // wake + scanning for the polaris* AP. Desktop is
            // unaffected (this parameter is only set on Android).
            val wifiScanHelper = remember { MountWifiScan(this) }
            val permsLauncher = remember {
                registerForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { grants ->
                    val allGranted = grants.values.all { it }
                    if (!allGranted) {
                        viewModel.notifyStatus(
                            "Wi-Fi/BT permission denied; cannot find Polaris"
                        )
                    } else {
                        scope.launch {
                            viewModel.setScanning(true)
                            try {
                                val aps = wifiScanHelper.scan { msg ->
                                    viewModel.notifyStatus(msg)
                                }
                                if (aps.isNotEmpty()) {
                                    foundAps = aps
                                    showFoundAps = true
                                }
                            } finally {
                                viewModel.setScanning(false)
                            }
                        }
                    }
                }
            }

            fun launchMountScan() {
                val perms = buildList {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                }
                val missing = perms.filter {
                    checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) {
                    scope.launch {
                        viewModel.setScanning(true)
                        try {
                            val aps = wifiScanHelper.scan { msg ->
                                viewModel.notifyStatus(msg)
                            }
                            if (aps.isNotEmpty()) {
                                foundAps = aps
                                showFoundAps = true
                            }
                        } finally {
                            viewModel.setScanning(false)
                        }
                    }
                } else {
                    permsLauncher.launch(missing.toTypedArray())
                }
            }

            if (showFoundAps && foundAps.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { showFoundAps = false },
                    properties = DialogProperties(dismissOnClickOutside = true),
                    title = { Text("Polaris networks found") },
                    text = {
                        // Render one row per AP. Kept as plain text
                        // (not a LazyColumn) because the result set is
                        // bounded — a Polaris gimbal only ever
                        // advertises itself, so realistic sizes are
                        // 1-3 rows.
                        val sb = StringBuilder()
                        for (ap in foundAps) {
                            sb.append(ap.ssid)
                                .append("   ")
                                .append(ap.qualityLabel())
                                .append("   ")
                                .append(ap.rssi).append(" dBm\n")
                        }
                        Text(sb.toString().trimEnd())
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            // Pick the strongest (already sorted) and
                            // hand it to the system. On Android 10+
                            // this pops a "Connect?" notification; on
                            // older versions we fall back to
                            // openNetworkSuggestions / the system
                            // Wi-Fi settings.
                            val picked = foundAps.first()
                            val ok = wifiScanHelper.join(picked)
                            viewModel.notifyStatus(
                                if (ok) "Requesting join: ${picked.ssid}"
                                else "Could not auto-join ${picked.ssid}; open Wi-Fi settings"
                            )
                            showFoundAps = false
                            if (!ok) {
                                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                            }
                        }) { Text("Join strongest") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            // Send the user to the system Wi-Fi
                            // picker so they can pick a non-Polaris
                            // network, or enter a password manually.
                            showFoundAps = false
                            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        }) { Text("Open Wi-Fi settings") }
                    },
                )
            }

            OpenPolarisApp(
                windowSizeClass = wsc,
                connectionFactory = { JvmConnection() },
                onFindWifi = {
                    // Legacy fallback: open the system Wi-Fi picker.
                    // The new "Find & wake Polaris…" button is the
                    // primary entry point on Android; this is kept so
                    // a user who wants to manually pick a different
                    // network still has a path. (Was the *only* path
                    // before the wake+scan fix; now it's a fallback.)
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                },
                onMountWifiScan = { progress ->
                    // Hand the host's progress sink to the helper so
                    // it can update vm.statusMessage directly. The
                    // host still owns the coroutine scope (and thus
                    // the button-disable dance via [setScanning]).
                    // We launch the coroutine here so the composable
                    // can return immediately and not block on
                    // dialog/permission flows.
                    scope.launch {
                        viewModel.setScanning(true)
                        try {
                            val aps = wifiScanHelper.scan(progress)
                            if (aps.isNotEmpty()) {
                                foundAps = aps
                                showFoundAps = true
                            }
                        } finally {
                            viewModel.setScanning(false)
                        }
                    }
                },
                onLaunchVr = {
                    // 3h-BUG: pass host and port so the VR activity connects
                    // to the same endpoint the user just configured (instead
                    // of hard-coding 192.168.43.1:8080 in VRActivity's
                    // companion defaults). Reading from viewModel.{host,port}
                    // keeps the two screens in sync.
                    //
                    // 7.4 (slice 2): also pass the most recent plate-solve
                    // (field centre) and the user-entered GoTo target so
                    // VRActivity can draw a reticle at the target's
                    // projected screen position. If there is no solve yet,
                    // the marker is silently hidden — we do not refuse to
                    // launch VR. The target strings are parsed with
                    // AstroMath so they share the validation the
                    // "Slew to target" button uses; an unparsable target
                    // also bails on the marker (no error UX in this path).
                    val solve = viewModel.lastSolveResult.value
                    val targetRaDeg = AstroMath.parseRa(viewModel.gotoRa)
                    val targetDecDeg = AstroMath.parseDec(viewModel.gotoDec)
                    val intent = Intent(this, VRActivity::class.java).apply {
                        putExtra(VRActivity.EXTRA_HOST, viewModel.host)
                        putExtra(VRActivity.EXTRA_PORT, viewModel.port)
                        if (solve != null && targetRaDeg != null && targetDecDeg != null) {
                            putExtra(VRActivity.EXTRA_SOLVE_RA_DEG, solve.raDeg)
                            putExtra(VRActivity.EXTRA_SOLVE_DEC_DEG, solve.decDeg)
                            putExtra(VRActivity.EXTRA_SOLVE_CONFIDENCE, solve.confidence.toFloat())
                            // Stamp at launch time, using the solve's
                            // recorded timestamp (issue #12) so the marker
                            // fades by actual age instead of always drawing
                            // at full alpha.
                            val ageMs = if (solve.timestampMs > 0L) {
                                (System.currentTimeMillis() - solve.timestampMs).coerceAtLeast(0L)
                            } else 0L
                            putExtra(VRActivity.EXTRA_SOLVE_AGE_MS, ageMs)
                            // Double? isn't a valid putExtra overload on
                            // Intent — only primitive `double` (or
                            // DoubleArray). The caller already gates on
                            // both being non-null, so unwrap to 0.0 as a
                            // "no target" sentinel that VRActivity
                            // recognises.
                            putExtra(VRActivity.EXTRA_TARGET_RA_DEG, targetRaDeg ?: 0.0)
                            putExtra(VRActivity.EXTRA_TARGET_DEC_DEG, targetDecDeg ?: 0.0)
                            // Stream 15.1 (issue #15): hand VRActivity the
                            // current camera profile from the VM. The
                            // value is a `PER_MOUNT_DEFAULT` for the
                            // Polaris eyepiece today; once a real sensor
                            // stream lands, the VM will publish a profile
                            // with `source = SENSOR` and the same Intent
                            // extras will carry the live values without
                            // any change here.
                            val profile = viewModel.cameraProfile.value
                            putExtra(VRActivity.EXTRA_FOV_X_DEG, profile.fovXDeg)
                            putExtra(VRActivity.EXTRA_FOV_Y_DEG, profile.fovYDeg)
                        }
                    }
                    startActivity(intent)
                },
                viewModel = viewModel,
                // Build-identity footer in the Settings dialog so the user
                // can confirm at a glance which app is actually running on
                // the device. Helps distinguish a real dev.openpolaris.app
                // build from a stale fork / sideloaded APK (issue #43).
                versionLabel = "v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})",
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

    override fun onDestroy() {
        // Drop the registry's references to the activity / launcher so we
        // don't leak the Activity across configuration changes. The picker
        // is single-slot; any in-flight callback will be lost on rotation,
        // which is acceptable for v1.
        FilePickerRegistry.clear()
        super.onDestroy()
    }
}
