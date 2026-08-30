package dev.openpolaris.core.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * These tests run `WifiBridge` against a fake [ProcessRunner] that records
 * every command. The point is to prove the bridge never issues a Wi-Fi
 * scan command, no matter what.
 */
class WifiBridgeTest {

    private class FakeRunner : ProcessRunner {
        val calls = mutableListOf<List<String>>()
        val responses = mutableMapOf<String, String>()
        override fun run(argv: List<String>): String {
            calls += argv
            responses[argv.first()]?.let { return it }
            // Synthesize responses for `ip rule show` and `ip route show table <id>`
            // from the recorded `add` calls so idempotency checks behave realistically.
            return when {
                argv == listOf("ip", "rule", "show") -> synthesizeRuleShow()
                argv.first() == "ip" && argv.getOrNull(1) == "route" &&
                    argv.getOrNull(2) == "show" && argv.getOrNull(3) == "table" -> {
                    val tableId = argv.getOrNull(4) ?: ""
                    synthesizeRouteShowTable(tableId)
                }
                else -> ""
            }
        }

        private fun synthesizeRuleShow(): String {
            return calls.asSequence()
                .filter { it.first() == "ip" && it.getOrNull(1) == "rule" && it.getOrNull(2) == "add" }
                .map { "32765:\tfrom all to ${it[4]} lookup ${it[6]}" }
                .joinToString("\n")
        }

        private fun synthesizeRouteShowTable(tableId: String): String {
            return calls.asSequence()
                .filter { it.first() == "ip" && it.getOrNull(1) == "route" && it.getOrNull(2) == "add" &&
                    it.getOrNull(6) == "table" && it.getOrNull(7) == tableId }
                .map { call ->
                    val dest = call[3]
                    val devIdx = call.indexOf("dev")
                    val dev = if (devIdx >= 0) call.getOrNull(devIdx + 1) ?: "" else ""
                    val metricIdx = call.indexOf("metric")
                    val metric = if (metricIdx >= 0) " metric ${call.getOrNull(metricIdx + 1) ?: ""}" else ""
                    "$dest dev $dev$metric"
                }
                .joinToString("\n")
        }
    }

    private class FakeRtTables(initial: List<String> = emptyList()) : RtTables {
        val lines: MutableList<String> = initial.toMutableList()
        val appends: MutableList<String> = mutableListOf()
        override fun readLines(): List<String> = lines.toList()
        override fun appendLine(line: String) {
            appends += line
            lines += line
        }
    }

    @Test
    fun `connectByProfile issues exactly one nmcli connection up, no scans`() {
        val fake = FakeRunner()
        val bridge = WifiBridge(fake)
        bridge.connectByProfile("polaris_d13e86", "wlp8s0")
        assertEquals(
            listOf(listOf("nmcli", "connection", "up", "polaris_d13e86", "ifname", "wlp8s0")),
            fake.calls,
        )
    }

    @Test
    fun `installPolicyRoute adds rule and routes idempotently`() {
        val fake = FakeRunner()
        val rt = FakeRtTables()
        val bridge = WifiBridge(fake, gimbalCidr = "192.168.0.0/24", rtTables = rt)
        bridge.installPolicyRoute("wlp8s0")
        bridge.installPolicyRoute("wlp8s0")
        val rule = fake.calls.filter { it.first() == "ip" && it.getOrNull(1) == "rule" }
        val addRule = rule.count { it == listOf("ip", "rule", "add", "to", "192.168.0.0/24", "table", fakeLikelyTableId(), "priority", "1000") }
        assertEquals(1, addRule, "rule should be added once, even after two install calls")
        assertEquals(1, rt.appends.size, "rt_tables should be appended to once")
    }

    @Test
    fun `forbidden scan patterns are never produced by the bridge`() {
        val fake = FakeRunner()
        val bridge = WifiBridge(fake, rtTables = FakeRtTables())
        bridge.connectByProfile("polaris_d13e86", "wlp8s0")
        bridge.disconnectByProfile("polaris_d13e86")
        bridge.installPolicyRoute("wlp8s0")
        bridge.removePolicyRoute("wlp8s0")
        for (call in fake.calls) {
            val s = call.joinToString(" ")
            assertFalse(s.contains("rescan"), "forbidden: $s")
            assertFalse(s.contains("wifi list"), "forbidden: $s")
            assertFalse(s.contains("wifi connect"), "forbidden: $s")
            assertFalse(s.contains("iwlist"), "forbidden: $s")
            assertFalse(s.contains("iw dev"), "forbidden: $s")
            assertFalse(s.contains("scan"), "forbidden: $s")
        }
    }

    @Test
    fun `awaitLinkUp returns false when no IPv4 ever appears`() {
        val fake = FakeRunner()
        val bridge = WifiBridge(fake)
        // The actual link state reads happen via sysfs + NetworkInterface, so
        // a fake runner is irrelevant for this assertion. We just want the
        // signature to be non-blocking and bounded — assert the call shape.
        val started = System.currentTimeMillis()
        val result = bridge.awaitLinkUp("wlan9", timeoutMs = 600)
        val elapsed = System.currentTimeMillis() - started
        assertFalse(result)
        assertTrue(elapsed >= 500, "awaitLinkUp should poll, not return instantly (elapsed=$elapsed)")
    }

    private fun fakeLikelyTableId(): String {
        // Mirror the hash table-id strategy used by WifiBridge so the assertion
        // matches the implementation regardless of the String hash.
        val id = (1000 + ("polaris-wifi".hashCode() and 0x7FFF) % 9000)
        return id.toString()
    }
}
