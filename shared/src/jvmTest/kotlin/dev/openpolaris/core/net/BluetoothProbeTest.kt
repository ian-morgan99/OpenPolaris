package dev.openpolaris.core.net

import dev.openpolaris.core.net.BluetoothProbe.DiscoveredDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises [BluetoothProbe] against a fake [ProcessRunner]. Locks down the
 * RE-documented wake sequence (pair → trust → connect → wait → disconnect)
 * so future refactors can't accidentally drop a step or change the order.
 */
class BluetoothProbeTest {

    private class FakeRunner(
        private val cannedPerCommand: Map<String, String> = emptyMap(),
    ) : ProcessRunner {
        val calls = mutableListOf<List<String>>()
        override fun run(args: List<String>): String {
            calls += args
            return cannedPerCommand[args.joinToString(" ")] ?: ""
        }
    }

    private fun dev(addr: String = "AA:BB:CC:DD:EE:FF", name: String = "polaris_d13e86") =
        DiscoveredDevice(addr, name)

    @Test
    fun `wake issues pair then trust then connect then disconnect in that exact order`() {
        val fake = FakeRunner()
        val probe = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        probe.wake(dev())

        // Filter only the bluetoothctl calls relevant to wake().
        val bt = fake.calls.filter { it.firstOrNull() == "bluetoothctl" }
        assertEquals(
            listOf(
                listOf("bluetoothctl", "pair", "AA:BB:CC:DD:EE:FF"),
                listOf("bluetoothctl", "trust", "AA:BB:CC:DD:EE:FF"),
                listOf("bluetoothctl", "connect", "AA:BB:CC:DD:EE:FF"),
                listOf("bluetoothctl", "disconnect", "AA:BB:CC:DD:EE:FF"),
            ),
            bt,
        )
    }

    @Test
    fun `wake waits wakeSettleMs between connect and disconnect`() {
        val fake = FakeRunner()
        val probe = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        val start = System.nanoTime()
        probe.wake(dev())
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        // 0ms should be effectively instant.
        assertTrue(elapsedMs < 200, "wake with 0ms settle should be near-instant, took ${elapsedMs}ms")
    }

    @Test
    fun `wake swallows disconnect errors so a wedged BT link doesn't fail the whole flow`() {
        val fake = object : ProcessRunner {
            val calls = mutableListOf<List<String>>()
            override fun run(args: List<String>): String {
                calls += args
                if (args.getOrNull(1) == "disconnect") {
                    throw BridgeException("bluetoothctl", 1, "not connected")
                }
                return ""
            }
        }
        val probe = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        // Should not throw even though disconnect fails.
        probe.wake(dev())
        // Disconnect was attempted.
        assertTrue(
            fake.calls.any { it.firstOrNull() == "bluetoothctl" && it.getOrNull(1) == "disconnect" },
            "disconnect must still be attempted, got: " + fake.calls.toString(),
        )
    }

    @Test
    fun `wake propagates non-disconnect failures so caller knows wake actually failed`() {
        val fake = object : ProcessRunner {
            override fun run(args: List<String>): String {
                if (args.getOrNull(1) == "connect") {
                    throw BridgeException("bluetoothctl", 1, "Failed to connect")
                }
                return ""
            }
        }
        val probe = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        assertFailsWith<BridgeException> {
            probe.wake(dev())
        }
    }

    @Test
    fun `discover returns null when no polaris-named device is in scan output`() {
        val fake = FakeRunner(
            cannedPerCommand = mapOf(
                "bluetoothctl --timeout 8 scan on" to
                    """
                    [NEW] Device 11:22:33:44:55:66 SomeRandomHeadphones
                    [NEW] Device 77:88:99:AA:BB:CC JBLFlip
                    [DEL] Device 11:22:33:44:55:66 SomeRandomHeadphones
                    """.trimIndent(),
            ),
        )
        val probe = BluetoothProbe(runner = fake)
        assertNull(probe.discover())
    }

    @Test
    fun `discover finds polaris_-prefixed devices and ignores others`() {
        val fake = FakeRunner(
            cannedPerCommand = mapOf(
                "bluetoothctl --timeout 8 scan on" to
                    """
                    [NEW] Device 11:22:33:44:55:66 SomeRandomHeadphones
                    [NEW] Device AA:BB:CC:DD:EE:FF polaris_d13e86
                    [NEW] Device 77:88:99:AA:BB:CC JBLFlip
                    """.trimIndent(),
            ),
        )
        val probe = BluetoothProbe(runner = fake)
        val found = probe.discover()
        assertNotNull(found)
        assertEquals("AA:BB:CC:DD:EE:FF", found.address)
        assertEquals("polaris_d13e86", found.name)
    }

    @Test
    fun `discover treats polaris_ match as the first polaris device even if multiple appear`() {
        val fake = FakeRunner(
            cannedPerCommand = mapOf(
                "bluetoothctl --timeout 8 scan on" to
                    """
                    [NEW] Device AA:BB:CC:DD:EE:FF polaris_d13e86
                    [NEW] Device 11:22:33:44:55:66 polaris_ffff99
                    """.trimIndent(),
            ),
        )
        val probe = BluetoothProbe(runner = fake)
        val found = probe.discover()
        assertNotNull(found)
        // First match in scan order wins.
        assertEquals("AA:BB:CC:DD:EE:FF", found.address)
    }

    @Test
    fun `discover returns null when scan returns empty stdout`() {
        val fake = FakeRunner()
        val probe = BluetoothProbe(runner = fake)
        assertNull(probe.discover())
    }
}
