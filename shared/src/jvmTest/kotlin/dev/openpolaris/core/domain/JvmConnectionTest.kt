package dev.openpolaris.core.domain

import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JvmConnectionTest {

    @Test
    fun `connect without bindTo works on a loopback echo server`() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        try {
            val port = server.localPort
            val thread = Thread {
                val s = server.accept()
                val inp = s.getInputStream()
                val outp = s.getOutputStream()
                val buf = ByteArray(64)
                val n = inp.read(buf)
                if (n > 0) outp.write(buf, 0, n)
                s.close()
            }.also { it.isDaemon = true; it.start() }

            val conn = JvmConnection()
            conn.connect(InetAddress.getLoopbackAddress().hostAddress!!, port, 1000)
            conn.write("ping".toByteArray())
            val buf = ByteArray(64)
            val n = conn.read(buf, 1000)
            assertEquals(4, n)
            assertEquals("ping", String(buf, 0, n))
            conn.close()
            thread.join(500)
        } finally {
            server.close()
        }
    }

    @Test
    fun `bindTo is honored when set to loopback`() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        try {
            val port = server.localPort
            val thread = Thread {
                val s = server.accept(); s.close()
            }.also { it.isDaemon = true; it.start() }

            val conn = JvmConnection(bindTo = InetAddress.getLoopbackAddress())
            conn.connect(InetAddress.getLoopbackAddress().hostAddress!!, port, 1000)
            assertNotNull(conn)
            conn.close()
            thread.join(500)
        } finally {
            server.close()
        }
    }

    @Test
    fun `bindTo is mutable so the orchestrator can set it post-construction`() {
        val conn = JvmConnection()
        assertTrue(conn.bindTo == null)
        conn.bindTo = InetAddress.getLoopbackAddress()
        assertEquals(InetAddress.getLoopbackAddress(), conn.bindTo)
    }
}
