package dev.openpolaris.core.net

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
 * The default [BluetoothProbe] (blank GATT UUIDs) makes the BT phase
 * short-circuit cleanly, which is exactly the behaviour the user wants
 * until the real GATT UUIDs are captured from the official Benro app.
 */
class BridgeOrchestratorTest {

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
        val bt = BluetoothProbe(runner = fake) // blank GATT UUIDs -> BT skipped
        val orch = BridgeOrchestrator(wifi = wifi, bt = bt)

        val messages = mutableListOf<String>()
        val ok = orch.bridgeToMount(
            profile = "polaris_d13e86",
            ifname = "wlp8s0",
            progress = { messages += it },
        )

        assertTrue(ok, "happy path should return true when link is up")

        // BT phase short-circuits because GATT UUIDs are blank.
        assertTrue(
            messages.any { it.contains("BT wake skipped") },
            "expected BT-skipped progress message, got: " + messages.toString(),
        )

        // NM-up, link-up wait, policy route install all happened.
        val nmUp = fake.calls.filter { it.firstOrNull() == "nmcli" && it.getOrNull(1) == "connection" }
        assertEquals(
            listOf(listOf("nmcli", "connection", "up", "polaris_d13e86", "ifname", "wlp8s0")),
            nmUp,
        )
        val ruleAdd = fake.calls.filter {
            it.first() == "ip" && it.getOrNull(1) == "rule" && it.getOrNull(2) == "add" &&
                it.getOrNull(3) == "to" && it.getOrNull(4) == "192.168.0.0/24"
        }
        assertEquals(1, ruleAdd.size, "expected one policy rule add, got " + ruleAdd.size.toString() + ": " + ruleAdd.toString())

        // No scan primitives issued anywhere along the path.
        for (call in fake.calls) {
            val s = call.joinToString(" ")
            assertFalse(s.contains("rescan"), "forbidden: " + s)
            assertFalse(s.contains("wifi list"), "forbidden: " + s)
            assertFalse(s.contains("wifi connect"), "forbidden: " + s)
            assertFalse(s.contains("iwlist"), "forbidden: " + s)
            assertFalse(s.contains("iw dev"), "forbidden: " + s)
            assertFalse(s.contains("iw scan"), "forbidden: " + s)
        }

        // The final "ready" progress message was emitted.
        assertTrue(
            messages.any { it.startsWith("Mount Wi-Fi ready") },
            "expected ready message, got: " + messages.toString(),
        )
    }

    @Test
    fun `bridgeToMount returns false and emits failure message when link never comes up`() = runBlocking {
        val fake = FakeRunner()
        // Real WifiBridge; awaitLinkUp polls sysfs/NetworkInterface for wlan9,
        // which doesn't exist on the test host, so it returns false within the
        // bounded poll window.
        val wifi = WifiBridge(fake, gimbalCidr = "192.168.0.0/24", rtTables = InMemoryRtTables())
        val bt = BluetoothProbe(runner = fake)
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
    fun `tearDown removes policy route and brings profile down in order`() = runBlocking {
        val fake = FakeRunner()
        val wifi = WifiBridge(fake, gimbalCidr = "192.168.0.0/24", rtTables = InMemoryRtTables())
        val bt = BluetoothProbe(runner = fake)
        val orch = BridgeOrchestrator(wifi = wifi, bt = bt)

        val messages = mutableListOf<String>()
        orch.tearDown(
            profile = "polaris_d13e86",
            ifname = "wlp8s0",
            progress = { messages += it },
        )

        val ruleDel = fake.calls.filter { it.first() == "ip" && it.getOrNull(1) == "rule" && it.getOrNull(2) == "del" }
        assertEquals(1, ruleDel.size, "expected one policy rule del, got: " + ruleDel.toString())
        val nmDown = fake.calls.filter {
            it.firstOrNull() == "nmcli" && it.getOrNull(1) == "connection" && it.getOrNull(2) == "down"
        }
        assertEquals(
            listOf(listOf("nmcli", "connection", "down", "polaris_d13e86")),
            nmDown,
        )
        // Order: policy route removal before profile down.
        val ruleDelIdx = fake.calls.indexOfFirst { it == ruleDel.first() }
        val nmDownIdx = fake.calls.indexOfFirst { it == nmDown.first() }
        assertTrue(ruleDelIdx < nmDownIdx, "policy route should be removed before nmcli down")
        assertTrue(messages.any { it.contains("torn down") })
    }
}
