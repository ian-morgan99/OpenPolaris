package dev.openpolaris.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dev.openpolaris.core.net.BluetoothProbe
import dev.openpolaris.core.net.ProcessRunner

/**
 * Tests for the bridge tool's arg shape. We do not shell out in tests —
 * the real shells are exercised manually on the host.
 *
 * Note: `main(args)` is a thin wrapper that calls `kotlin.system.exitProcess`
 * with the return value of [runMain]. JVM 17 disables `SecurityManager` and
 * Kotlin 2.x dropped the `ExitProcessException` class, so we test the
 * testable seam — [runMain] — directly.
 */
class MainTest {

    @Test
    fun `usage message mentions all subcommands`() {
        val realOut = System.out
        val buf = java.io.ByteArrayOutputStream()
        try {
            System.setOut(java.io.PrintStream(buf))
            val code = runMain(arrayOf("--help"))
            assertEquals(0, code)
        } finally {
            System.setOut(realOut)
        }
        val text = buf.toString()
        assertTrue(text.contains("--probe"), "usage should mention --probe, got:\n$text")
        assertTrue(text.contains("--wake"), "usage should mention --wake")
        assertTrue(text.contains("--up"), "usage should mention --up")
        assertTrue(text.contains("--down"), "usage should mention --down")
        assertTrue(text.contains("--check"), "usage should mention --check")
    }

    @Test
    fun `runMain returns 2 when no mode is given`() {
        val realOut = System.out
        val buf = java.io.ByteArrayOutputStream()
        val realErr = System.err
        try {
            System.setOut(java.io.PrintStream(buf))
            System.setErr(java.io.PrintStream(java.io.ByteArrayOutputStream()))
            val code = runMain(emptyArray())
            assertEquals(2, code)
        } finally {
            System.setOut(realOut)
            System.setErr(realErr)
        }
    }

    @Test
    fun `runMain returns 2 for unknown argument`() {
        val realOut = System.out
        val buf = java.io.ByteArrayOutputStream()
        val realErr = System.err
        val errBuf = java.io.ByteArrayOutputStream()
        try {
            System.setOut(java.io.PrintStream(buf))
            System.setErr(java.io.PrintStream(errBuf))
            val code = runMain(arrayOf("--bogus"))
            assertEquals(2, code)
            assertTrue(errBuf.toString().contains("unknown argument"))
        } finally {
            System.setOut(realOut)
            System.setErr(realErr)
        }
    }

    @Test
    fun `--wake returns 1 with not-found message when no device is present`() {
        val realOut = System.out
        val buf = java.io.ByteArrayOutputStream()
        val realErr = System.err
        val errBuf = java.io.ByteArrayOutputStream()
        try {
            System.setOut(java.io.PrintStream(buf))
            System.setErr(java.io.PrintStream(errBuf))
            // On the test host there is no polaris-named BT device, so the
            // scan will return no rows. With the 0-ms settle default the
            // function returns quickly.
            val code = runMain(arrayOf("--wake"), noBluetoothDevices())
            assertEquals(1, code)
            val out = buf.toString()
            assertTrue(
                out.contains("no Polaris-named BT device known or found") ||
                    out.contains("\"ok\":false"),
                "expected not-found message, got: $out",
            )
        } finally {
            System.setOut(realOut)
            System.setErr(realErr)
        }
    }

    @Test
    fun `--wake with --json emits not-found JSON`() {
        val realOut = System.out
        val buf = java.io.ByteArrayOutputStream()
        val realErr = System.err
        val errBuf = java.io.ByteArrayOutputStream()
        try {
            System.setOut(java.io.PrintStream(buf))
            System.setErr(java.io.PrintStream(errBuf))
            val code = runMain(arrayOf("--wake", "--json"), noBluetoothDevices())
            assertEquals(1, code)
            val out = buf.toString()
            assertTrue(
                out.contains("\"ok\":false") && out.contains("\"err\""),
                "expected not-found JSON line, got: $out",
            )
        } finally {
            System.setOut(realOut)
            System.setErr(realErr)
        }
    }

    private fun noBluetoothDevices() = BluetoothProbe(runner = ProcessRunner { "" })

    /**
     * `--wake` must use the BlueZ cache before scanning. A scan cannot see the
     * mount while it is asleep (its BLE radio is down with the AP) or while it
     * is connected, which is exactly when a wake is wanted — so a scan-first
     * `--wake` could never wake anything.
     */
    @Test
    fun `--wake uses the BlueZ cache instead of requiring a scan hit`() {
        val calls = mutableListOf<List<String>>()
        val runner = ProcessRunner { args ->
            calls += args
            if (args == listOf("bluetoothctl", "devices")) "Device 48:E7:DA:D4:B5:72 polaris_d13e86\n" else ""
        }
        val realOut = System.out
        val buf = java.io.ByteArrayOutputStream()
        try {
            System.setOut(java.io.PrintStream(buf))
            val code = runMain(arrayOf("--wake", "--json"), BluetoothProbe(runner = runner, wakeSettleMs = 0))
            assertEquals(0, code, "a cached address must be enough to wake, got: $buf")
            assertTrue(buf.toString().contains("48:E7:DA:D4:B5:72"), "expected the cached address, got: $buf")
            assertTrue(
                calls.contains(listOf("bluetoothctl", "connect", "48:E7:DA:D4:B5:72")),
                "expected a GATT connect, got: $calls",
            )
            assertTrue(
                calls.none { "scan" in it },
                "a cache hit must not fall through to a scan: $calls",
            )
        } finally {
            System.setOut(realOut)
        }
    }
}
