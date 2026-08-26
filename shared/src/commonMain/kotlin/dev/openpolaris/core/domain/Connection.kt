package dev.openpolaris.core.domain

/** Platform-neutral transport. Each target provides an `actual` (socket, NWConnection, etc.). */
interface Connection {
    /** Blocks/ suspends until connected or throws. */
    suspend fun connect(host: String, port: Int, timeoutMs: Int)

    /** Send raw bytes; suspends until written. */
    suspend fun write(data: ByteArray)

    /** Suspend until bytes arrive; returns 0 on clean close, -1 on timeout. */
    suspend fun read(buffer: ByteArray, timeoutMs: Int): Int

    fun close()
}
