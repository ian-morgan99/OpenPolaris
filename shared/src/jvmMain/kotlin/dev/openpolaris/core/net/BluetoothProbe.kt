package dev.openpolaris.core.net

import java.util.UUID

/**
 * Bluetooth control plane for the gimbal.
 *
 * The official Benro app wakes the gimbal's Wi-Fi AP over Bluetooth LE
 * (GATT write), then drops the BT link and connects to the AP. We do the
 * same:
 *
 *   1. one-shot scan for a device whose name matches [namePattern];
 *   2. connect, discover primary GATT services;
 *   3. write [apOnBytes] to the characteristic at [apToggleCharacteristic];
 *   4. wait for the [apReadyCharacteristic] to indicate "AP up";
 *   5. caller hands off to [WifiBridge].
 *
 * Because the exact service / characteristic UUIDs are vendor-specific and
 * aren't published in the public Polaris docs, the GATT UUIDs are parameters
 * to the class, not constants. The user discovers them once (via a packet
 * capture of the official app or `bluetoothctl` while it controls the
 * gimbal) and passes them in. Until then, [startAp] will throw a
 * [BridgeException] with a clear "UUID not yet configured" message.
 *
 * Every shell call goes through [runner], so this class is fully unit-testable.
 */
class BluetoothProbe(
    private val runner: ProcessRunner = SystemProcessRunner,
    private val namePattern: String = "Polaris",
    /**
     * GATT characteristic handle UUID that toggles the gimbal's Wi-Fi AP.
     * Format: `0000xxxx-0000-1000-8000-00805f9b34fb` (standard BLE base).
     * Leave blank to disable AP toggling (discover-only mode).
     */
    private val apToggleCharacteristic: String = "",
    /**
     * GATT characteristic UUID whose notification signals "AP is up".
     * Leave blank to skip the wait and return immediately after the write.
     */
    private val apReadyCharacteristic: String = "",
    /**
     * Bytes to write to [apToggleCharacteristic] to start the AP.
     * Default is a single 0x01 byte; override with the vendor value once known.
     */
    private val apOnBytes: ByteArray = byteArrayOf(0x01),
    /** Timeout in ms for the [startAp] notification wait. */
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
    /**
     * True iff the GATT UUIDs needed to toggle the AP are configured. When
     * false, [startAp] will refuse to run; callers should treat the BT
     * control plane as unavailable and fall back to manual wake.
     */
    val canStartAp: Boolean
        get() = apToggleCharacteristic.isNotBlank()

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
     * Pairs, connects, and starts the gimbal's AP.
     * Throws [BridgeException] on any failure or if the GATT UUIDs are blank.
     */
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
     */
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
