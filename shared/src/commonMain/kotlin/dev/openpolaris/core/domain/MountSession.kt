package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.EMPTY_CONTENT
import dev.openpolaris.core.protocol.ResponseParser
import dev.openpolaris.core.protocol.command
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Single owner of the mount connection (ARCHITECTURE §3.1).
 *
 * **Demux model** (PLAN-CRITICAL-REVIEW §F, issue #6): a single background
 * [runReaderLoop] coroutine owns the socket read. It parses every incoming
 * frame and:
 *
 * 1. Publishes the frame to [frames] so any subscriber (e.g. an AutoLevel
 *    flow consuming 538 tilt pushes) sees it.
 * 2. Completes the [pending] waiter registered for [ResponseParser.Frame.code]
 *    — if any.
 *
 * [request] therefore becomes "register a one-shot waiter for `code`,
 * write the command, await the waiter". The mutex serialises writes, the
 * reader does all reads, and unsolicited push frames (538, future 517
 * events) do not have to be polled.
 *
 * The reader is started automatically by [connect] and torn down by
 * [disconnect] / [handleDisconnect].
 *
 * The reader's [CoroutineScope] is injected so tests can pass the
 * [kotlinx.coroutines.test.TestScope] (which uses virtual time) instead
 * of a real [Dispatchers.Default] scope — otherwise the reader would
 * busy-spin real time during a test and the suite would never finish.
 */
class MountSession(
    private val connectionFactory: () -> Connection,
    private val host: String = "192.168.0.1",
    private val port: Int = 9090,
    private val readerScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    sealed interface CmdResult<out T> {
        data class Ok<T>(val value: T) : CmdResult<T>
        data object Timeout : CmdResult<Nothing>
        data class ProtocolError(val message: String) : CmdResult<Nothing>
    }

    private val _state = MutableStateFlow(MountState())
    val state: StateFlow<MountState> = _state

    private val _frames = MutableStateFlow<ResponseParser.Frame?>(null)
    val frames: StateFlow<ResponseParser.Frame?> = _frames

    /**
     * Test-only seam: directly publish a frame on [frames] without going
     * through the reader loop. The real reader loop drives this from the
     * socket, but tests need to inject frames deterministically to cover
     * the 517/538 demux (PLAN-CRITICAL-REVIEW §F). Marked `internal` so
     * production callers cannot accidentally rely on it.
     */
    internal fun publishFrameForTest(f: ResponseParser.Frame) {
        _frames.value = f
    }

    /**
     * Last [CmdResult.ProtocolError] observed by [request] or [send], or
     * null. Cleared on a successful [connect] so callers can tell "the
     * mount came back" from "no error has happened yet" (PLAN-CRITICAL-
     * REVIEW §H). Synthesised from [MountState.lastErrorMessage] on the
     * shared [state] flow so the typed API matches the spec (Stream 8.1)
     * without giving the top-level [MountState] a compile-time
     * dependency on [MountSession.CmdResult].
     */
    val lastError: CmdResult<Nothing>?
        get() = _state.value.lastErrorMessage?.let { CmdResult.ProtocolError(it) }

    private fun recordError(err: CmdResult<Nothing>) {
        val msg = (err as? CmdResult.ProtocolError)?.message
        _state.value = _state.value.copy(lastErrorMessage = msg)
    }

    private val sendMutex = Mutex()
    private var connection: Connection? = null

    @kotlin.concurrent.Volatile
    private var wantConnected = false

    /**
     * Pending request waiters keyed by response [ResponseParser.Frame.code].
     * Guarded by the [pending] instance monitor (O(1) ops, no coroutine
     * mutex overhead).
     */
    private val pending = mutableMapOf<Int, CompletableDeferred<ResponseParser.Frame>>()

    /**
     * The background reader coroutine. Null before [startReader] is
     * called, after [stopReader] / [handleDisconnect], or if the launch
     * failed to start.
     */
    private var readerJob: Job? = null

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
            _state.value = _state.value.copy(connected = true, lastErrorMessage = null)
            // Start the demux reader before issuing the handshake so
            // the 284 response is dispatched to the waiter's deferred
            // instead of being dropped on the floor.
            startReader()
            // Lifecycle handshake: confirm the mount responds at all
            // (PROTOCOL.md §4). Routes through the new demux path.
            val handshake = request<ResponseParser.Frame>(
                code = Codes.PUSH_MODE_STATE,
                timeoutMs = 2000L,
            ) { it }
            if (handshake !is CmdResult.Ok) {
                throw java.io.IOException("handshake failed: $handshake")
            }
            true
        } catch (e: Exception) {
            stopReader()
            conn.close()
            connection = null
            _state.value = _state.value.copy(connected = false)
            false
        }
    }

    /**
     * Launch [runReaderLoop] in [readerScope] if it isn't already running.
     * Idempotent: a second call while the loop is alive is a no-op.
     */
    private fun startReader() {
        if (readerJob?.isActive == true) return
        readerJob = readerScope.launch {
            runReaderLoop()
        }
    }

    /**
     * Cancel the reader coroutine and wait for it to wind down. Does not
     * touch [connection] — that's [handleDisconnect]'s job.
     */
    private fun stopReader() {
        readerJob?.cancel()
        readerJob = null
    }

    /**
     * The single owner of [Connection.read]. Parses every frame, publishes
     * it to [frames], and completes any matching [pending] waiter.
     *
     * Exits when the connection is closed, the reader job is cancelled, or
     * the socket raises. On exit, all waiters are failed with the cause
     * so in-flight [request]s unblock instead of timing out.
     */
    private suspend fun runReaderLoop() {
        val conn = connection ?: return
        val parser = ResponseParser()
        val buf = ByteArray(4096)
        var carry = ByteArray(0)
        try {
            coroutineScope {
                while (isActive) {
                    val n = try {
                        conn.read(buf, READ_TIMEOUT_MS.toInt())
                    } catch (e: Exception) {
                        throw e
                    }
                    if (n <= 0) {
                        // 0 = clean close, -1 = timeout. Either way, yield so
                        // the scheduler can deliver cancels.
                        if (n == 0) break
                        delay(READ_RETRY_MS)
                        continue
                    }
                    val combined = carry + buf.copyOf(n)
                    val (frames, consumed) = parser.parse(combined)
                    carry = combined.drop(consumed).toByteArray()
                    for (f in frames) {
                        _frames.value = f
                        val waiter = synchronized(pending) { pending.remove(f.code) }
                        if (waiter != null) {
                            waiter.complete(f)
                        }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Re-throw to honour structured concurrency. We have nothing
            // to do for waiters here — the disconnect path that triggered
            // the cancel will fail them.
            throw e
        } catch (e: Exception) {
            handleDisconnect(e)
        }
    }

    /**
     * Send a command and wait for a response frame with the same code.
     * Returns [CmdResult.Timeout] if nothing matching arrives within [timeoutMs].
     *
     * The reader loop dispatches the matching frame to the waiter
     * registered here. If the socket drops, the reader's [handleDisconnect]
     * will fail this waiter with an IOException so the caller doesn't
     * have to wait the full timeout.
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
        val waiter = CompletableDeferred<ResponseParser.Frame>()
        synchronized(pending) { pending[code] = waiter }
        return try {
            sendMutex.withLock {
                try {
                    conn.write(command(code) { putRaw(payload) })
                } catch (e: Exception) {
                    synchronized(pending) { pending.remove(code) }
                    throw e
                }
            }
            val frame = try {
                withTimeout(timeoutMs) { waiter.await() }
            } catch (_: TimeoutCancellationException) {
                // Defensive: the reader should have removed/completed the
                // waiter, but a slow response could still time out here.
                synchronized(pending) { pending.remove(code) }
                return CmdResult.Timeout
            }
            val parsed = parse(frame)
            if (parsed == null) {
                val err = CmdResult.ProtocolError("parser returned null for code $code")
                recordError(err)
                err
            } else {
                CmdResult.Ok(parsed)
            }
        } catch (e: Exception) {
            handleDisconnect(e)
            val err: CmdResult<Nothing> = CmdResult.ProtocolError(e.message ?: "connection lost")
            recordError(err)
            err
        }
    }

    private fun handleDisconnect(e: Exception) {
        // Fail all in-flight waiters first so their callers don't sit
        // out the full timeout. The reader's catch will then exit
        // because `connection` is now null.
        val waiters: List<CompletableDeferred<ResponseParser.Frame>> =
            synchronized(pending) {
                val snapshot = pending.values.toList()
                pending.clear()
                snapshot
            }
        for (w in waiters) {
            w.completeExceptionally(e)
        }
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
        stopReader()
        // Drop any in-flight waiters — they were waiting for a session
        // that's about to disappear.
        val waiters: List<CompletableDeferred<ResponseParser.Frame>> =
            synchronized(pending) {
                val snapshot = pending.values.toList()
                pending.clear()
                snapshot
            }
        for (w in waiters) {
            w.completeExceptionally(java.io.IOException("session closed"))
        }
        connection?.close()
        connection = null
        _state.value = _state.value.copy(connected = false)
    }

    private companion object {
        const val READ_RETRY_MS = 10L
        const val READ_TIMEOUT_MS = 200L
    }
}
