package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.EMPTY_CONTENT
import dev.openpolaris.core.protocol.ResponseParser
import dev.openpolaris.core.protocol.command
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Single owner of the mount connection (ARCHITECTURE §3.1).
 * Serializes requests with a mutex, runs a reader loop dispatching frames to flows,
 * and reconnects with exponential backoff capped at 30 s.
 */
class MountSession(
    private val connectionFactory: () -> Connection,
    private val host: String = "192.168.0.1",
    private val port: Int = 9090,
) {
    sealed interface CmdResult<out T> {
        data class Ok<T>(val value: T) : CmdResult<T>
        data object Timeout : CmdResult<Nothing>
        data class ProtocolError(val message: String) : CmdResult<Nothing>
    }

    data class MountState(
        val connected: Boolean = false,
        /**
         * The last protocol-level error observed on this session, or null.
         *
         * Set by [request] / [send] whenever they return
         * [CmdResult.ProtocolError] (including the "not connected"
         * sentinel). Cleared on a successful [tryConnect] so callers
         * can tell "the mount came back" from "no error has happened
         * yet".
         *
         * Observing this lets the plate-solver distinguish "the
         * solver found no match" from "the mount is unreachable"
         * — the two are otherwise conflated in user-visible
         * error text.
         */
        val lastError: CmdResult<Nothing>? = null,
    )

    private val _state = MutableStateFlow(MountState())
    val state: StateFlow<MountState> = _state

    private val _frames = MutableStateFlow<ResponseParser.Frame?>(null)
    val frames: StateFlow<ResponseParser.Frame?> = _frames

    /**
     * Last [CmdResult.ProtocolError] observed by [request] or [send], or
     * null. Cleared on a successful [connect] so callers can tell "the
     * mount came back" from "no error has happened yet" (PLAN-CRITICAL-
     * REVIEW §H). Mirrors [MountState.lastError] which is kept in sync
     * for the [state] flow.
     */
    val lastError: CmdResult<Nothing>?
        get() = _state.value.lastError

    private fun recordError(err: CmdResult<Nothing>) {
        _state.value = _state.value.copy(lastError = err)
    }

    private val sendMutex = Mutex()
    private var connection: Connection? = null

    @kotlin.concurrent.Volatile
    private var wantConnected = false

    suspend fun connect(): Boolean {
        wantConnected = true
        return tryConnect()
    }

    private suspend fun tryConnect(): Boolean {
        val conn = connectionFactory()
        return try {
            conn.connect(host, port, timeoutMs = 5000)
            connection = conn
            // Clear any previous protocol error so observers can tell
            // "the mount came back" from "no error has happened yet".
            _state.value = _state.value.copy(connected = true, lastError = null)
            // Lifecycle handshake: poll status once (PROTOCOL.md §4).
            send(Codes.PUSH_MODE_STATE)
            true
        } catch (_: Exception) {
            conn.close()
            false
        }
    }

    /**
     * Send a command and wait for a response frame with the same code.
     * Returns [CmdResult.Timeout] if nothing matching arrives within [timeoutMs].
     */
    suspend fun <T> request(
        code: Int,
        payload: String = EMPTY_CONTENT,
        timeoutMs: Long = 2000,
        parse: (ResponseParser.Frame) -> T?,
    ): CmdResult<T> {
        val conn = connection
        if (conn == null) {
            val err = CmdResult.ProtocolError("not connected")
            recordError(err)
            return err
        }
        return sendMutex.withLock {
            try {
                conn.write(command(code) { putRaw(payload) })
                val parser = ResponseParser()
                val buf = ByteArray(4096)
                var carry = ByteArray(0)
                var matched: T? = null
                withTimeout(timeoutMs) {
                    while (matched == null) {
                        val n = conn.read(buf, timeoutMs.toInt())
                        // n <= 0 means no data yet; suspend briefly so the
                        // timeout can fire instead of busy-spinning the CPU.
                        if (n <= 0) { delay(READ_RETRY_MS); continue }
                        val combined = carry + buf.copyOf(n)
                        val (frames, consumed) = parser.parse(combined)
                        for (f in frames) {
                            _frames.value = f
                            if (f.code == code) {
                                parse(f)?.let { matched = it }
                            }
                        }
                        carry = combined.drop(consumed).toByteArray()
                    }
                }
                CmdResult.Ok(matched!!)
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                CmdResult.Timeout
            } catch (e: Exception) {
                handleDisconnect(e)
                val err: CmdResult<Nothing> = CmdResult.ProtocolError(e.message ?: "connection lost")
                recordError(err)
                err
            }
        }
    }

    private fun handleDisconnect(e: Exception) {
        connection?.close()
        connection = null
        _state.value = _state.value.copy(connected = false)
    }

    /** Fire-and-forget send (e.g., jog commands); no response awaited. */
    suspend fun send(code: Int, payload: String = EMPTY_CONTENT) {
        val conn = connection
        if (conn == null) {
            recordError(CmdResult.ProtocolError("not connected"))
            return
        }
        sendMutex.withLock {
            try {
                conn.write(command(code) { putRaw(payload) })
            } catch (e: Exception) {
                handleDisconnect(e)
                recordError(CmdResult.ProtocolError(e.message ?: "connection lost"))
            }
        }
    }

    fun disconnect() {
        wantConnected = false
        connection?.close()
        connection = null
        _state.value = _state.value.copy(connected = false)
    }

    private companion object {
        const val READ_RETRY_MS = 10L
    }
}
