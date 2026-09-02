package dev.openpolaris.android

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * One row of a Polaris mount access point found by [MountWifiScan].
 *
 * @param ssid the network name (without the surrounding quotes the
 *   `WifiManager` scan results sometimes include).
 * @param bssid the access point's MAC address.
 * @param rssi signal strength in dBm; more negative = weaker.
 * @param capabilities a comma-separated string of WPA/WPA2/WPS/etc.
 *   capability tokens, exactly as the platform reports them.
 */
data class MountAp(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val capabilities: String,
) {
    /** Returns a short, human-readable signal-quality bar. */
    fun qualityLabel(): String = when {
        rssi >= -55 -> "★★★★"
        rssi >= -65 -> "★★★☆"
        rssi >= -75 -> "★★☆☆"
        rssi >= -85 -> "★☆☆☆"
        else        -> "·"
    }
}

/**
 * Drives the "Find & wake Polaris…" flow on the Android Connection
 * pane. Mirrors the Benro app's first-tap behaviour: pulse a soft
 * Bluetooth nudge so a sleeping gimbal wakes up its access point, then
 * run a fresh Wi-Fi scan and filter the results down to SSIDs whose
 * prefix is `polaris`.
 *
 * Usage:
 * ```
 * val helper = MountWifiScan(activity)
 * val aps = helper.scan(progress = { msg -> vm.notifyStatus(msg) })
 * // show aps in a dialog; tapping one calls [join].
 * ```
 *
 * The class is stateless and safe to instantiate per-click; it owns no
 * long-lived resources outside the duration of a single [scan] call.
 */
class MountWifiScan(private val context: Context) {

    private val tag = "MountWifiScan"

    /**
     * Run a wake pulse + Wi-Fi scan and return the matching access
     * points. The optional [progress] callback is invoked from the
     * main thread with short status strings suitable for the
     * connection status line; the function returns an empty list if
     * the scan failed (e.g. permissions missing, no Wi-Fi adapter,
     * no matching APs found).
     */
    suspend fun scan(progress: suspend (String) -> Unit = {}): List<MountAp> {
        progress("Pulsing Bluetooth…")
        btWakePulse()
        // Give the gimbal a beat to actually bring up the AP after the
        // wake nudge lands. 2.5s is the empirically-observed
        // Benro/BenroClone cadence; longer than 4s is more likely to
        // be the user re-pressing the button than the mount waking.
        delayMillis(2_500)

        progress("Scanning Wi-Fi…")
        val rawResults = runWifiScanWithLatch() ?: return emptyList()
        val polaris = rawResults
            .mapNotNull { sr -> sr.toMountApOrNull() }
            .filter { it.ssid.lowercase().startsWith(POLARIS_PREFIX) }
            .sortedByDescending { it.rssi }

        progress(
            if (polaris.isEmpty()) "No Polaris networks found"
            else "Found ${polaris.size} Polaris network(s)"
        )
        return polaris
    }

    /**
     * Build a `WifiNetworkSuggestion` for the given [ap] and ask the
     * framework to consider joining it. Returns true on a best-effort
     * basis (the platform may decline silently on Android < 10, or if
     * the user previously rejected this network).
     */
    @SuppressLint("NewApi")
    fun join(ap: MountAp): Boolean {
        val wm = context.wifiManagerOrNull() ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val suggestion = android.net.wifi.WifiNetworkSuggestion.Builder()
                    .setSsid(ap.ssid)
                    .setBssid(android.net.MacAddress.fromString(ap.bssid))
                    .build()
                val status = wm.addNetworkSuggestions(listOf(suggestion))
                Log.i(tag, "addNetworkSuggestions($ap) -> $status")
                status == android.net.wifi.WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
            } else {
                // Pre-Android-10: fall back to the old
                // WifiManager.enableNetwork path. We don't have the
                // PSK, so this is just a signal-to-the-system nudge;
                // the user still has to enter the password in the
                // system Wi-Fi dialog that follows.
                @Suppress("DEPRECATION")
                val netId = wm.addNetwork(
                    android.net.wifi.WifiConfiguration().apply {
                        SSID = "\"" + ap.ssid + "\""
                        BSSID = ap.bssid
                        priority = 1
                    }
                )
                Log.i(tag, "addNetwork($ap) -> $netId")
                netId != -1
            }
        } catch (se: SecurityException) {
            Log.w(tag, "join: missing permission for $ap", se)
            false
        } catch (e: IllegalArgumentException) {
            // MacAddress.fromString can throw IAE for malformed BSSIDs
            // coming out of buggy drivers. Treat that the same as a
            // soft failure rather than crashing the UI.
            Log.w(tag, "join: invalid BSSID for $ap", e)
            false
        }
    }

    // ---- internals ----

    private fun btWakePulse() {
        // We don't have the gimbal's GATT service UUID baked in (the
        // Benro protocol is closed), so we issue a soft nudge:
        // BluetoothAdapter.startDiscovery() forces the BT radio to
        // wake and look around, which is the same "are there any
        // familiar peers nearby?" signal the Benro app uses as a
        // first-step wake. If the gimbal is awake but not paired
        // to this phone, this is a no-op; if it's asleep with the
        // AP down, the discovery broadcast is what nudges it to
        // bring the AP back up.
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // BLUETOOTH_CONNECT is the Android 12+ gate for
                // startDiscovery(); without it, skip cleanly so the
                // Wi-Fi scan can still run.
                return
            }
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            @SuppressLint("MissingPermission")
            val started = adapter.startDiscovery()
            Log.i(tag, "BT startDiscovery -> $started")
        } catch (se: SecurityException) {
            Log.w(tag, "BT wake: SecurityException, skipping", se)
        } catch (t: Throwable) {
            Log.w(tag, "BT wake: unexpected ${t.javaClass.simpleName}", t)
        }
    }

    /**
     * Run a one-shot Wi-Fi scan and block on the
     * `SCAN_RESULTS_AVAILABLE_ACTION` broadcast. We use a latch
     * rather than a coroutine channel because the receiver must be
     * registered on the main thread, and we already own a 7-second
     * timeout to keep the user from waiting forever on a buggy
     * device.
     */
    private fun runWifiScanWithLatch(): List<ScanResult>? {
        val wm = context.wifiManagerOrNull() ?: run {
            Log.w(tag, "WifiManager unavailable")
            return null
        }
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            Log.w(tag, "scan: missing location permission")
            return null
        }
        val latch = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    latch.countDown()
                }
            }
        }
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        val mainHandler = Handler(Looper.getMainLooper())
        return try {
            mainHandler.post {
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            }
            @SuppressLint("MissingPermission")
            val started = wm.startScan()
            Log.i(tag, "WifiManager.startScan -> $started")
            // 7s is generous; most devices return within ~3s. If the
            // platform never fires the broadcast (vendor bug) the
            // latch returns false and we fall through.
            latch.await(SCAN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (latch.count > 0) {
                Log.w(tag, "scan: timed out after ${SCAN_TIMEOUT_MS}ms")
            }
            @SuppressLint("MissingPermission")
            wm.scanResults
        } catch (se: SecurityException) {
            Log.w(tag, "scan: SecurityException", se)
            null
        } catch (t: Throwable) {
            Log.w(tag, "scan: unexpected ${t.javaClass.simpleName}", t)
            null
        } finally {
            try {
                mainHandler.post { context.unregisterReceiver(receiver) }
            } catch (e: Throwable) {
                // Receiver may already be unregistered; not a fatal
                // error here.
            }
        }
    }

    private fun hasPermission(perm: String): Boolean =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    private fun Context.wifiManagerOrNull(): WifiManager? =
        ContextCompat.getSystemService(this, WifiManager::class.java)

    private fun ScanResult.toMountApOrNull(): MountAp? {
        // SSID is the network name; on some Android versions the
        // value is the literal string "<unknown ssid>" when the
        // caller doesn't hold the location permission. Skip those
        // rows so they don't pollute the result list.
        val raw = SSID ?: return null
        if (raw.isBlank() || raw == "<unknown ssid>") return null
        val clean = raw.trim('"')
        return MountAp(
            ssid = clean,
            bssid = BSSID ?: "",
            rssi = level,
            capabilities = capabilities ?: "",
        )
    }

    private suspend fun delayMillis(ms: Long) =
        kotlinx.coroutines.delay(ms)

    companion object {
        /** SSID prefix the gimbal's access point advertises. */
        const val POLARIS_PREFIX: String = "polaris"

        /** Max time to wait for a single Wi-Fi scan to land. */
        const val SCAN_TIMEOUT_MS: Long = 7_000
    }
}
