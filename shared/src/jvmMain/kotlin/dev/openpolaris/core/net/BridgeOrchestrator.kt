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
            .onFailure { progress("BT wake skipped: ${it.message ?: it::class.simpleName}") }

        progress("Bringing $profile up on $ifname…")
        runCatching { wifi.connectByProfile(profile, ifname) }
            .onFailure { progress("nmcli up failed: ${it.message ?: it::class.simpleName}") }

        progress("Waiting for link on $ifname…")
        val linkUp = runCatching { wifi.awaitLinkUp(ifname, timeoutMs = 15_000) }
            .getOrDefault(false)
        if (!linkUp) {
            progress("Link never came up on $ifname — is the gimbal powered on?")
            return@withContext false
        }

        progress("Installing policy route for ${wifi.gimbalCidrForDebug} → $ifname")
        runCatching { wifi.installPolicyRoute(ifname) }
            .onFailure { progress("Policy route failed: ${it.message ?: it::class.simpleName}") }

        progress("Mount Wi-Fi ready on $ifname")
        true
    }

    /**
     * BT-wake only — fires the GATT-connect pulse and settles, but does
     * NOT bring up the Wi-Fi profile or install a policy route. Used by
     * the desktop "Wake" button when the user wants to pulse the gimbal
     * awake on a cold start before deciding whether to bridge. Returns
     * `true` when the pulse completed (whether or not a device was found;
     * missing-device is treated as "gimbal already awake" and is not a
     * failure).
     */
    suspend fun wakeOnly(
        progress: suspend (String) -> Unit = {},
    ): Boolean = withContext(io) {
        runCatching { wakeOverBluetooth(progress) }
            .onFailure { progress("BT wake skipped: ${it.message ?: it::class.simpleName}") }
            .map { true }
            .getOrElse { true /* missing device is a no-op, not a failure */ }
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
        val device = try {
            bt.discover(timeoutMs = 5_000)
        } catch (e: Exception) {
            progress("BT scan failed: ${e.message ?: e::class.simpleName}")
            null
        }
        if (device == null) {
            progress("No Polaris device found on Bluetooth — assuming gimbal already awake")
            return
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
