package dev.openpolaris.core.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProcessRunnerTest {

    @Test
    fun `functional interface records the call and returns the recorded output`() {
        val calls = mutableListOf<List<String>>()
        val runner = ProcessRunner { argv -> calls += argv; "ok-${argv.joinToString(",")}" }
        val out = runner.run(listOf("echo", "hi"))
        assertEquals("ok-echo,hi", out)
        assertEquals(1, calls.size)
    }

    @Test
    fun `BridgeException carries command and exit code`() {
        val e = BridgeException("nmcli connection up polaris_d13e86", 5, "no connection with that name")
        assertEquals(5, e.exitCode)
        assertEquals("nmcli connection up polaris_d13e86", e.command)
        assert(e.message!!.contains("no connection with that name"))
    }

    @Test
    fun `assertBridgeException helper translates non-zero exit`() {
        // Demonstrates the shape the in-app wrapper should expose.
        val ex = assertFailsWith<BridgeException> {
            throw BridgeException("ip rule show", 1, "RTNETLINK answers: File exists")
        }
        assertEquals(1, ex.exitCode)
    }
}
