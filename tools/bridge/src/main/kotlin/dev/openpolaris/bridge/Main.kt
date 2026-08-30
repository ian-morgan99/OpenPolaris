package dev.openpolaris.bridge

import dev.openpolaris.core.net.BluetoothProbe
import dev.openpolaris.core.net.WifiBridge

/**
 * Headless CLI for the BT → WiFi bridge flow.
 *
 * Subcommands:
 *   --probe           One-shot BT LE scan for a Polaris-named device.
 *   --wake            Pulse BT GATT connect to wake the gimbal's WiFi AP.
 *   --up <profile>    Bring the saved NM profile up, await link, install policy route.
 *   --down <profile>  Tear the policy route down, drop the NM profile.
 *   --check           Print which interface has the gimbal route (LAN must NOT).
 *   --json            Emit machine-readable JSON lines (default: human-readable).
 *
 * No arguments: print usage and exit 2.
 *
 * Examples:
 *   tools/bridge --probe
 *   tools/bridge --wake
 *   tools/bridge --up polaris_d13e86 --ifname wlp8s0
 *   tools/bridge --down polaris_d13e86 --ifname wlp8s0
 *   tools/bridge --check
 */
fun main(args: Array<String>) {
    val code = runMain(args)
    kotlin.system.exitProcess(code)
}

/**
 * Run the CLI. Returns the process exit code (0 success, 1 device-not-found,
 * 2 user/argument error). Separated from [main] so unit tests can assert the
 * code without touching JVM SecurityManager.
 */
fun runMain(args: Array<String>): Int {
    var mode: String? = null
    var profile: String? = null
    var ifname: String? = "wlp8s0"
    var json = false
    var i = 0
    while (i < args.size) {
        when (val a = args[i]) {
            "--probe", "--up", "--down", "--check", "--wake" -> mode = a.removePrefix("--")
            "--profile" -> { profile = args.getOrNull(++i) }
            "--ifname" -> { ifname = args.getOrNull(++i) }
            "--json" -> json = true
            "-h", "--help" -> { usage(); return 0 }
            else -> return err("unknown argument: $a")
        }
        i++
    }
    val m = mode ?: run { usage(); return 2 }
    val printer: (String) -> Unit = { if (json) println("{\"ok\":true,\"msg\":\"$it\"}") else println(it) }
    val bridge = WifiBridge()
    val bt = BluetoothProbe()
    return when (m) {
        "probe" -> {
            val dev = bt.discover()
            if (dev == null) {
                if (json) println("{\"ok\":false,\"err\":\"no device matched\"}")
                else println("no Polaris-named BT device found")
                1
            } else {
                if (json) println("""{"ok":true,"address":"${dev.address}","name":"${dev.name}"}""")
                else println("found ${dev.name} (${dev.address})")
                0
            }
        }
        "wake" -> {
            val dev = bt.discover()
            if (dev == null) {
                if (json) println("{\"ok\":false,\"err\":\"no device matched\"}")
                else println("no Polaris-named BT device found — nothing to wake")
                1
            } else {
                if (!json) println("found ${dev.name} (${dev.address}); pulsing BT wake…")
                bt.wake(dev)
                if (json) println("""{"ok":true,"address":"${dev.address}","name":"${dev.name}","msg":"woke"}""")
                else println("woke ${dev.name} (${dev.address}); AP should be up in ~2s")
                0
            }
        }
        "up" -> {
            val p = profile ?: return err("--up requires --profile <name>")
            val n = ifname ?: return err("--up requires --ifname <name>")
            printer("bringing $p up on $n")
            bridge.connectByProfile(p, n)
            bridge.awaitLinkUp(n, timeoutMs = 15000)
            bridge.installPolicyRoute(n)
            printer("policy route installed for ${"192.168.0.0/24"} via $n")
            0
        }
        "down" -> {
            val p = profile ?: return err("--down requires --profile <name>")
            val n = ifname ?: return err("--down requires --ifname <name>")
            bridge.removePolicyRoute(n)
            bridge.disconnectByProfile(p)
            printer("policy route removed and $p brought down")
            0
        }
        "check" -> {
            val addr = bridge.localAddressFor(ifname!!)
            val policyRoute = ProcessBuilder("ip", "route", "show", "table", "polaris-wifi")
                .redirectErrorStream(true).start().inputStream.bufferedReader().readText()
            if (json) {
                println("""{"ok":true,"ifname":"$ifname","link_local":${addr?.hostAddress?.let { "\"$it\"" } ?: "null"},"policy_route":${policyRoute.trim().isNotEmpty()}}""")
            } else {
                println("ifname=$ifname link_local=${addr?.hostAddress ?: "(none)"}")
                println("polaris-wifi table:")
                println(policyRoute.trim().ifEmpty { "  (empty)" })
            }
            0
        }
        else -> err("unknown mode: $m")
    }
}

private fun usage() {
    println(
        """
        |usage: bridge --probe | --wake | --up --profile <p> [--ifname <n>] | --down --profile <p> [--ifname <n>] | --check
        |
        |  --probe   one-shot BT scan for a Polaris device
        |  --wake    pulse BT GATT connect to wake the gimbal's AP, then drop the link
        |  --up      bring saved NM profile up, install policy route
        |  --down    tear policy route down, drop profile
        |  --check   show gimbal link address and policy table
        |  --json    emit machine-readable JSON lines
        |
        |default ifname is wlp8s0.
        """.trimMargin()
    )
}

private fun err(msg: String): Int {
    System.err.println("error: $msg")
    return 2
}
