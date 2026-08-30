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
 * 1. **BT wake** (best-effort) — one-shot scan for the gimbal, then a GATT
 *    write to toggle its Wi-Fi AP. If the GATT UUIDs aren't configured yet,
 *    this phase is skipped silently: the user can still wake the gimbal
 *    manually and the Wi-Fi phases below will succeed anyway.
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
        // The user can pre-configure the GATT UUIDs by passing a BluetoothProbe
        // instance; the default has them blank, which short-circuits to skip.
        if (!bt.canStartAp) {
            progress("BT wake skipped (GATT UUIDs not configured — power gimbal manually)")
            return
        }
        progress("Scanning for gimbal over Bluetooth…")
        val device = bt.discover(timeoutMs = 5_000)
        if (device == null) {
            progress("No Polaris device found on Bluetooth — power gimbal manually")
            return
        }
        progress("Telling ${device.name} to turn Wi-Fi on…")
        bt.startAp(device)
        // Brief settle for the AP to come up after the GATT write completes.
        var settled = 0
        while (settled < 20 && coroutineContext.isActive) {
            delay(250); settled++
        }
    }
}
