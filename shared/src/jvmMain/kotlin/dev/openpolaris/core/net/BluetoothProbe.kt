package dev.openpolaris.core.net

import java.util.UUID
import kotlinx.coroutines.CancellationException

/**
 * Bluetooth control plane for the gimbal.
 *
 * The official Benro app wakes the gimbal's Wi-Fi AP over Bluetooth LE by
 * **opening a GATT connection** to the gimbal — that single act is the wake
 * pulse. No characteristic write, no GATT command, no payload. The firmware
 * notices the incoming GATT connection, brings up its Wi-Fi AP in response,
 * and we then drop the BT link and connect over Wi-Fi.
 *
 * Confirmed via live capture: the wake-pulse code path opens a GATT handle
 * to the device and immediately closes it. See `polaris-re-results.md` §8.5.
 *
 * Flow this class supports:
 *   1. [knownDevices] — the BlueZ device cache, which needs no scan and is the
 *      only source that reliably knows the mount in both of its radio states
 *      (see its KDoc);
 *   2. [discover] — one-shot LE scan for a device whose name matches
 *      [namePattern] (default `polaris_` or `theta_` prefixes used by Benro);
 *   3. [wake] — connect and retain GATT through the Wi-Fi/control handoff;
 *   4. caller hands off to [WifiBridge] (or `nmcli`) to bring up the AP link.
 *
 * Why [knownDevices] comes first and [discover] is only a fallback: the mount
 * is never advertisable at the moment we most need it. Verified live on
 * `beast` / firmware 6.0.0.54 on 2026-10-08:
 *   - asleep: the BLE radio sleeps with the AP, so a scan finds nothing;
 *   - awake: it stops advertising once a client is connected, so a scan finds
 *     nothing either (an 8s scan while its AP was up and associated returned
 *     zero `polaris_` devices, while an unrelated advertiser was picked up —
 *     the adapter scans fine, the mount simply is not advertising).
 * [discover] therefore cannot be a precondition for [wake]; it can only ever
 * supply an address we do not already have.
 *
 * [startAp] is kept as a **vendor-extension escape hatch** for firmware
 * revisions that actually do require a GATT characteristic write to start
 * the AP. It is deprecated because the Benro Polaris does not need it.
 *
 * Every shell call goes through [runner], so this class is fully unit-testable.
 */
class BluetoothProbe(
    private val runner: ProcessRunner = SystemProcessRunner,
    private val namePattern: String = "polaris_",
    /**
     * Optional time in ms to wait after the connect/disconnect wake pulse.
     * The normal handoff starts Wi-Fi association immediately, so the default
     * is zero; callers that cannot retry association may request a delay.
     */
    private val wakeSettleMs: Int = 0,
    /** Keep GATT until the caller has established a durable Wi-Fi control owner. */
    private val retainGattConnection: Boolean = true,
    /**
     * GATT characteristic handle UUID that toggles the gimbal's Wi-Fi AP,
     * for firmware revisions that require a GATT write. Format:
     * `0000xxxx-0000-1000-8000-00805f9b34fb` (standard BLE base).
     * Leave blank to disable the GATT-write path entirely.
     */
    @Deprecated(
        "Benro Polaris wakes on a bare GATT connect (see wake()). The GATT-write " +
            "path is kept only as a vendor escape hatch.",
    )
    private val apToggleCharacteristic: String = "",
    /**
     * GATT characteristic UUID whose notification signals "AP is up", for
     * the deprecated GATT-write path. Leave blank to skip the wait.
     */
    @Deprecated(
        "Benro Polaris wakes on a bare GATT connect. apReadyCharacteristic is " +
            "only relevant to the deprecated startAp() path.",
    )
    private val apReadyCharacteristic: String = "",
    /**
     * Bytes to write to [apToggleCharacteristic] to start the AP, for the
     * deprecated GATT-write path.
     */
    @Deprecated("Only used by the deprecated startAp() path.")
    private val apOnBytes: ByteArray = byteArrayOf(0x01),
    /** Timeout in ms for the deprecated [startAp] notification wait. */
    @Deprecated("Only used by the deprecated startAp() path.")
    private val apReadyTimeoutMs: Int = 10_000,
) {

    data class DiscoveredDevice(
        val address: String,         // e.g. "AA:BB:CC:DD:EE:FF"
        val name: String,            // e.g. "Polaris-7B70"
        val rssi: Int? = null,       // dBm, if reported
    )

    companion object {
        /**
         * BlueZ's error for tearing down a link it had already established
         * locally. The GATT connection *was* opened, which is all the wake
         * pulse requires, so [wake] treats it as success.
         */
        const val LOCAL_ABORT = "le-connection-abort-by-local"

        /**
         * The only reliable positive signal that a GATT link was established.
         * `bluetoothctl` prints this on stdout when the connection succeeds,
         * and — unlike the exit status — it does not lie: an unreachable mount
         * exits 0 without ever printing it.
         */
        const val CONNECTED_YES = "Connected: yes"

        /**
         * Seconds to let `bluetoothctl` wait for the connection result.
         *
         * Without `--timeout`, `bluetoothctl connect` returns as soon as the
         * request is *issued*, before the link resolves, so `Connected: yes`
         * would never be observed and success could not be detected at all.
         * Bounded well inside the bridge's link budget; a mount that is awake
         * and in range answers in about a second.
         */
        const val CONNECT_TIMEOUT_SECONDS = 15
    }

    /**
     * Runs a single, time-bounded LE scan, returns the first device whose
     * advertised name matches [namePattern] (case-insensitive contains).
     * Does NOT enable continuous discovery, so this is safe to call without
     * overloading the host.
     */
    fun discover(timeoutMs: Int = 8000): DiscoveredDevice? {
        val out = runner.run(listOf("bluetoothctl", "--timeout", (timeoutMs / 1000).coerceAtLeast(1).toString(), "scan", "on"))
        return parseDevices(out).firstOrNull { it.name.lowercase().contains(namePattern.lowercase()) }
    }

    /**
     * Reads the BlueZ device cache (`bluetoothctl devices`) for entries whose
     * name matches [namePattern]. Unlike [discover] this issues no scan, so it
     * is cheap, it cannot disturb the adapter, and — the reason it exists — it
     * still returns the mount when the mount is not advertisable, which is
     * every state we actually wake it from.
     *
     * Returns every match, most-recently-listed first as BlueZ prints them.
     * An empty list means BlueZ has never seen the device, which is the only
     * case where a scan is worth attempting.
     */
    fun knownDevices(): List<DiscoveredDevice> {
        val out = try {
            runner.run(listOf("bluetoothctl", "devices"))
        } catch (e: CancellationException) {
            // Not an operational failure: the caller's scope was cancelled, and
            // swallowing it here would turn a cancel into "no devices known"
            // and let the wake continue. See #52.
            throw e
        } catch (e: Exception) {
            // No adapter, bluetoothctl missing, BlueZ down: all mean "nothing
            // cached", which is a normal answer and has safe fallbacks.
            return emptyList()
        }
        return parseDevices(out).filter { it.name.lowercase().contains(namePattern.lowercase()) }
    }

    /**
     * Parses `Device <MAC> <name>` lines out of `bluetoothctl devices` or scan
     * output. Shared by [discover] and [knownDevices] so both agree on what a
     * device line looks like.
     */
    private fun parseDevices(output: String): List<DiscoveredDevice> {
        val addressRegex = Regex("""Device\s+([0-9A-Fa-f:]{17})\s+(.+)""")
        return addressRegex.findAll(output).map { match ->
            DiscoveredDevice(match.groupValues[1], match.groupValues[2].trim())
        }.toList()
    }

    /**
     * True iff the deprecated GATT-write wake path is configured. The
     * current Benro firmware does not need it; [wake] is always usable.
     */
    @Deprecated("Benro Polaris does not need the GATT-write path; use wake().")
    val canStartAp: Boolean
        get() = apToggleCharacteristic.isNotBlank()

    /**
     * Wakes the gimbal's Wi-Fi AP by issuing a bare GATT connect to the
     * peripheral. This is the mechanism the official Benro app uses; see
     * `polaris-re-results.md` §8.5. The sequence is:
     *
     *   1. `bluetoothctl connect` — open GATT immediately (this IS the wake pulse)
     *   2. on failure, try pair/trust as best-effort cache improvements
     *   3. retry `connect`; pair/trust failures never suppress this attempt
     *   4. retain GATT by default while the caller starts Wi-Fi association
     *   5. optionally wait [wakeSettleMs] for callers that need a post-pulse delay
     *
     * After this returns, the gimbal's AP should be visible to NetworkManager
     * (or any wifi scanner). The caller should then bring up the
     * `polaris_<id>` connection via [WifiBridge.up].
     *
     * Throws [BridgeException] if any bluetoothctl call fails.
     */
    fun wake(device: DiscoveredDevice) {
        // A pulse counts only if the link actually opened. Measured on
        // 2026-10-09: `bluetoothctl connect` on an unreachable mount prints
        // "Attempting to connect to …" and then exits **0** without ever
        // reaching `Connected: yes`, so the exit status cannot be the signal —
        // treating it as success made wake report "woke <device>" for a pulse
        // that was never delivered. The textual marker is authoritative.
        var pulseIssued = attemptConnect(device)
        if (!pulseIssued) {
            runCatching { runner.run(listOf("bluetoothctl", "pair", device.address)) }
            runCatching { runner.run(listOf("bluetoothctl", "trust", device.address)) }
            pulseIssued = attemptConnect(device)
        }
        if (!pulseIssued) {
            throw BridgeException(
                "bluetoothctl connect ${device.address}",
                1,
                "no GATT connection could be opened to ${device.address}; " +
                    "the mount is out of range, powered down, or in deep sleep",
            )
        }
        if (!retainGattConnection) {
            runCatching { runner.run(listOf("bluetoothctl", "disconnect", device.address)) }
        }
        if (wakeSettleMs > 0) {
            Thread.sleep(wakeSettleMs.toLong())
        }
    }

    /**
     * Issues one GATT connect and reports whether the link was actually
     * established, which is the only thing that wakes the AP.
     *
     * Success is read from the command's output rather than its exit status,
     * for two reasons measured on this host:
     *  - an unreachable mount exits 0 having only printed "Attempting to
     *    connect", and
     *  - the normal success path then aborts locally (`le-connection-abort-by-
     *    local`) and exits *non-zero*, even though the link did open and does
     *    wake the AP.
     * So `Connected: yes` in stdout, or the abort marker in stderr, are the
     * two forms of a delivered pulse.
     */
    private fun attemptConnect(device: DiscoveredDevice): Boolean {
        // --timeout makes bluetoothctl wait for the connection result instead
        // of returning immediately; without it the process can exit before the
        // link is even attempted.
        val connect = listOf("bluetoothctl", "--timeout", CONNECT_TIMEOUT_SECONDS.toString(), "connect", device.address)
        return try {
            runner.run(connect).contains(CONNECTED_YES)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BridgeException) {
            e.isLocalAbort() || (e.message?.contains(CONNECTED_YES) == true)
        }
    }

    /** True iff [this] is BlueZ aborting a link it had already established. */
    private fun BridgeException.isLocalAbort(): Boolean =
        message?.contains(LOCAL_ABORT) == true

    /** Release the wake link after Wi-Fi and its persistent control owner exist. */
    fun release(device: DiscoveredDevice) {
        runCatching { runner.run(listOf("bluetoothctl", "disconnect", device.address)) }
    }

    /**
     * Pairs, connects, and starts the gimbal's AP via a GATT characteristic
     * write. Kept as a vendor-extension escape hatch for firmware revisions
     * that need more than a bare GATT connect. Benro Polaris does not.
     *
     * Throws [BridgeException] on any failure or if the GATT UUIDs are blank.
     */
    @Deprecated(
        "Benro Polaris wakes on a bare GATT connect. Use wake() instead. " +
            "This entry point is retained for vendor-specific firmware that " +
            "requires an explicit AP-toggle characteristic write.",
    )
    fun startAp(device: DiscoveredDevice) {
        require(apToggleCharacteristic.isNotBlank()) {
            "BluetoothProbe.startAp: apToggleCharacteristic UUID is blank. " +
            "Set it from the official Benro app's GATT profile before using the BT control plane."
        }
        runner.run(listOf("bluetoothctl", "pair", device.address))
        runner.run(listOf("bluetoothctl", "trust", device.address))
        runner.run(listOf("bluetoothctl", "connect", device.address))
        // gatttool is the simplest CLI for GATT ops; if absent, fall back to bluetoothctl.
        val payload = apOnBytes.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
        try {
            runner.run(
                listOf(
                    "gatttool", "-b", device.address,
                    "--char-write-req", "-a", apHandleFor(device.address, apToggleCharacteristic),
                    "-n", payload,
                )
            )
        } catch (e: BridgeException) {
            // No gatttool. Modern BlueZ removed it; use bluetoothctl under menu gatt.
            runner.run(listOf("bluetoothctl", "menu", "gatt"))
            runner.run(listOf("bluetoothctl", "select-attribute", apToggleCharacteristic))
            runner.run(listOf("bluetoothctl", "write", payload.replace(" ", "")))
            runner.run(listOf("bluetoothctl", "back"))
        }
        if (apReadyCharacteristic.isNotBlank()) {
            waitForApReady(device)
        }
    }

    private fun apHandleFor(address: String, charUuid: String): String {
        // Run gatttool --characteristics to map UUID -> handle, then return the
        // handle. We can't pipe into gatttool here without a PTY, so we shell
        // out a second time and parse the output.
        val out = runner.run(listOf("gatttool", "-b", address, "--characteristics"))
        val target = charUuid.lowercase()
        val handleRegex = Regex("""char\s+handle\s+(0x[0-9a-fA-F]+).*?uuid:\s+([0-9a-fA-F-]+)""")
        for (m in handleRegex.findAll(out)) {
            val handle = m.groupValues[1]
            val uuid = m.groupValues[2].lowercase()
            if (uuid == target) return handle
        }
        // Fall back: assume caller wants the raw UUID (bluetoothctl path).
        return charUuid
    }

    private fun waitForApReady(device: DiscoveredDevice) {
        // Subscribe via gatttool --listen in a time-bounded call. Any bytes
        // returned count as "ready" — the characteristic is vendor-defined.
        val listen = ProcessBuilder(
            "timeout", (apReadyTimeoutMs / 1000).coerceAtLeast(1).toString(),
            "gatttool", "-b", device.address, "--char-read", "-a", apHandleFor(device.address, apReadyCharacteristic),
        ).redirectErrorStream(true)
        val proc = listen.start()
        proc.inputStream.bufferedReader().readText()
        proc.waitFor()
    }

    /**
     * Best-effort cleanup: tell the gimbal to power its AP off, then drop
     * the GATT connection. Both calls are non-fatal.
     *
     * Retained alongside [startAp] for the deprecated GATT-write path. The
     * Benro Polaris firmware powers the AP down on its own when it sees no
     * Wi-Fi clients, so the modern usage is to simply close the network
     * connection — no BT teardown required.
     */
    @Deprecated("Use the normal Wi-Fi disconnect; the gimbal powers its AP down on its own.")
    fun stopAp(device: DiscoveredDevice) {
        if (apToggleCharacteristic.isNotBlank()) {
            val off = byteArrayOf(0x00)
            val payload = off.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
            runCatching {
                runner.run(
                    listOf(
                        "gatttool", "-b", device.address,
                        "--char-write-req", "-a", apHandleFor(device.address, apToggleCharacteristic),
                        "-n", payload,
                    )
                )
            }
        }
        runCatching { runner.run(listOf("bluetoothctl", "disconnect", device.address)) }
    }

    /** Helper for the CLI: convert a 16-bit short UUID to a full 128-bit one. */
    @Suppress("unused")
    fun fullUuid(shortHex: String): UUID {
        val s = shortHex.removePrefix("0x").padStart(4, '0').lowercase()
        return UUID.fromString("0000$s-0000-1000-8000-00805f9b34fb")
    }
}
