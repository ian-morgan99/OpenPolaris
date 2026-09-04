package dev.openpolaris.core.net

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Glues the three bridge phases together so the desktop UI (or any other
 * caller) can run the whole thing with one call.
 *
 * 1. **BT wake** (best-effort) — one-shot scan for the gimbal, then a bare
 *    GATT connect to it. The connect itself is the wake pulse; the Benro
 *    firmware notices and brings up its Wi-Fi AP. See
 *    `BluetoothProbe.wake()` and `polaris-re-results.md` §8.5.
 * 2. **NM up** — `nmcli connection up <profile> ifname <ifname>` on a saved
 *    profile. The profile is *required* because scans are forbidden on this
 *    laptop (see `NoScanGuardTest`).
 * 3. **Policy route** — installs an `ip rule` + `ip route` table so traffic
 *    to the gimbal subnet leaves via `<ifname>` only, and the Wi-Fi interface
 *    is never promoted to the system default.
 *
 * The orchestrator never blocks the caller for long. Each phase is a
 * suspending function, and the lambda [progress] is invoked from the IO
 * dispatcher so the caller can render status text without jumping threads.
 *
 * Returns `true` iff the link is up and the policy route is installed.
 */
class BridgeOrchestrator(
    private val wifi: WifiBridge = WifiBridge(),
    private val bt: BluetoothProbe = BluetoothProbe(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Run the full bring-up. [progress] is invoked from the IO dispatcher
     * with short, human-readable status strings suitable for direct display.
     */
    suspend fun bridgeToMount(
        profile: String,
        ifname: String,
        progress: suspend (String) -> Unit = {},
    ): Boolean = withContext(io) {
        runCatching { wakeOverBluetooth(progress) }
            .onFailure {
                progress(
                    "BT wake failed: ${it.message ?: it::class.simpleName}; " +
                        "trying the saved Wi-Fi profile"
                )
            }

        progress("Bringing $profile up on $ifname…")
        try {
            wifi.connectByProfile(profile, ifname)
        } catch (e: Exception) {
            progress("Wi-Fi activation failed: ${e.message ?: e::class.simpleName}")
            return@withContext false
        }

        progress("Waiting for link on $ifname…")
        val linkUp = try {
            wifi.awaitLinkUp(ifname, timeoutMs = 15_000)
        } catch (e: Exception) {
            progress("Wi-Fi link check failed: ${e.message ?: e::class.simpleName}")
            return@withContext false
        }
        if (!linkUp) {
            progress("Link never came up on $ifname — is the gimbal powered on?")
            return@withContext false
        }

        progress("Installing policy route for ${wifi.gimbalCidrForDebug} → $ifname")
        try {
            wifi.installPolicyRoute(ifname)
        } catch (e: Exception) {
            progress("Policy route failed: ${e.message ?: e::class.simpleName}")
            return@withContext false
        }

        progress("Mount Wi-Fi ready on $ifname")
        true
    }

    /**
     * BT-wake only — fires the GATT-connect pulse and settles, but does
     * NOT bring up the Wi-Fi profile or install a policy route. Used by
     * the desktop "Wake" button when the user wants to pulse the gimbal
     * awake on a cold start before deciding whether to bridge. Returns true
     * only when the GATT wake pulse completed. A missing device is reported
     * as an inconclusive result rather than being incorrectly called a
     * successful wake.
     */
    suspend fun wakeOnly(
        progress: suspend (String) -> Unit = {},
    ): Boolean = withContext(io) {
        try {
            wakeOverBluetooth(progress)
            true
        } catch (e: Exception) {
            progress("BT wake failed: ${e.message ?: e::class.simpleName}")
            false
        }
    }

    /**
     * Tear-down. The reverse of [bridgeToMount]: policy route first, then NM
     * down. Safe to call even if the link isn't up; every step is wrapped
     * in `runCatching`.
     */
    suspend fun tearDown(
        profile: String,
        ifname: String,
        progress: suspend (String) -> Unit = {},
    ) = withContext(io) {
        progress("Removing policy route on $ifname")
        runCatching { wifi.removePolicyRoute(ifname) }
        progress("Bringing $profile down")
        runCatching { wifi.disconnectByProfile(profile) }
        progress("Mount Wi-Fi torn down")
    }

    private suspend fun wakeOverBluetooth(progress: suspend (String) -> Unit) {
        // The wake path is unconditional on the Benro Polaris: a bare GATT
        // connect pulses the firmware's Wi-Fi AP. If the gimbal is already
        // awake, the scan and connect will still succeed (no-op).
        progress("Scanning for gimbal over Bluetooth…")
        val device = bt.discover(timeoutMs = 5_000)
        if (device == null) {
            throw IllegalStateException(
                "no Polaris device discovered; move it closer or use Connect to try the saved Wi-Fi profile"
            )
        }
        progress("Waking ${device.name} via Bluetooth…")
        bt.wake(device)
        // Brief settle so the firmware has time to bring the AP up after the
        // BT connect pulse. wakeSettleMs inside BluetoothProbe handles the
        // primary wait; this gives a little extra for slow firmware.
        var settled = 0
        while (settled < 8 && coroutineContext.isActive) {
            delay(250); settled++
        }
    }
}
