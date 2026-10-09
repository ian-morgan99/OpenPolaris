package dev.openpolaris.core.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end exercise of [BridgeOrchestrator] against a fake [ProcessRunner].
 *
 * Two paths are covered:
 *
 *  * Happy path - the orchestrator is given a [WifiBridge] subclass that
 *    returns `true` from [WifiBridge.awaitLinkUp], so the NM-up, link-up,
 *    and policy-route phases all complete and the function returns `true`.
 *
 *  * Link-up failure - using a real [WifiBridge] against a non-existent
 *    interface. The NM-up phase still issues, the function returns `false`,
 *    and the failure message reaches the progress callback.
 *
 * The bridge proceeds to the saved Wi-Fi profile when Bluetooth wake is
 * unavailable, but it reports that outcome rather than claiming the wake
 * pulse succeeded.
 *
 * Every orchestrator here is given an explicit [KnownBleAddressStore]. The
 * production default reads `~/.config/openpolaris/known-ble-address`, which on
 * a developer machine holds the real mount's address; leaving it unset would
 * make these tests depend on whether this machine has ever woken a mount.
 */
/**
 * `bluetoothctl connect` reports a successful link only on stdout. Its exit
 * status is not a usable signal: an unreachable mount exits 0, and the normal
 * success path then aborts locally and exits non-zero. Fakes that want a wake
 * to succeed must return this.
 */
private const val CONNECTED_YES_OUTPUT = "[CHG] Device 48:E7:DA:D4:B5:72 Connected: yes\n"

/** True if a GATT connect was issued to [address], whatever timeout was used. */
private fun woke(calls: List<List<String>>, address: String): Boolean =
    calls.any { it.contains("connect") && it.contains(address) }

class BridgeOrchestratorTest {

    private val serviceOk = PolarisServiceIdentityProbe { _, _ -> Result.success("ports 22+9090/284") }

    /** A store with nothing recorded, so no address comes from persistence. */
    private fun emptyStore(): KnownBleAddressStore = KnownBleAddressStore.inMemory()

/** True if a GATT connect was issued to [address], whatever timeout was used. */
    private fun FakeRunner.wakeCall(address: String): Boolean =
        calls.any { it.contains("connect") && it.contains(address) }

    private class FakeRunner : ProcessRunner {
        val calls = mutableListOf<List<String>>()
        override fun run(argv: List<String>): String {
            calls += argv
            // A GATT connect only counts as delivered if stdout carried
            // `Connected: yes`; the exit status proves nothing. Waking tests
            // therefore need the marker, or wake() correctly reports failure.
            if (argv.contains("connect")) return "[CHG] Device 48:E7:DA:D4:B5:72 Connected: yes\n"
            return ""
        }
    }

    /**
     * WifiBridge subclass that lets us force the link-up result without
     * needing a real sysfs / NetworkInterface to exist on the test host.
     * The rest of the bridge (nmcli, ip rule, ip route) still goes through
     * the injected [runner], so we observe real call shapes.
     */
    private class StubbedWifiBridge(
        runner: ProcessRunner,
        private val linkUpResult: Boolean,
        gimbalCidr: String = "192.168.0.0/24",
    ) : WifiBridge(runner, gimbalCidr = gimbalCidr, rtTables = InMemoryRtTables()) {
        override fun awaitLinkUp(ifname: String, timeoutMs: Int): Boolean = linkUpResult
        override fun verifyPolarisIdentity(ifname: String): Result<LinkIdentity> =
            Result.success(LinkIdentity("polaris_test", "AA:BB:CC:DD:EE:FF", "192.168.0.1 dev $ifname"))
    }

    private class InMemoryRtTables : RtTables {
        val lines: MutableList<String> = mutableListOf()
        override fun readLines(): List<String> = lines.toList()
        override fun appendLine(line: String) { lines += line }
    }

    @Test
    fun `bridgeToMount happy path runs NM up, awaits link, installs policy route`() = runBlocking {
        val fake = FakeRunner()
        val wifi = StubbedWifiBridge(fake, linkUpResult = true)
        // Fake scanner returns no devices. The orchestrator reports it and
        // still lets saved-profile activation verify whether Wi-Fi is ready.
        val bt = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        val orch = BridgeOrchestrator(wifi = wifi, bt = bt, serviceProbe = serviceOk, addressStore = emptyStore(), wakeLinkTimeoutMs = 200, activationSliceMs = 50L)

        val messages = mutableListOf<String>()
        val ok = orch.bridgeToMount(
            profile = "polaris_d13e86",
            ifname = "wlp8s0",
            progress = { messages += it },
        )

        assertTrue(ok, "happy path should return true when link is up")

        // The BlueZ cache is consulted before any scan, and an empty cache
        // falls through to a scan; either way the bridge must still proceed to
        // the saved profile rather than treating "not discovered" as terminal.
        // That hard-fail (5638519) is what made the wake pulse unreachable.
        assertTrue(
            fake.calls.any { it == listOf("bluetoothctl", "devices") },
            "expected the BlueZ cache to be consulted first, got: " + fake.calls.toString(),
        )
        assertTrue(
            fake.calls.any { it.firstOrNull() == "bluetoothctl" && it.contains("scan") },
            "expected a bluetoothctl scan fallback call, got: " + fake.calls.toString(),
        )
        assertFalse(
            messages.any { it.contains("Link never came up") },
            "an undiscoverable device must not be reported as a dead mount: " + messages.toString(),
        )

        // NM-up, link-up wait, policy route install all happened.
        val nmUp = fake.calls.filter { it.firstOrNull() == "nmcli" && it.getOrNull(1) == "connection" }
        assertEquals(
            listOf(listOf("nmcli", "connection", "up", "polaris_d13e86", "ifname", "wlp8s0")),
            nmUp,
        )
        assertTrue(fake.calls.any { it.takeLast(2) == listOf("install", "wlp8s0") })

        // No scan primitives issued anywhere along the path.
        for (call in fake.calls) {
            val s = call.joinToString(" ")
            assertFalse(s.contains("rescan"), "forbidden: " + s)
            assertFalse(s.contains("wifi list"), "forbidden: " + s)
            assertFalse(s.contains("wifi connect"), "forbidden: " + s)
            assertFalse(s.contains("iwlist"), "forbidden: " + s)
            assertFalse(s.contains("iw scan"), "forbidden: " + s)
        }

        // The final "ready" progress message was emitted.
        assertTrue(
            messages.any { it.startsWith("Mount Wi-Fi ready") },
            "expected ready message, got: " + messages.toString(),
        )
    }

    @Test
    fun `bridge refuses connected state when required service ports fail verification`() = runBlocking {
        val fake = FakeRunner()
        val wifi = StubbedWifiBridge(fake, linkUpResult = true)
        val bt = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        val serviceFail = PolarisServiceIdentityProbe { _, _ -> Result.failure(IllegalStateException("wrong service")) }
        val orch = BridgeOrchestrator(wifi = wifi, bt = bt, serviceProbe = serviceFail, addressStore = emptyStore(), wakeLinkTimeoutMs = 200, activationSliceMs = 50L)
        val messages = mutableListOf<String>()

        val ok = orch.bridgeToMount("polaris_test", "wlp8s0") { messages += it }

        assertFalse(ok)
        assertTrue(messages.any { it.contains("service identity failed") })
        assertTrue(fake.calls.any { it.takeLast(2) == listOf("remove", "wlp8s0") })
    }

    @Test
    fun `wake tries configured known BLE address without scanning`() = runBlocking {
        val fake = FakeRunner()
        val bt = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        val orch = BridgeOrchestrator(bt = bt, knownBleAddress = "48:E7:DA:D4:B5:72", addressStore = emptyStore())

        assertTrue(orch.wakeOnly())
        assertEquals(
            listOf("bluetoothctl", "--timeout", "15", "connect", "48:E7:DA:D4:B5:72"),
            fake.calls.first(),
        )
        assertFalse(fake.calls.any { "scan" in it })
    }

    @Test
    fun `bridgeToMount returns false and emits failure message when link never comes up`() = runBlocking {
        val fake = FakeRunner()
        // Real WifiBridge; awaitLinkUp polls sysfs/NetworkInterface for wlan9,
        // which doesn't exist on the test host, so it returns false within the
        // bounded poll window.
        val wifi = WifiBridge(fake, gimbalCidr = "192.168.0.0/24", rtTables = InMemoryRtTables())
        val bt = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        val orch = BridgeOrchestrator(wifi = wifi, bt = bt, addressStore = emptyStore(), wakeLinkTimeoutMs = 200, activationSliceMs = 50L)

        val messages = mutableListOf<String>()
        val ok = orch.bridgeToMount(
            profile = "polaris_d13e86",
            ifname = "wlan9",
            progress = { messages += it },
        )

        assertFalse(ok, "missing interface should cause link-up to fail")
        // NM-up must still be issued - the orchestrator tries first, then waits.
        val nmUp = fake.calls.filter { it.firstOrNull() == "nmcli" && it.getOrNull(1) == "connection" }
        assertEquals(
            listOf(listOf("nmcli", "connection", "up", "polaris_d13e86", "ifname", "wlan9")),
            nmUp,
        )
        // No policy route should be installed when the link isn't up.
        val ruleAdd = fake.calls.filter { it.first() == "ip" && it.getOrNull(1) == "rule" && it.getOrNull(2) == "add" }
        assertTrue(ruleAdd.isEmpty(), "no rule should be added when link-up fails, got: " + ruleAdd.toString())
        // The failure message reached the progress callback.
        assertTrue(
            messages.any { it.contains("Link never came up") },
            "expected link-failure message, got: " + messages.toString(),
        )
    }

    @Test
    fun `bridgeToMount stops when saved profile activation fails`() = runBlocking {
        val fake = object : ProcessRunner {
            val calls = mutableListOf<List<String>>()

            override fun run(argv: List<String>): String {
                calls += argv
                if (argv.firstOrNull() == "nmcli") {
                    throw BridgeException("nmcli", 10, "No suitable device found")
                }
                return ""
            }
        }
        val wifi = StubbedWifiBridge(fake, linkUpResult = true)
        val bt = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        val orch = BridgeOrchestrator(wifi = wifi, bt = bt, addressStore = emptyStore(), wakeLinkTimeoutMs = 200, activationSliceMs = 50L)
        val messages = mutableListOf<String>()

        val ok = orch.bridgeToMount(
            profile = "polaris_d13e86",
            ifname = "wlp8s0",
            progress = { messages += it },
        )

        assertFalse(ok, "failed saved-profile activation must be terminal")
        assertTrue(
            messages.any { it.startsWith("Wi-Fi activation failed:") },
            "expected activation failure message, got: $messages",
        )
        assertFalse(
            fake.calls.any { it.firstOrNull() == "ip" && it.getOrNull(1) == "rule" },
            "a policy route must not be installed after activation fails",
        )
    }

    @Test
    fun `tearDown removes policy route and brings profile down in order`() = runBlocking {
        val fake = FakeRunner()
        val wifi = WifiBridge(fake, gimbalCidr = "192.168.0.0/24", rtTables = InMemoryRtTables())
        val bt = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        val orch = BridgeOrchestrator(wifi = wifi, bt = bt, addressStore = emptyStore(), wakeLinkTimeoutMs = 200, activationSliceMs = 50L)

        val messages = mutableListOf<String>()
        orch.tearDown(
            profile = "polaris_d13e86",
            ifname = "wlp8s0",
            progress = { messages += it },
        )

        val helperRemove = fake.calls.filter { it.takeLast(2) == listOf("remove", "wlp8s0") }
        assertEquals(1, helperRemove.size, "expected one helper remove, got: $helperRemove")
        val nmDown = fake.calls.filter {
            it.firstOrNull() == "nmcli" && it.getOrNull(1) == "connection" && it.getOrNull(2) == "down"
        }
        assertEquals(
            listOf(listOf("nmcli", "connection", "down", "polaris_d13e86")),
            nmDown,
        )
        // Order: policy route removal before profile down.
        val ruleDelIdx = fake.calls.indexOfFirst { it == helperRemove.first() }
        val nmDownIdx = fake.calls.indexOfFirst { it == nmDown.first() }
        assertTrue(ruleDelIdx < nmDownIdx, "policy route should be removed before nmcli down")
        assertTrue(messages.any { it.contains("torn down") })
    }

    /**
     * A [ProcessRunner] whose `bluetoothctl scan on` returns a single Polaris
     * device so [BluetoothProbe.discover] finds it and the orchestrator records
     * the woken address. Every other call returns an empty string (success).
     */
    private class ScanningRunner : ProcessRunner {
        val calls = mutableListOf<List<String>>()
        override fun run(argv: List<String>): String {
            calls += argv
            return when {
                argv.contains("scan") -> "Device AA:BB:CC:DD:EE:FF polaris_d13e86\n"
                argv.contains("connect") -> CONNECTED_YES_OUTPUT
                else -> ""
            }
        }
    }

    @Test
    fun `tearDown releases the retained GATT link after a wake`() = runBlocking {
        val fake = ScanningRunner()
        val wifi = WifiBridge(fake, gimbalCidr = "192.168.0.0/24", rtTables = InMemoryRtTables())
        val bt = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        val orch = BridgeOrchestrator(wifi = wifi, bt = bt, addressStore = emptyStore(), wakeLinkTimeoutMs = 200, activationSliceMs = 50L)

        // Wake first: discover finds the device and the orchestrator records the
        // woken address so the retained GATT link can be released later.
        val woke = orch.wakeOnly()
        assertTrue(woke, "wakeOnly should succeed when a Polaris device is discovered")

        val messages = mutableListOf<String>()
        orch.tearDown(
            profile = "polaris_d13e86",
            ifname = "wlp8s0",
            progress = { messages += it },
        )

        // The retained GATT link must be dropped after the Wi-Fi profile is down.
        val disconnect = fake.calls.filter {
            it.firstOrNull() == "bluetoothctl" && it.getOrNull(1) == "disconnect"
        }
        assertEquals(
            listOf(listOf("bluetoothctl", "disconnect", "AA:BB:CC:DD:EE:FF")),
            disconnect,
            "expected the retained GATT link to be released on teardown, got: " + fake.calls.toString(),
        )
        // The release happens after the Wi-Fi profile is brought down.
        val nmDownIdx = fake.calls.indexOfFirst {
            it.firstOrNull() == "nmcli" && it.getOrNull(1) == "connection" && it.getOrNull(2) == "down"
        }
        val disconnectIdx = fake.calls.indexOfFirst {
            it.firstOrNull() == "bluetoothctl" && it.getOrNull(1) == "disconnect"
        }
        assertTrue(nmDownIdx < disconnectIdx, "GATT release should happen after the Wi-Fi profile is down")
        assertTrue(messages.any { it.contains("Releasing retained BLE wake link") })
    }

    @Test
    fun `tearDown does not release GATT when no device was woken`() = runBlocking {
        val fake = FakeRunner()
        val wifi = WifiBridge(fake, gimbalCidr = "192.168.0.0/24", rtTables = InMemoryRtTables())
        val bt = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        val orch = BridgeOrchestrator(wifi = wifi, bt = bt, addressStore = emptyStore(), wakeLinkTimeoutMs = 200, activationSliceMs = 50L)

        // No wake happened this session, so teardown must not issue a disconnect.
        orch.tearDown(profile = "polaris_d13e86", ifname = "wlp8s0")

        val disconnect = fake.calls.filter {
            it.firstOrNull() == "bluetoothctl" && it.getOrNull(1) == "disconnect"
        }
        assertEquals(0, disconnect.size, "no GATT release expected when nothing was woken: $disconnect")
    }

    /**
     * #52 — cancellation must propagate out of [BridgeOrchestrator.bridgeToMount]
     * and [BridgeOrchestrator.wakeOnly] rather than being folded into a normal
     * `false` result. The injected [ProcessRunner] throws the same
     * [CancellationException] produced when its owning coroutine is cancelled;
     * the exception must escape instead of being converted to `false`.
     */

    private class CancellingRunner : ProcessRunner {
        val calls = mutableListOf<List<String>>()
        override fun run(argv: List<String>): String {
            calls += argv
            throw CancellationException("test cancellation")
        }
    }

    @Test
    fun `bridgeToMount propagates cancellation instead of returning false`() = runBlocking {
        val runner = CancellingRunner()
        val wifi = StubbedWifiBridge(runner, linkUpResult = true)
        val bt = BluetoothProbe(runner = runner, wakeSettleMs = 0)
        val orch = BridgeOrchestrator(wifi = wifi, bt = bt, addressStore = emptyStore(), wakeLinkTimeoutMs = 200, activationSliceMs = 50L)

        val result = runCatching {
            orch.bridgeToMount("polaris_d13e86", "wlp8s0")
        }
        assertTrue(
            result.exceptionOrNull() is CancellationException,
            "cancelling during a bridge phase must propagate CancellationException, got: " +
                result.exceptionOrNull(),
        )
    }

    @Test
    fun `wakeOnly propagates cancellation instead of returning false`() = runBlocking {
        val runner = CancellingRunner()
        val bt = BluetoothProbe(runner = runner, wakeSettleMs = 0)
        val orch = BridgeOrchestrator(bt = bt, addressStore = emptyStore())

        val result = runCatching {
            orch.wakeOnly()
        }
        assertTrue(
            result.exceptionOrNull() is CancellationException,
            "cancelling during BT wake must propagate CancellationException, got: " +
                result.exceptionOrNull(),
        )
    }

    // ---------------------------------------------------------------------
    // The 2026-10-08 wake regression.
    //
    // 5638519 made "no device discovered" terminal, but the mount is not
    // advertisable in either state we wake it from, so discovery can never
    // succeed and the GATT pulse was never issued. These tests pin the
    // corrected contract: an address from the BlueZ cache or the persisted
    // store must produce a connect, and discovery must never gate it.
    // ---------------------------------------------------------------------

    /** Runner that answers `bluetoothctl devices` from the BlueZ cache only. */
    private class CachedOnlyRunner(private val cache: String) : ProcessRunner {
        val calls = mutableListOf<List<String>>()
        override fun run(argv: List<String>): String {
            calls += argv
            return when {
                argv == listOf("bluetoothctl", "devices") -> cache
                argv.contains("connect") -> CONNECTED_YES_OUTPUT
                else -> ""
            }
        }
    }

    @Test
    fun `wake uses the BlueZ cache when a scan would find nothing`() = runBlocking {
        // The real-world condition: cache populated, scan returns nothing
        // because the mount is asleep. Wake must still be attempted.
        val fake = CachedOnlyRunner("Device 48:E7:DA:D4:B5:72 polaris_d13e86\n")
        val bt = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        val store = KnownBleAddressStore.inMemory()
        val orch = BridgeOrchestrator(bt = bt, addressStore = store)

        assertTrue(orch.wakeOnly(), "a cached address must be enough to wake")
        assertTrue(
            woke(fake.calls, "48:E7:DA:D4:B5:72"),
            "expected a GATT connect from the cached address, got: ${fake.calls}",
        )
        assertFalse(
            fake.calls.any { "scan" in it },
            "a cache hit must not fall through to a scan: ${fake.calls}",
        )
        // And the success is remembered so the next wake needs no cache at all.
        assertEquals("48:E7:DA:D4:B5:72", store.read())
    }

    @Test
    fun `wake prefers the persisted address over cache and scan`() = runBlocking {
        val fake = CachedOnlyRunner("Device 99:99:99:99:99:99 polaris_other\n")
        val bt = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        val orch = BridgeOrchestrator(
            bt = bt,
            addressStore = KnownBleAddressStore.inMemory("48:E7:DA:D4:B5:72"),
        )

        assertTrue(orch.wakeOnly())
        assertEquals(
            listOf("bluetoothctl", "--timeout", "15", "connect", "48:E7:DA:D4:B5:72"),
            fake.calls.first(),
            "the last known-good address must win, and cost no subprocess to find",
        )
    }

    @Test
    fun `wake falls back to a scan only when nothing is known`() = runBlocking {
        val fake = ScanningRunner() // answers `scan on` with AA:BB:CC:DD:EE:FF
        val bt = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        val orch = BridgeOrchestrator(bt = bt, addressStore = emptyStore())

        assertTrue(orch.wakeOnly())
        assertTrue(
            fake.calls.any { "scan" in it },
            "with no cached address a scan is the correct last resort",
        )
        assertTrue(
            woke(fake.calls, "AA:BB:CC:DD:EE:FF"),
            "a scanned address must still be woken, got: ${fake.calls}",
        )
    }

    @Test
    fun `bridge retries profile activation while a woken AP comes on the air`() = runBlocking {
        // A sleeping mount cannot be activated on the first try: NetworkManager
        // has no SSID to join yet. The first `nmcli connection up` fails, the
        // AP appears, and the retry succeeds — all inside the wake budget.
        val calls = mutableListOf<List<String>>()
        var nmcliUpAttempts = 0
        val runner = object : ProcessRunner {
            override fun run(argv: List<String>): String {
                calls += argv
                if (argv == listOf("bluetoothctl", "devices")) {
                    return "Device 48:E7:DA:D4:B5:72 polaris_d13e86\n"
                }
                if (argv.getOrNull(0) == "nmcli" && argv.getOrNull(2) == "up") {
                    nmcliUpAttempts++
                    if (nmcliUpAttempts < 3) throw BridgeException("nmcli", 7, "No network with this SSID")
                }
                if (argv.contains("connect")) return CONNECTED_YES_OUTPUT
                return ""
            }
        }
        val wifi = object : WifiBridge(runner, gimbalCidr = "192.168.0.0/24", rtTables = InMemoryRtTables()) {
            // Link appears only on the third activation attempt.
            override fun awaitLinkUp(ifname: String, timeoutMs: Int): Boolean = nmcliUpAttempts >= 3
            override fun verifyPolarisIdentity(ifname: String): Result<LinkIdentity> =
                Result.success(LinkIdentity("polaris_test", "AA:BB:CC:DD:EE:FF", "192.168.0.1 dev $ifname"))
        }
        val bt = BluetoothProbe(runner = runner, wakeSettleMs = 0)
        val orch = BridgeOrchestrator(
            wifi = wifi,
            bt = bt,
            serviceProbe = serviceOk,
            addressStore = emptyStore(),
            wakeLinkTimeoutMs = 5_000,
            activationSliceMs = 50L,
        )

        val messages = mutableListOf<String>()
        val ok = orch.bridgeToMount("polaris_d13e86", "wlp8s0") { messages += it }

        assertTrue(ok, "a transient activation failure during wake must be retried, got: $messages")
        assertTrue(nmcliUpAttempts >= 3, "expected repeated activation attempts, got $nmcliUpAttempts")
        assertTrue(messages.any { it.startsWith("Mount Wi-Fi ready") }, "expected ready message, got: $messages")
    }

    @Test
    fun `bridge reports the dead-mount message only after the full wake budget`() = runBlocking {
        // No interface, no link, activation always fine: the failure must come
        // after the long post-wake budget, not after a fixed 15 s.
        val fake = CachedOnlyRunner("Device 48:E7:DA:D4:B5:72 polaris_d13e86\n")
        val wifi = WifiBridge(fake, gimbalCidr = "192.168.0.0/24", rtTables = InMemoryRtTables())
        val bt = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        val orch = BridgeOrchestrator(
            wifi = wifi,
            bt = bt,
            addressStore = emptyStore(),
            wakeLinkTimeoutMs = 300,
            activationSliceMs = 50L,
        )

        val messages = mutableListOf<String>()
        val ok = orch.bridgeToMount("polaris_d13e86", "wlan9") { messages += it }

        assertFalse(ok)
        assertTrue(
            messages.any { it.contains("Link never came up") },
            "expected the link-failure message once the budget is exhausted, got: $messages",
        )
    }
}
