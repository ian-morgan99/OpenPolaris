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
 */
class BridgeOrchestratorTest {

    private val serviceOk = PolarisServiceIdentityProbe { _, _ -> Result.success("9090/284") }

    private class FakeRunner : ProcessRunner {
        val calls = mutableListOf<List<String>>()
        override fun run(argv: List<String>): String {
            calls += argv
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
        val orch = BridgeOrchestrator(wifi = wifi, bt = bt, serviceProbe = serviceOk)

        val messages = mutableListOf<String>()
        val ok = orch.bridgeToMount(
            profile = "polaris_d13e86",
            ifname = "wlp8s0",
            progress = { messages += it },
        )

        assertTrue(ok, "happy path should return true when link is up")

        // BT phase scans, finds nothing, and the orchestrator proceeds.
        assertTrue(
            messages.any { it.contains("BT wake failed") },
            "expected explicit BT wake failure message, got: " + messages.toString(),
        )
        // A bluetoothctl scan should have been issued.
        assertTrue(
            fake.calls.any { it.firstOrNull() == "bluetoothctl" && it.contains("scan") },
            "expected a bluetoothctl scan call, got: " + fake.calls.toString(),
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
    fun `bridge refuses connected state when 9090 service identity fails`() = runBlocking {
        val fake = FakeRunner()
        val wifi = StubbedWifiBridge(fake, linkUpResult = true)
        val bt = BluetoothProbe(runner = fake, wakeSettleMs = 0)
        val serviceFail = PolarisServiceIdentityProbe { _, _ -> Result.failure(IllegalStateException("wrong service")) }
        val orch = BridgeOrchestrator(wifi = wifi, bt = bt, serviceProbe = serviceFail)
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
        val orch = BridgeOrchestrator(bt = bt, knownBleAddress = "48:E7:DA:D4:B5:72")

        assertTrue(orch.wakeOnly())
        assertEquals(
            listOf("bluetoothctl", "connect", "48:E7:DA:D4:B5:72"),
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
        val orch = BridgeOrchestrator(wifi = wifi, bt = bt)

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
        val orch = BridgeOrchestrator(wifi = wifi, bt = bt)
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
        val orch = BridgeOrchestrator(wifi = wifi, bt = bt)

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
        val orch = BridgeOrchestrator(wifi = wifi, bt = bt)

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
        val orch = BridgeOrchestrator(bt = bt)

        val result = runCatching {
            orch.wakeOnly()
        }
        assertTrue(
            result.exceptionOrNull() is CancellationException,
            "cancelling during BT wake must propagate CancellationException, got: " +
                result.exceptionOrNull(),
        )
    }
}
