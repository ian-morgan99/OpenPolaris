package dev.openpolaris.probe

import dev.openpolaris.core.protocol.Codes
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmokeSafetyTest {
    @Test
    fun `read-only smoke never sends the capture frame and completes under push traffic`() {
        val received = Collections.synchronizedList(mutableListOf<String>())
        ServerSocket(0).use { server ->
            server.soTimeout = 2_000
            val worker = thread(start = true) {
                server.accept().use { client ->
                    client.soTimeout = 100
                    val input = client.getInputStream()
                    val output = client.getOutputStream()
                    val pending = StringBuilder()
                    val buffer = ByteArray(4096)
                    while (!client.isClosed) {
                        try {
                            val count = input.read(buffer)
                            if (count < 0) break
                            pending.append(String(buffer, 0, count, Charsets.US_ASCII))
                            while ('#' in pending) {
                                val end = pending.indexOf("#")
                                val request = pending.substring(0, end + 1)
                                pending.delete(0, end + 1)
                                received += request
                                val code = request.substringAfter('&').substringBefore('&')
                                // Include unrelated push traffic before the correlated reply.
                                output.write("1&524&2&x:0;#1&$code&2&state:0;#".toByteArray(Charsets.US_ASCII))
                                output.flush()
                            }
                        } catch (_: SocketTimeoutException) {
                            output.write("1&524&2&x:0;#".toByteArray(Charsets.US_ASCII))
                            output.flush()
                        }
                    }
                }
            }

            runSmoke("127.0.0.1", server.localPort, destructive = false, allowCapture = false, responseWindowMs = 10)
            worker.join(2_000)
            assertFalse(worker.isAlive, "smoke must close its socket and terminate")
        }

        val shutter = "1&${Codes.CAM_CAPTURE}&${Codes.CAM_CAPTURE_SUBTYPE}&${Codes.CAM_CAPTURE_PAYLOAD}#"
        assertFalse(received.contains(shutter), "read-only smoke sent the shutter frame: $shutter")
        assertTrue(received.isNotEmpty(), "test server should observe ordinary read probes")
    }
}
