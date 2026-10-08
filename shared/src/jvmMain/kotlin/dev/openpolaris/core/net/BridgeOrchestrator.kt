package dev.openpolaris.core.net

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Glues the three bridge phases together so the desktop UI (or any other
 * caller) can run the whole thing with one call.
 *
 * 1. **BT wake** (best-effort) — a bare GATT connect to the gimbal, using an
 *    address from configuration, the persisted last-known-good store, or the
 *    BlueZ cache, in that order; a scan is attempted only if none of those
 *    know the mount. The connect itself is the wake pulse; the Benro firmware
 *    notices and brings up its Wi-Fi AP. See `BluetoothProbe.wake()` and
 *    `polaris-re-results.md` §8.5.
 *    Scanning is a last resort rather than the entry point because the mount is
 *    not advertisable in either state we wake it from — see
 *    `BluetoothProbe`'s class KDoc for the measurements.
 * 2. **NM up** — `nmcli connection up <profile> ifname <ifname>` on a saved
 *    profile, retried while the just-woken AP comes on the air. The profile is
 *    *required* because Wi-Fi scans are forbidden on this laptop (see
 *    `NoScanGuardTest`).
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
    private val serviceProbe: PolarisServiceIdentityProbe = SocketPolarisServiceIdentityProbe,
    private val knownBleAddress: String? = System.getenv("OPENPOLARIS_BLE_ADDRESS")?.takeIf { it.isNotBlank() },
    private val addressStore: KnownBleAddressStore = KnownBleAddressStore.default(),
    /**
     * How long to wait for the mount's AP after we issued a wake pulse.
     *
     * Measured on 2026-10-08: the AP became visible ~31 s after the GATT
     * connect, and the repo's own hardware-verified `wake-and-probe.sh` polls
     * for 60 s by default (and suggests 300 s after a hard reboot). The
     * previous fixed 15 s link wait therefore declared "Link never came up — is
     * the gimbal powered on?" while the mount was still waking, so even a
     * correctly issued pulse looked like a failure.
     */
    private val wakeLinkTimeoutMs: Int = 90_000,
    /** Link-wait budget when no wake was needed and the AP should already exist. */
    private val warmLinkTimeoutMs: Int = 15_000,
    /**
     * Upper bound on a single activation attempt inside [bringUpAfterWake].
     * Kept short so a failed `nmcli connection up` (SSID not on the air yet)
     * is retried promptly instead of burning the whole budget on one poll.
     */
    private val activationSliceMs: Long = 5_000,
    /**
     * Emit one progress line per this many activation attempts. With the default
     * 5 s slice this reports roughly every 30 s instead of every attempt.
     */
    private val progressEveryNAttempts: Int = 6,
) {

    /**
     * The BLE address of the last device we woke via [wakeOverBluetooth].
     *
     * Since the keep-alive change, [BluetoothProbe.wake] retains the GATT link
     * by default so the gimbal's AP stays up while a Wi-Fi control owner is
     * attached. That means nothing drops the link on its own — the caller must
     * release it once the Wi-Fi control owner goes away. We remember the address
     * here so [tearDown] can drop the retained GATT link (best-effort) after the
     * Wi-Fi profile is brought down. The desktop holds a single orchestrator
     * instance across `bridgeToMount` → `tearDown`, so this instance state is
     * the right seam.
     */
    private var lastWokenAddress: String? = null

    /**
     * Run the full bring-up. [progress] is invoked from the IO dispatcher
     * with short, human-readable status strings suitable for direct display.
     */
    suspend fun bridgeToMount(
        profile: String,
        ifname: String,
        progress: suspend (String) -> Unit = {},
    ): Boolean = withContext(io) {
        // Best-effort BT wake. A CancellationException must propagate (the
        // owning scope was cancelled), so it is rethrown rather than folded
        // into the "try the saved Wi-Fi profile" path like an operational
        // failure. See #52.
        //
        // `woke` is true once a GATT pulse has actually been issued. It selects
        // the link budget: after a pulse the AP needs tens of seconds to appear,
        // so applying the warm-path 15 s would report a healthy wake as a dead
        // mount.
        var woke = false
        try {
            woke = wakeOverBluetooth(progress)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            progress(
                "BT wake did not confirm: ${e.message ?: e::class.simpleName}; " +
                    "waiting for the AP before trying the saved Wi-Fi profile"
            )
            // Live 2026-09-10: BlueZ returned
            // `le-connection-abort-by-local`, but that short-lived attempt
            // still woke the Polaris AP. Treat the exit status as
            // inconclusive and allow firmware time to advertise Wi-Fi. The
            // link, route and ports 22/9090 checks below remain authoritative.
            //
            // 2026-10-08: an *unconfirmed* wake is still a wake we attempted,
            // so it gets the long budget too. The old 5 s pause followed by a
            // fixed 15 s link wait was the second half of the "cannot wake"
            // report: the pulse was issued, the AP arrived at ~31 s, and the
            // bridge had already given up. No extra sleep is needed here —
            // bringUpAfterWake retries activation on its own cadence.
            woke = true
        }

        val linkUp = if (woke) {
            bringUpAfterWake(profile, ifname, progress)
        } else {
            // Warm path: nothing was woken, so the AP should already exist and
            // a single activation attempt with the short budget is right.
            try {
                wifi.connectByProfile(profile, ifname)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                progress("Wi-Fi activation failed: ${e.message ?: e::class.simpleName}")
                return@withContext false
            }
            progress("Waiting for link on $ifname…")
            try {
                wifi.awaitLinkUp(ifname, timeoutMs = warmLinkTimeoutMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                progress("Wi-Fi link check failed: ${e.message ?: e::class.simpleName}")
                return@withContext false
            }
        }
        if (!linkUp) {
            progress("Link never came up on $ifname — is the gimbal powered on?")
            return@withContext false
        }

        progress("Installing policy route for ${wifi.gimbalCidrForDebug} → $ifname")
        try {
            wifi.installPolicyRoute(ifname)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            progress("Policy route failed: ${e.message ?: e::class.simpleName}")
            return@withContext false
        }

        progress("Verifying Polaris AP and route identity…")
        val identity = wifi.verifyPolarisIdentity(ifname).getOrElse { error ->
            progress("Polaris identity check failed: ${error.message ?: error::class.simpleName}")
            runCatching { wifi.removePolicyRoute(ifname) }
            return@withContext false
        }
        progress("Verifying ports 22 and 9090…")
        val serviceIdentity = serviceProbe.verify("192.168.0.1", 9090).getOrElse { error ->
            progress("Polaris service identity failed: ${error.message ?: error::class.simpleName}")
            runCatching { wifi.removePolicyRoute(ifname) }
            return@withContext false
        }

        progress("Mount Wi-Fi ready on $ifname (${identity.ssid}, ${identity.bssid}, $serviceIdentity)")
        true
    }

    /**
     * BT-wake only — fires the GATT-connect pulse and does NOT bring up the
     * Wi-Fi profile or install a policy route. Used by the desktop "Wake"
     * button when the user wants to pulse the gimbal awake on a cold start
     * before deciding whether to bridge.
     *
     * Returns true only when a pulse was actually issued to some address. Not
     * knowing any address is reported as false with an explanatory progress
     * message rather than an exception, because the caller's next move (try the
     * saved profile) is still reasonable when the mount is already awake.
     */
    suspend fun wakeOnly(
        progress: suspend (String) -> Unit = {},
    ): Boolean = withContext(io) {
        try {
            // wakeOverBluetooth reports whether a pulse was actually issued; it
            // no longer throws when no address is known, so the result must be
            // propagated rather than hardcoded to true.
            wakeOverBluetooth(progress)
        } catch (e: CancellationException) {
            // Propagate cancellation so the caller's scope is not left to
            // continue UI/state work after a cancelled wake. See #52.
            throw e
        } catch (e: Exception) {
            progress("BT wake failed: ${e.message ?: e::class.simpleName}")
            false
        }
    }

    /**
     * Tear-down. The reverse of [bridgeToMount]: policy route first, then NM
     * down, then release the retained BLE GATT wake link. Safe to call even if
     * the link isn't up; every step is wrapped in `runCatching`.
     *
     * The final step drops the GATT link that [wakeOverBluetooth] retained (the
     * keep-alive change). It only runs when we actually woke a device this
     * session, and it is best-effort — a missing/already-gone device must not
     * fail the teardown. Releasing after the Wi-Fi profile is down matches the
     * "release only once a durable control owner exists" contract: by the time
     * we get here the Wi-Fi session is being torn down, so the keep-alive link
     * can go with it.
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
        val woken = lastWokenAddress
        if (woken != null) {
            progress("Releasing retained BLE wake link for $woken")
            runCatching { bt.release(BluetoothProbe.DiscoveredDevice(woken, "known Polaris")) }
            lastWokenAddress = null
        }
        progress("Mount Wi-Fi torn down")
    }

    /**
     * Activate the saved profile while the AP is still coming up, and wait for
     * the link, within [wakeLinkTimeoutMs].
     *
     * A single `nmcli connection up` issued right after a wake pulse fails, and
     * has always failed: NetworkManager cannot activate a profile for an SSID
     * that is not on the air yet, and the mount needs tens of seconds after the
     * pulse. So activation is retried on a short cadence until the budget runs
     * out. Only `nmcli connection up` and the existing link poll are used — no
     * Wi-Fi scan primitive is introduced, per NoScanGuardTest.
     */
    private suspend fun bringUpAfterWake(
        profile: String,
        ifname: String,
        progress: suspend (String) -> Unit,
    ): Boolean {
        val deadline = System.currentTimeMillis() + wakeLinkTimeoutMs
        var lastActivationError: String? = null
        var attempt = 0
        while (System.currentTimeMillis() < deadline && coroutineContext.isActive) {
            attempt++
            // The UI shows every progress line, so report the first attempt and
            // then roughly every 30 s rather than once per slice — otherwise a
            // single slow wake writes ~36 lines of near-identical text.
            if (attempt == 1 || attempt % progressEveryNAttempts == 0) {
                progress(
                    if (attempt == 1) "Bringing $profile up on $ifname…"
                    else "Still waiting for the mount's AP; retrying $profile on $ifname…"
                )
            }
            try {
                wifi.connectByProfile(profile, ifname)
                lastActivationError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastActivationError = e.message ?: e::class.simpleName
            }
            val remaining = (deadline - System.currentTimeMillis()).coerceAtMost(activationSliceMs)
            if (remaining <= 0) break
            try {
                if (wifi.awaitLinkUp(ifname, timeoutMs = remaining.toInt())) return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                progress("Wi-Fi link check failed: ${e.message ?: e::class.simpleName}")
                return false
            }
        }
        lastActivationError?.let { progress("Wi-Fi activation failed: $it") }
        return false
    }

    private suspend fun wakeOverBluetooth(progress: suspend (String) -> Unit): Boolean {
        // The wake path is unconditional on the Benro Polaris: a bare GATT
        // connect pulses the firmware's Wi-Fi AP. If the gimbal is already
        // awake, the connect is a harmless no-op.
        //
        // Address sources are tried in order of reliability, and crucially a
        // failure to find an address never suppresses the pulse below: the
        // mount is not advertisable when asleep (BLE radio down with the AP)
        // nor once awake and connected, so a scan is the *least* reliable way
        // to obtain it and must be last.
        for (address in candidateAddresses(progress)) {
            progress("Waking Polaris at $address over Bluetooth…")
            try {
                bt.wake(BluetoothProbe.DiscoveredDevice(address, "known Polaris"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                progress("Wake attempt for $address failed: ${e.message ?: e::class.simpleName}")
                continue
            }
            lastWokenAddress = address
            rememberAddress(address)
            progress("GATT wake pulse issued to $address")
            return true
        }
        progress(
            "No Polaris Bluetooth address available; trying the saved Wi-Fi profile. " +
                "If the mount is asleep it will not wake itself — pair it once, or set " +
                "OPENPOLARIS_BLE_ADDRESS."
        )
        return false
    }

    /**
     * Addresses to attempt, best first: explicit configuration, then the store
     * of previously successful wakes, then the BlueZ cache, and only then a
     * scan. Duplicates are collapsed so a cached address is not tried twice.
     */
    private suspend fun candidateAddresses(progress: suspend (String) -> Unit): List<String> {
        // Sources are consulted lazily and in order, so a configured or
        // previously-successful address costs no subprocess at all.
        val ordered = LinkedHashSet<String>()
        knownBleAddress?.let { ordered += it }
        if (ordered.isEmpty()) {
            optionalRead("address store", progress) { addressStore.read() }
                ?.takeIf { it.isNotBlank() }
                ?.let { ordered += it }
        }
        if (ordered.isEmpty()) {
            optionalRead("Bluetooth cache", progress) { bt.knownDevices() }?.forEach { ordered += it.address }
        }
        if (ordered.isNotEmpty()) return ordered.toList()

        // Nothing we know of — this is the only case where scanning is worth
        // its cost and its latency.
        progress("No stored Polaris Bluetooth address; scanning…")
        val scanned = optionalRead("Bluetooth scan", progress) { bt.discover(timeoutMs = 5_000) }
        return listOfNotNull(scanned?.address)
    }

    /**
     * Runs an address source that is allowed to fail, reporting the failure
     * without aborting the wake. Cancellation is never swallowed — see #52, and
     * the `runCatching`-eats-CancellationException trap this guards against.
     */
    private suspend inline fun <T> optionalRead(
        source: String,
        progress: suspend (String) -> Unit,
        block: () -> T,
    ): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        progress("Could not read $source: ${e.message ?: e::class.simpleName}")
        null
    }

    /** Persist a successful wake address so the next wake skips discovery. */
    private fun rememberAddress(address: String) {
        runCatching { addressStore.write(address) }
    }
}
