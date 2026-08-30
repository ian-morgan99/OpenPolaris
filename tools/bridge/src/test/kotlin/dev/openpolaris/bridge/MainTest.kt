package dev.openpolaris.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
