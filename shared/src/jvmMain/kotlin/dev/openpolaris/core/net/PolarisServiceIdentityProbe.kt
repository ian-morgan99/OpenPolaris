package dev.openpolaris.core.net

import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.ResponseParser
import dev.openpolaris.core.protocol.command
import java.net.InetSocketAddress
import java.net.Socket

fun interface PolarisServiceIdentityProbe {
    fun verify(host: String, port: Int): Result<String>
}

object SocketPolarisServiceIdentityProbe : PolarisServiceIdentityProbe {
    override fun verify(host: String, port: Int): Result<String> = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 2_000)
            socket.soTimeout = 3_000
            socket.getOutputStream().apply { write(command(Codes.PUSH_MODE_STATE)); flush() }
            val bytes = ByteArray(4096)
            val count = socket.getInputStream().read(bytes)
            require(count > 0) { "Polaris service returned no identity frame" }
            val frames = ResponseParser().parse(bytes.copyOf(count)).first
            require(frames.any { it.code == Codes.PUSH_MODE_STATE }) {
                "service did not return Polaris status code ${Codes.PUSH_MODE_STATE}"
            }
            "9090/${Codes.PUSH_MODE_STATE}"
        }
    }
}
