package dev.openpolaris.core.net

import dev.openpolaris.core.net.BluetoothProbe.DiscoveredDevice
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises [BluetoothProbe] against a fake [ProcessRunner]. Locks down the
 * live-verified wake rule: connect first; pairing is only a fallback.
 * so future refactors can't accidentally drop a step or change the order.
 */
/**
 * What `bluetoothctl --timeout N connect <addr>` prints on stdout when the link
 * really was established. The exit status is *not* a usable signal here — see
 * the tests around [BluetoothProbe.CONNECTED_YES].
 */
private const val CONNECTED_OK =
    "Attempting to connect to AA:BB:CC:DD:EE:FF\n[CHG] Device AA:BB:CC:DD:EE:FF Connected: yes\n"

class BluetoothProbeTest {

    private class FakeRunner(
        private val cannedPerCommand: Map<String, String> = emptyMap(),
        private val failuresPerCommand: Map<String, BridgeException> = emptyMap(),
    ) : ProcessRunner {
        val calls = mutableListOf<List<String>>()

        /**
         * Keys match as a *prefix* of the invoked command, so a test can pin
         * behaviour for `bluetoothctl connect` without restating the timeout
         * the probe happens to pass today.
         */
        private fun <T> lookup(table: Map<String, T>): T? {
            val cmd = calls.last().joinToString(" ")
            return table.entries.firstOrNull { cmd.startsWith(it.key) }?.value
        }

        override fun run(args: List<String>): String {
            calls += args
            lookup(failuresPerCommand)?.let { throw it }
            return lookup(cannedPerCommand) ?: ""
        }
    }

    private fun dev(addr: String = "AA:BB:CC:DD:EE:FF", name: String = "polaris_d13e86") =
        DiscoveredDevice(addr, name)

    /**
     * `bluetoothctl` subcommands are not at a fixed index: `connect` and
     * `disconnect` are preceded by `--timeout N`, while `pair`/`trust` are not.
     */
    private fun subcommandOf(args: List<String>): String =
        args.firstOrNull { it in setOf("connect", "disconnect", "pair", "trust") } ?: args.getOrNull(1).orEmpty()

    @Test
    fun `wake connects directly and retains GATT by default`() {
        val fake = FakeRunner(
            cannedPerCommand = mapOf("bluetoothctl --timeout 15 connect" to CONNECTED_OK),
        )
        val probe = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        probe.wake(dev())

        // Filter only the bluetoothctl calls relevant to wake().
        val bt = fake.calls.filter { it.firstOrNull() == "bluetoothctl" }
        assertEquals(
            listOf(
                listOf("bluetoothctl", "--timeout", "15", "connect", "AA:BB:CC:DD:EE:FF"),
            ),
            bt,
        )
    }

    @Test
    fun `release disconnects after the caller completes handoff`() {
        val fake = FakeRunner()
        val probe = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        probe.release(dev())
        assertEquals(
            listOf(listOf("bluetoothctl", "disconnect", "AA:BB:CC:DD:EE:FF")),
            fake.calls,
        )
    }

    @Test
    fun `wake can add a post-pulse settle delay`() {
        val fake = FakeRunner(cannedPerCommand = mapOf("bluetoothctl --timeout 15 connect" to CONNECTED_OK))
        val probe = BluetoothProbe(runner = fake, wakeSettleMs = 0, retainGattConnection = false)
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
                if (args.getOrNull(2) == "disconnect") {
                    throw BridgeException("bluetoothctl", 1, "not connected")
                }
                return CONNECTED_OK
            }
        }
        val probe = BluetoothProbe(runner = fake, wakeSettleMs = 0, retainGattConnection = false)
        // Should not throw even though disconnect fails.
        probe.wake(dev())
        // Disconnect was attempted.
        assertTrue(
            fake.calls.any { it.firstOrNull() == "bluetoothctl" && it.contains("disconnect") },
            "disconnect must still be attempted, got: " + fake.calls.toString(),
        )
    }

    @Test
    fun `pair failure does not suppress retry of the wake-producing connect`() {
        val fake = object : ProcessRunner {
            var connects = 0
            override fun run(args: List<String>): String {
                if (args.contains("connect")) {
                    connects++
                    if (connects == 1) throw BridgeException("bluetoothctl", 1, "not cached")
                    return CONNECTED_OK
                }
                if (args.contains("pair")) {
                    throw BridgeException("bluetoothctl", 1, "pair rejected")
                }
                return ""
            }
        }
        val probe = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        probe.wake(dev())
        assertEquals(2, fake.connects)
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

    // ---------------------------------------------------------------------
    // knownDevices(): the address source that works when scanning cannot.
    //
    // Measured live on 2026-10-08: the mount is not advertisable while asleep
    // (BLE radio down with the AP) and stops advertising once a client is
    // connected, so `scan on` returns no polaris_ device in either state. The
    // BlueZ cache does. These tests keep knownDevices() usable as the primary
    // source so a future refactor cannot quietly make wake depend on a scan
    // again — the regression that broke the desktop "Wake" button.
    // ---------------------------------------------------------------------

    @Test
    fun `knownDevices reads the BlueZ cache without issuing a scan`() {
        val fake = FakeRunner(
            cannedPerCommand = mapOf(
                "bluetoothctl devices" to
                    """
                    Device 48:E7:DA:D4:B5:72 polaris_d13e86
                    Device 11:22:33:44:55:66 some_other_gadget
                    """.trimIndent(),
            ),
        )
        val probe = BluetoothProbe(runner = fake)

        val found = probe.knownDevices()

        assertEquals(listOf("48:E7:DA:D4:B5:72"), found.map { it.address })
        assertEquals(listOf("polaris_d13e86"), found.map { it.name })
        // No scan: this path must stay cheap and must not disturb the adapter.
        assertEquals(listOf(listOf("bluetoothctl", "devices")), fake.calls)
    }

    @Test
    fun `knownDevices is empty when BlueZ has never seen the device`() {
        val fake = FakeRunner()
        val probe = BluetoothProbe(runner = fake)
        assertEquals(emptyList(), probe.knownDevices())
    }

    @Test
    fun `knownDevices does not throw when bluetoothctl is unavailable`() {
        val throwing = ProcessRunner { throw BridgeException("bluetoothctl", -1, "adapter down") }
        val probe = BluetoothProbe(runner = throwing)
        assertEquals(emptyList(), probe.knownDevices())
    }

    @Test
    fun `knownDevices propagates cancellation instead of reporting no devices`() {
        // #52: an empty list is a meaningful answer ("nothing cached, go on"),
        // so a cancellation must not be disguised as one.
        val cancelling = ProcessRunner { throw CancellationException("test cancellation") }
        val probe = BluetoothProbe(runner = cancelling)
        assertFailsWith<CancellationException> { probe.knownDevices() }
    }

    @Test
    fun `wake treats a BlueZ local abort as an issued pulse`() {
        // `Connected: yes` followed by `le-connection-abort-by-local` is the
        // normal outcome on this host, and it still wakes the AP. Treating it
        // as a failure caused pointless pair/trust retries and, before that,
        // hid whether a pulse had been sent at all.
        val fake = object : ProcessRunner {
            val calls = mutableListOf<List<String>>()
            override fun run(args: List<String>): String {
                calls += args
                if (args.contains("connect")) {
                    // The link came up (stdout carried `Connected: yes`), then
                    // BlueZ dropped it and exited non-zero with this on stderr.
                    throw BridgeException("bluetoothctl", 1, "org.bluez.Error.Failed le-connection-abort-by-local")
                }
                return ""
            }
        }
        val probe = BluetoothProbe(runner = fake, wakeSettleMs = 0)

        probe.wake(dev())

        val bt = fake.calls.filter { it.firstOrNull() == "bluetoothctl" }
        assertEquals(
            listOf(listOf("bluetoothctl", "--timeout", "15", "connect", "AA:BB:CC:DD:EE:FF")),
            bt,
            "a local abort must not trigger pair/trust retries",
        )
    }

    @Test
    fun `wake still retries via pair and trust for a genuine connect failure`() {
        val fake = object : ProcessRunner {
            val calls = mutableListOf<List<String>>()
            override fun run(args: List<String>): String {
                calls += args
                if (args.getOrNull(1) == "connect") {
                    throw BridgeException("bluetoothctl", 1, "org.bluez.Error.ConnectionAttemptFailed")
                }
                return ""
            }
        }
        val probe = BluetoothProbe(runner = fake, wakeSettleMs = 0)

        assertFailsWith<BridgeException> { probe.wake(dev()) }

        val bt = fake.calls.filter { it.firstOrNull() == "bluetoothctl" }.map { subcommandOf(it) }
        assertEquals(listOf("connect", "pair", "trust", "connect"), bt)
    }

    // ---------------------------------------------------------------------
    // Success must be read from the output, never from the exit status.
    //
    // Measured live on 2026-10-09 with the mount powered off: `bluetoothctl
    // --timeout 15 connect <addr>` printed only "Attempting to connect to …"
    // and exited 0. A previous version of wake() treated a zero exit as a
    // delivered pulse, so the desktop reported "woke polaris_d13e86" for a
    // pulse that was never sent, and the bridge then waited out its whole
    // link budget for an AP that had never been woken.
    // ---------------------------------------------------------------------

    @Test
    fun `wake does not claim success when connect exits zero without connecting`() {
        // The exact output of a connect to an unreachable mount: exit 0, no
        // `Connected: yes`.
        val fake = FakeRunner(
            cannedPerCommand = mapOf(
                "bluetoothctl --timeout 15 connect" to "Attempting to connect to AA:BB:CC:DD:EE:FF\n",
            ),
        )
        val probe = BluetoothProbe(runner = fake, wakeSettleMs = 0)

        assertFailsWith<BridgeException> { probe.wake(dev()) }

        // And it must have tried the pair/trust recovery rather than stopping
        // at the first silent failure.
        assertEquals(
            listOf("connect", "pair", "trust", "connect"),
            fake.calls.filter { it.firstOrNull() == "bluetoothctl" }.map { subcommandOf(it) },
        )
    }

    @Test
    fun `wake reports the mount is unreachable rather than a bare failure`() {
        val fake = FakeRunner(
            cannedPerCommand = mapOf(
                "bluetoothctl --timeout 15 connect" to "Attempting to connect to AA:BB:CC:DD:EE:FF\n",
            ),
        )
        val probe = BluetoothProbe(runner = fake, wakeSettleMs = 0)

        val error = assertFailsWith<BridgeException> { probe.wake(dev()) }
        assertTrue(
            error.message!!.contains("deep sleep"),
            "the message should name the likely cause, got: " + error.message,
        )
    }

    @Test
    fun `wake bounds the connect so a slow adapter cannot stall the bridge`() {
        val fake = FakeRunner(cannedPerCommand = mapOf("bluetoothctl --timeout 15 connect" to CONNECTED_OK))
        val probe = BluetoothProbe(runner = fake, wakeSettleMs = 0)

        probe.wake(dev())

        val connect = fake.calls.first { it.contains("connect") }
        val timeout = connect[connect.indexOf("--timeout") + 1].toInt()
        assertTrue(
            timeout in 1..30,
            "connect must be bounded, and well inside the bridge link budget, got ${timeout}s",
        )
    }
}
