package dev.openpolaris.core.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class JvmConnection(
    /**
     * When non-null, every [connect] call binds the socket to this local
     * address before dialing. Used to anchor the connection to a dedicated
     * Wi-Fi interface (e.g. `wlp8s0`) so the gimbal traffic never leaves
     * via the LAN NIC. Leave null on platforms that don't expose a NIC
     * binding (e.g. mobile, CI).
     */
    @Volatile var bindTo: java.net.InetAddress? = null,
) : Connection {
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    override suspend fun connect(host: String, port: Int, timeoutMs: Int) {
        withContext(Dispatchers.IO) {
            val s = Socket()
            s.tcpNoDelay = true
            bindTo?.let { s.bind(InetSocketAddress(it, 0)) }
            s.connect(InetSocketAddress(host, port), timeoutMs)
            socket = s
            input = s.getInputStream()
            output = s.getOutputStream()
        }
    }

    override suspend fun write(data: ByteArray): Unit = withContext(Dispatchers.IO) {
        output?.write(data)
        output?.flush()
    }

    override suspend fun read(buffer: ByteArray, timeoutMs: Int): Int =
        withContext(Dispatchers.IO) {
            val inp = input ?: return@withContext -1
            if (inp.available() == 0) socket?.soTimeout = timeoutMs
            try {
                inp.read(buffer, 0, buffer.size)
            } catch (_: java.net.SocketTimeoutException) {
                -1
            }
        }

    override fun close() {
        runCatching { socket?.close() }
        socket = null; input = null; output = null
    }
}
