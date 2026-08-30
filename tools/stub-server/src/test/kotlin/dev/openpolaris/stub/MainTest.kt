package dev.openpolaris.stub

import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.command
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the standalone gimbal stub. The server runs on an ephemeral
 * port (0) so it never conflicts with anything else on the host.
 *
 * The server's accept loop runs forever, so we run it on a daemon thread
 * and shut it down by ending the test (the daemon thread dies with the JVM).
 */
class MainTest {

    @Test
    fun `argValue parses --port and --bind in any order`() {
        assertEquals("9090", argValue(arrayOf("--port", "9090", "--bind", "0.0.0.0"), "--port"))
        assertEquals("127.0.0.1", argValue(arrayOf("--bind", "127.0.0.1", "--port", "9090"), "--bind"))
        assertEquals(null, argValue(arrayOf("--port", "9090"), "--bind"))
        assertEquals(null, argValue(arrayOf("--port"), "--port"))
    }

    @Test
    fun `parseArgs rejects non-integer --port with usage error`() {
        val r = parseArgs(arrayOf("--port", "notanumber"))
        assertTrue(r is ParseResult.Usage, "expected Usage, got $r")
        val msg = (r as ParseResult.Usage).message
        assertTrue(msg.contains("--port"), "usage should mention --port, got: $msg")
    }

    @Test
    fun `parseArgs defaults bind to 0_0_0_0 and port to 9090`() {
        val r = parseArgs(emptyArray()) as ParseResult.Startup
        assertEquals("0.0.0.0", r.bind)
        assertEquals(9090, r.port)
    }

    @Test
    fun `parseArgs accepts explicit bind and port`() {
        val r = parseArgs(arrayOf("--bind", "127.0.0.1", "--port", "9091")) as ParseResult.Startup
        assertEquals("127.0.0.1", r.bind)
        assertEquals(9091, r.port)
    }

    @Test
    fun `stub stays silent on unframed heartbeat byte`() {
        // Documented contract: the stub only emits responses for #-terminated frames.
        // A lone 'h' (heartbeat byte without terminator) is buffered but never answered,
        // because the protocol parser waits for a complete frame. This proves the stub
        // doesn't crash and doesn't emit partial/garbage responses when fed non-frames.
        val port = startStub()
        Socket("127.0.0.1", port).use { s ->
            s.soTimeout = 300
            s.getOutputStream().write('h'.code)
            s.getOutputStream().flush()
            val out = readFrames(s.getInputStream(), expectFrames = 0, maxBytes = 64)
            assertEquals("", out, "stub should stay silent on a non-framed heartbeat byte")
        }
    }

    @Test
    fun `stub returns a SimulatedProtocol frame for EX_AXIS_STA (524)`() {
        val port = startStub()
        Socket("127.0.0.1", port).use { s ->
            s.soTimeout = 2000
            // 524 = EX_AXIS_STA — SimulatedProtocol responds with state+angle
            s.getOutputStream().write(command(Codes.EX_AXIS_STA))
            s.getOutputStream().flush()
            val out = readFrames(s.getInputStream(), expectFrames = 1)
            assertTrue(out.startsWith("1&524&2&"), "expected framed 524 response, got: $out")
            assertTrue(out.contains("state:"), "expected state: field, got: $out")
            assertTrue(out.contains("angle:"), "expected angle: field, got: $out")
        }
    }

    @Test
    fun `stub handles a burst of independent codes on one connection`() {
        val port = startStub()
        Socket("127.0.0.1", port).use { s ->
            s.soTimeout = 2000
            val out = s.getOutputStream()
            for (c in intArrayOf(Codes.EX_AXIS_STA, Codes.GET_WIFI_BAND, Codes.OMS_RUN_STATE)) {
                out.write(command(c))
                out.flush()
            }
            val collected = readFrames(s.getInputStream(), expectFrames = 3, maxBytes = 4096)
            val hashes = collected.count { it == '#' }
            assertTrue(hashes >= 3, "expected ≥3 frames in burst, got $hashes in:\n$collected")
            assertTrue(collected.contains("1&524"), "missing 524 frame in: $collected")
            assertTrue(collected.contains("1&802"), "missing 802 frame in: $collected")
            assertTrue(collected.contains("1&824"), "missing 824 frame in: $collected")
        }
    }

    @Test
    fun `stub accepts multiple sequential clients`() {
        val port = startStub()
        repeat(3) { i ->
            Socket("127.0.0.1", port).use { s ->
                s.soTimeout = 2000
                s.getOutputStream().write(command(Codes.EX_AXIS_STA))
                s.getOutputStream().flush()
                val out = readFrames(s.getInputStream(), expectFrames = 1)
                assertTrue(out.startsWith("1&524&2&"), "client $i: expected framed 524 response, got: $out")
            }
        }
    }

    // -- helpers --

    /**
     * Start the stub on an ephemeral port. Returns the bound port. The server
     * runs on a daemon thread so it does not block JVM shutdown.
     */
    private fun startStub(): Int {
        val server = ServerSocket(0)
        val port = server.localPort
        server.close() // free the port; runServer will rebind it
        val t = Thread({ runServer("127.0.0.1", port) }, "stub-test-server").apply {
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

    /**
     * Read from [stream] until [expectFrames] frames have been terminated
     * (i.e. a '#' has been seen for each) or the read times out.
     */
    private fun readFrames(
        stream: java.io.InputStream,
        expectFrames: Int,
        maxBytes: Int = 256,
    ): String {
        val buf = ByteArray(1)
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + 2000
        while (sb.length < maxBytes && sb.count { it == '#' } < expectFrames) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            try {
                val n = stream.read(buf, 0, 1)
                if (n < 0) break
                sb.append(buf[0].toInt().toChar())
            } catch (_: java.net.SocketTimeoutException) {
                break
            }
        }
        return sb.toString()
    }
}
