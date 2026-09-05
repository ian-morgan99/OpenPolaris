package dev.openpolaris.core.net

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Brings a single, saved Wi-Fi profile up on a dedicated interface with a
 * policy route that keeps it off the LAN.
 *
 * Constraints baked in (because the user disabled Wi-Fi over an auth-storm):
 *   - **No scans.** The bridge must never invoke a Wi-Fi scan primitive; the
 *     only Wi-Fi control it does is bring a pre-existing NetworkManager profile
 *     up. See NoScanGuardTest for the exact list of forbidden patterns.
 *   - **Segregation.** A dedicated `ip rule` + `ip route` table is installed
 *     so traffic to the gimbal subnet leaves via `wlp8s0` only, and
 *     `wlp8s0` is never promoted to the default route.
 *   - **Idempotent.** Every step checks before adding; re-running is safe.
 */
open class WifiBridge(
    private val runner: ProcessRunner = SystemProcessRunner,
    private val gimbalCidr: String = "192.168.0.0/24",
    private val policyTableName: String = "polaris-wifi",
    private val policyPriority: Int = 1000,
    private val rtTables: RtTables = SystemRtTables,
) {

    /** Exposed for progress messages; the policy route is keyed off this CIDR. */
    val gimbalCidrForDebug: String get() = gimbalCidr

    // ---------- Bring-up / tear-down ---------------------------------------

    fun connectByProfile(profile: String, ifname: String) {
        require(profile.isNotBlank() && !profile.contains(' ')) {
            "Invalid NetworkManager profile name: '$profile'"
        }
        require(ifname.isNotBlank() && !ifname.contains(' ')) {
            "Invalid interface name: '$ifname'"
        }
        runner.run(listOf("nmcli", "connection", "up", profile, "ifname", ifname))
    }

    fun disconnectByProfile(profile: String) {
        // `nmcli connection down` is non-fatal if the profile isn't active.
        runCatching { runner.run(listOf("nmcli", "connection", "down", profile)) }
    }

    // ---------- Routing ----------------------------------------------------

    fun installPolicyRoute(ifname: String) {
        val tableId = ensureRoutingTable()
        val rules = runner.run(listOf("ip", "rule", "show"))
        if (!rules.lineSequence().any { it.contains("to $gimbalCidr") && it.contains("lookup $tableId") }) {
            runner.run(
                listOf("ip", "rule", "add", "to", gimbalCidr, "table", tableId, "priority", policyPriority.toString())
            )
        }
        val table = runner.run(listOf("ip", "route", "show", "table", tableId))
        if (!table.lineSequence().any { it.startsWith("$gimbalCidr ") && it.contains("dev $ifname") }) {
            runner.run(
                listOf("ip", "route", "add", gimbalCidr, "dev", ifname, "table", tableId)
            )
        }
        if (!table.lineSequence().any { it.startsWith("default ") && it.contains("dev $ifname") }) {
            // High-metric default via the same device, so NetworkManager can't ever
            // promote wlp8s0 to the system default while the rule is installed.
            runner.run(
                listOf("ip", "route", "add", "default", "dev", ifname, "metric", "10000", "table", tableId)
            )
        }
    }

    fun removePolicyRoute(ifname: String) {
        val tableId = tableIdFor(policyTableName)
        runCatching {
            runner.run(listOf("ip", "rule", "del", "to", gimbalCidr, "table", tableId, "priority", policyPriority.toString()))
        }
        runCatching {
            runner.run(listOf("ip", "route", "del", gimbalCidr, "dev", ifname, "table", tableId))
        }
        runCatching {
            runner.run(listOf("ip", "route", "del", "default", "dev", ifname, "metric", "10000", "table", tableId))
        }
    }

    private fun ensureRoutingTable(): String {
        val alias = tableIdFor(policyTableName)
        // Registering the name in rt_tables is purely cosmetic (so `ip route
        // show table polaris-wifi` works); every `ip rule`/`ip route` call
        // below uses the numeric id directly, so a permission failure here
        // (rt_tables is root-owned) must not abort the bridge.
        runCatching {
            val current = rtTables.readLines()
            if (current.none { it.trim().endsWith(" $policyTableName") || it.trim() == alias }) {
                rtTables.appendLine("$alias $policyTableName")
            }
        }
        return alias
    }

    private fun tableIdFor(name: String): String {
        val id = (1000 + (name.hashCode() and 0x7FFF) % 9000)
        return id.toString()
    }

    // ---------- Socket bind helpers ----------------------------------------

    /**
     * Returns the link-local IPv4 address assigned to [ifname], or null if the
     * link has no IPv4 yet (e.g. just before DHCP completes on a fresh
     * `nmcli connection up`). Callers should `awaitLinkUp` first.
     */
    fun localAddressFor(ifname: String): InetAddress? {
        val nic = NetworkInterface.getByName(ifname) ?: return null
        return nic.inetAddresses.asSequence()
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
    }

    /** Polls [linkIsUp] and the IPv4 presence until [timeoutMs] elapses. */
    open fun awaitLinkUp(ifname: String, timeoutMs: Int): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (linkIsUp(ifname) && localAddressFor(ifname) != null) return true
            Thread.sleep(250)
        }
        return linkIsUp(ifname) && localAddressFor(ifname) != null
    }

    /**
     * True only if the kernel reports the link as both `UP` and the sysfs
     * `operstate` is `up`. Both are checked because they can lag each other
     * by a fraction of a second on association.
     */
    fun linkIsUp(ifname: String): Boolean {
        val nic = NetworkInterface.getByName(ifname) ?: return false
        if (!nic.isUp) return false
        val operstate = runCatching {
            String(Files.readAllBytes(Paths.get("/sys/class/net/$ifname/operstate")), Charsets.UTF_8).trim()
        }.getOrDefault("unknown")
        return operstate == "up"
    }
}

/**
 * Tiny seam for the kernel's `/etc/iproute2/rt_tables` file. Production code uses
 * [SystemRtTables]; tests inject an in-memory fake so the bridge can be exercised
 * without root and without touching the real filesystem.
 */
interface RtTables {
    /** Existing lines in the rt_tables file, in order, with no trailing whitespace. */
    fun readLines(): List<String>
    /** Append a single "id name" line. */
    fun appendLine(line: String)
}

/** Default implementation: reads/writes the real `/etc/iproute2/rt_tables`. */
object SystemRtTables : RtTables {
    private val path = Paths.get("/etc/iproute2/rt_tables")
    override fun readLines(): List<String> {
        if (!Files.exists(path)) return emptyList()
        return Files.readAllLines(path, Charsets.UTF_8)
    }
    override fun appendLine(line: String) {
        Files.write(
            path,
            (readLines() + listOf(line)),
            Charsets.UTF_8,
        )
    }
}
