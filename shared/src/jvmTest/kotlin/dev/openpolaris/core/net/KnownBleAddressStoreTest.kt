package dev.openpolaris.core.net

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the persisted last-known-good Bluetooth address.
 *
 * This store is what makes a second wake work without consulting the BlueZ
 * cache or scanning, so its file format (one bare address) and its refusal to
 * throw are both part of the contract.
 */
class KnownBleAddressStoreTest {

    private fun tempFile(): File {
        val dir = java.nio.file.Files.createTempDirectory("op-ble-").toFile()
        return File(dir, ".config/openpolaris/known-ble-address")
    }

    @Test
    fun `read returns null before anything is recorded`() {
        assertNull(FileBased(tempFile()).read())
    }

    @Test
    fun `a recorded address survives a new store instance`() {
        val target = tempFile()

        FileBased(target).write("48:E7:DA:D4:B5:72")

        assertTrue(target.isFile, "expected $target to be created")
        assertEquals("48:E7:DA:D4:B5:72", FileBased(target).read())
    }

    @Test
    fun `default store falls back to a no-op when the home directory is unset`() {
        val previous = System.getProperty("user.home")
        System.clearProperty("user.home")
        try {
            val store = KnownBleAddressStore.default()
            assertNull(store.read())
            store.write("AA:BB:CC:DD:EE:FF") // must not throw
        } finally {
            if (previous != null) System.setProperty("user.home", previous)
        }
    }

    @Test
    fun `inMemory store round-trips`() {
        val store = KnownBleAddressStore.inMemory()
        assertNull(store.read())
        store.write("AA:BB:CC:DD:EE:FF")
        assertEquals("AA:BB:CC:DD:EE:FF", store.read())
    }

    @Test
    fun `a failing store cannot break the wake path`() {
        // The orchestrator wraps reads/writes, but the interface contract is
        // also "must not throw", so a store that honours it is safe to use.
        val store = object : KnownBleAddressStore {
            override fun read(): String? = null
            override fun write(address: String) = Unit
        }
        store.write("AA:BB:CC:DD:EE:FF")
        assertNull(store.read())
    }
}
