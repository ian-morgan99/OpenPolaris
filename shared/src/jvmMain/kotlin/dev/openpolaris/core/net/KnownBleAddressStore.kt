package dev.openpolaris.core.net

import java.io.File

/**
 * Remembers the Bluetooth address of the mount we last woke successfully.
 *
 * Why this exists: the mount is not advertisable at the moment we need to wake
 * it. Asleep, its BLE radio is powered down with the AP; awake, it stops
 * advertising once a client is connected. Both states were measured live on
 * 2026-10-08 — an 8 s `bluetoothctl scan on` returned no `polaris_` device
 * while its AP was up and associated (an unrelated advertiser was seen, so the
 * adapter scans fine). A scan-first wake therefore cannot work in general, and
 * the address has to come from somewhere that does not require advertising.
 *
 * The BlueZ cache usually has it, but the cache can be removed (`bluetoothctl
 * remove`, a BlueZ reset, a fresh adapter), and the previous design made that
 * fatal. This store is the app's own durable copy, written after a wake that
 * demonstrably worked.
 *
 * Deliberately a plain file rather than a new settings framework: it is one
 * line, and it must be readable by the CLI and the desktop app alike without
 * pulling in a preferences dependency.
 *
 * The interface is not a `fun interface` because it has two members; tests
 * implement it directly or use [inMemory].
 */
interface KnownBleAddressStore {
    /** Last known-good address, or null when nothing has been recorded yet. */
    fun read(): String?

    /**
     * Records [address] as the last known-good one. Implementations must not
     * throw: a failed write costs a fast path on the next wake, nothing more.
     */
    fun write(address: String)

    companion object {
        /**
         * Production store: `~/.config/openpolaris/known-ble-address`, matching
         * the `~/.config/openpolaris/` convention already used for
         * `desktop.properties`. Falls back to an in-memory no-op store when
         * `user.home` is unavailable, so a headless or sandboxed launch degrades
         * instead of failing.
         */
        fun default(): KnownBleAddressStore {
            val home = System.getProperty("user.home") ?: return inMemory(null)
            val file = File(home, ".config/openpolaris/known-ble-address")
            return FileBased(file)
        }

        /** Test/offline store that keeps the value only for this process. */
        fun inMemory(initial: String? = null): KnownBleAddressStore = object : KnownBleAddressStore {
            private var current: String? = initial
            override fun read(): String? = current
            override fun write(address: String) { current = address }
        }
    }
}

internal class FileBased(private val file: File) : KnownBleAddressStore {
    override fun read(): String? = runCatching {
        if (!file.isFile) return null
        file.readText().trim().takeIf { it.isNotBlank() }
    }.getOrNull()

    override fun write(address: String) {
        runCatching {
            if (read() == address) return
            file.parentFile?.mkdirs()
            file.writeText(address + "\n")
        }
    }
}
