package dev.openpolaris.probe

import dev.openpolaris.stub.runServer
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BurstTest {

    // ---- parseBurstArgs ----

    @Test
    fun `parseBurstArgs defaults host port and codes`() {
        val a = parseBurstArgs(emptyArray())
        assertEquals("192.168.0.1", a.host)
        assertEquals(9090, a.port)
        assertEquals(listOf(524, 544, 802, 824, 775, 778, 779), a.codes)
    }

    @Test
    fun `parseBurstArgs parses explicit host and port`() {
        val a = parseBurstArgs(arrayOf("10.0.0.5", "1234"))
        assertEquals("10.0.0.5", a.host)
        assertEquals(1234, a.port)
        // codes still default
        assertEquals(listOf(524, 544, 802, 824, 775, 778, 779), a.codes)
    }

    @Test
    fun `parseBurstArgs parses a custom code list with whitespace`() {
        val a = parseBurstArgs(arrayOf("h", "1", "524, 802 ,825"))
        assertEquals(listOf(524, 802, 825), a.codes)
    }

    @Test
    fun `parseBurstArgs treats a non-integer port as default 9090`() {
        val a = parseBurstArgs(arrayOf("h", "notanumber"))
        assertEquals(9090, a.port)
    }

    @Test
    fun `parseBurstArgs --full uses the canonical pre-camera burst codes`() {
        val a = parseBurstArgs(arrayOf("h", "1", "--full"))
        assertTrue(a.full, "expected full=true when --full is passed")
        // 9 codes in the canonical order: 808, 809, 802, 778, 779, 775, 824, 524, 543
        assertEquals(
            listOf(808, 809, 802, 778, 779, 775, 824, 524, 543),
            a.codes,
        )
    }

    // ---- runBurst: error path ----

    @Test
    fun `runBurst emits an error line when no server is listening`() {
        // Find a definitely-free port by binding then closing.
        val ss = ServerSocket(0); val freePort = ss.localPort; ss.close()
        val lines = runBurst(BurstArgs("127.0.0.1", freePort, codes = listOf(524)), sink = {})
        assertTrue(
            lines.any { it.startsWith("  <error:") },
            "expected an <error:...> line, got:\n${lines.joinToString("\n")}"
        )
        // The header line should still be emitted.
        assertTrue(
            lines.first().startsWith("burst → 127.0.0.1:$freePort codes="),
            "expected header line, got: ${lines.first()}"
        )
    }

    // ---- runBurst: integration with stub-server ----

    @Test
    fun `runBurst against the stub receives framed responses for query codes`() {
        val port = startStubServer()
        val lines = runBurst(
            BurstArgs("127.0.0.1", port, codes = listOf(524, 802, 824)),
            sink = {},
        )
        // Each code should produce: sent, drained (with a frame), parsed
        val parsed = lines.filter { it.startsWith("  code=") }
        assertEquals(3, parsed.size, "expected 3 parsed lines, got:\n${lines.joinToString("\n")}")
        assertTrue(parsed.any { it.contains("code=524") && it.contains("state=") }, "missing 524: $parsed")
        assertTrue(parsed.any { it.contains("code=802") && it.contains("band=") }, "missing 802: $parsed")
        assertTrue(parsed.any { it.contains("code=824") && it.contains("running=") }, "missing 824: $parsed")
    }

    // -- helpers --

    /**
     * Start the stub-server on an ephemeral port, returning the bound port.
     * The listener thread is a daemon so it won't block JVM shutdown.
     */
    private fun startStubServer(): Int {
        val ss = ServerSocket(0); val port = ss.localPort; ss.close()
        val t = Thread({ runServer("127.0.0.1", port) }, "cli-probe-test-stub").apply {
            isDaemon = true
        }
        t.start()
        // Wait for the listener to come up — retry connect until success or timeout.
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", port), 100) }
                return port
            } catch (_: Exception) {
                Thread.sleep(20)
            }
        }
        error("stub server did not start on port $port within 3s")
    }
}
