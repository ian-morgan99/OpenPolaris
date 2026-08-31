package dev.openpolaris.core.net

import java.util.UUID

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
 *   1. [discover] — one-shot LE scan for a device whose name matches
 *      [namePattern] (default `polaris_` or `theta_` prefixes used by Benro);
 *   2. [wake] — pair + trust + connect + settle + disconnect, the wake pulse;
 *   3. caller hands off to [WifiBridge] (or `nmcli`) to bring up the AP link.
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
     * Time in ms to wait between the BT `connect` and `disconnect` so the
     * gimbal's firmware has time to bring its AP up. The Benro firmware
     * appears to need ~1s; 2s is a safe default.
     */
    private val wakeSettleMs: Int = 2_000,
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

    /**
     * Runs a single, time-bounded LE scan, returns the first device whose
     * advertised name matches [namePattern] (case-insensitive contains).
     * Does NOT enable continuous discovery, so this is safe to call without
     * overloading the host.
     */
    fun discover(timeoutMs: Int = 8000): DiscoveredDevice? {
        val out = runner.run(listOf("bluetoothctl", "--timeout", (timeoutMs / 1000).coerceAtLeast(1).toString(), "scan", "on"))
        val addressRegex = Regex("""Device\s+([0-9A-Fa-f:]{17})\s+(.+)""")
        val needle = namePattern.lowercase()
        for (match in addressRegex.findAll(out)) {
            val addr = match.groupValues[1]
            val name = match.groupValues[2].trim()
            if (name.lowercase().contains(needle)) {
                return DiscoveredDevice(addr, name)
            }
        }
        return null
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
     *   1. `bluetoothctl pair`   — bond so subsequent connects don't prompt
     *   2. `bluetoothctl trust`  — auto-accept future connections
     *   3. `bluetoothctl connect` — open the GATT link (this IS the wake pulse)
     *   4. wait [wakeSettleMs]   — let the firmware bring the AP up
     *   5. `bluetoothctl disconnect` — drop the BT link; we don't need it
     *
     * After this returns, the gimbal's AP should be visible to NetworkManager
     * (or any wifi scanner). The caller should then bring up the
     * `polaris_<id>` connection via [WifiBridge.up].
     *
     * Throws [BridgeException] if any bluetoothctl call fails.
     */
    fun wake(device: DiscoveredDevice) {
        runner.run(listOf("bluetoothctl", "pair", device.address))
        runner.run(listOf("bluetoothctl", "trust", device.address))
        runner.run(listOf("bluetoothctl", "connect", device.address))
        if (wakeSettleMs > 0) {
            Thread.sleep(wakeSettleMs.toLong())
        }
        // Disconnect is best-effort: if it fails, the link will drop on its
        // own once the device goes idle, and the WiFi AP is already up.
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
