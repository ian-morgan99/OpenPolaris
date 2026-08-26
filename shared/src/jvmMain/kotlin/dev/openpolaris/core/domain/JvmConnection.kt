package dev.openpolaris.core.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class JvmConnection : Connection {
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    override suspend fun connect(host: String, port: Int, timeoutMs: Int) {
        withContext(Dispatchers.IO) {
            val s = Socket()
            s.tcpNoDelay = true
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
