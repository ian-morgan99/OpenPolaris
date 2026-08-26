package dev.openpolaris.probe

import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.ResponseParser
import dev.openpolaris.core.protocol.command
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.exitProcess

/**
 * cli-probe v0: connect to the mount, poll status (284), print parsed response.
 * Usage: probe [host] [port]   (defaults 192.168.0.1:9090)
 */
fun main(args: Array<String>) {
    val host = args.getOrElse(0) { "192.168.0.1" }
    val port = args.getOrElse(1) { "9090" }.toIntOrNull() ?: 9090

    println("Open Polaris probe → $host:$port")
    val socket = Socket()
    try {
        socket.connect(InetSocketAddress(host, port), 5000)
        socket.soTimeout = 3000
        println("connected.")

        socket.getOutputStream().apply {
            write(command(Codes.PUSH_MODE_STATE))
            flush()
        }
        val buf = ByteArray(4096)
        val n = socket.getInputStream().read(buf)
        if (n <= 0) {
            println("no response")
            exitProcess(2)
        }
        val parser = ResponseParser()
        val (frames, _) = parser.parse(buf.copyOf(n))
        if (frames.isEmpty()) {
            println("unparsed: ${String(buf, 0, n, Charsets.US_ASCII)}")
        } else {
            frames.forEach { f ->
                println("code=${f.code}")
                f.fields.forEach { (k, v) -> println("  $k = $v") }
            }
        }
    } catch (e: Exception) {
        System.err.println("error: ${e.message}")
        exitProcess(1)
    } finally {
        socket.close()
    }
}
