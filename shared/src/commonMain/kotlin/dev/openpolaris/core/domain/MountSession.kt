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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

/**
 * Single owner of the mount connection (ARCHITECTURE §3.1).
 *
 * Wire model after issue #6: a **session-level** background reader owns
 * the single [Connection.read] call. Every complete frame the reader
 * sees is dispatched to one of two places:
 *
 *  - Push frames ([Codes.SET_TILT_STATE] = 538) → [tilt] flow. The
 *    mount emits these continuously on the same socket as request
 *    traffic, and a per-request spin-read used to drop the majority of
 *    them (538 frames per 5 minutes in the live-burst capture).
 *  - Every other frame → the [pending] waiter for that frame's
 *    [ResponseParser.Frame.code], if any is currently registered. The
 *    request method registers a `CompletableDeferred` before it
 *    writes, so its response is the next frame the reader sees that
 *    has the matching code, regardless of any interleaved 538s.
 *
 * The reader is started in [tryConnect] **before** the handshake write
 * so the handshake reply (a 284 frame) is not lost to a race with the
 * reader's first read. The reader is cancelled in [disconnect] and
 * [handleDisconnect].
 *
 * [readerScope] is injected so tests can pass a [kotlinx.coroutines.test.TestScope]
 * (or similar) and avoid racing real time. The default is a private
 * `SupervisorJob() + Dispatchers.Default` so production callers do not
 * have to think about it.
 *
 * The session itself does **not** reconnect on its own. The original
 * spec implies an exponential-backoff auto-reconnect capped at 30 s;
 * that is tracked as a separate follow-up.
 */
class MountSession(
    private val connectionFactory: () -> Connection,
    private val host: String = "192.168.0.1",
    private val port: Int = 9090,
    private val readerScope: CoroutineScope = MountSession.defaultReaderScope(),
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
     * Push stream of [TiltSample]s. Each sample is parsed from a
     * `SET_TILT_STATE` (538) push frame. Replay is zero (subscribers
     * that arrive late do not get backlog, which is what the consumer
     * — the auto-level controller — wants; it should start from the
     * current sample, not a stale one).
     *
     * Buffer is 64 with `SUSPEND` overflow. With the live-burst capture
     * the tilt push rate is ~2 Hz and the consumer is a single
     * coroutine, so we never come close to 64 in flight. The buffer
     * exists to absorb a brief stall (GC pause, deserialisation) without
     * losing a sample; if a consumer cannot keep up for longer than
     * ~32 s we suspend it rather than silently drop a sample.
     */
    private val _tilt = MutableSharedFlow<TiltSample>(
        replay = 0,
        extraBufferCapacity = TILT_BUFFER_CAPACITY,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.SUSPEND,
    )
    val tilt: Flow<TiltSample> = _tilt.asSharedFlow()

    /**
     * Test seam: expose the live MutableSharedFlow so unit tests can
     * drive it deterministically. Production code should subscribe to
     * [tilt] instead.
     */
    internal val tiltForTest: SharedFlow<TiltSample> get() = _tilt

    private val sendMutex = Mutex()

    /** Pending request waiters, keyed by the frame code they are waiting on. */
    private val pending = mutableMapOf<Int, CompletableDeferred<ResponseParser.Frame>>()

    private var connection: Connection? = null
    private var readerJob: Job? = null

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
            // Start the background reader BEFORE the handshake write so
            // the 284 reply is delivered to a running reader and any
            // pre-existing 538 push frames are not lost.
            startReader(conn)
            _state.value = _state.value.copy(connected = true)
            // Lifecycle handshake: poll status once (PROTOCOL.md §4).
            // Fire-and-forget. The reader will observe the 284 reply
            // (it has no waiter, so it is published to `_frames` for
            // diagnostics) and any 538 push frames will flow to
            // [tilt]. A waitable handshake is out of scope for issue
            // #6: the production gap it fixes is push-frame loss, not
            // handshake robustness.
            send(Codes.PUSH_MODE_STATE)
            true
        } catch (e: Exception) {
            stopReader()
            conn.close()
            false
        }
    }

    /**
     * Send a command and wait for the first response frame with the
     * same [code]. Returns [CmdResult.Timeout] if no such frame arrives
     * within [timeoutMs]. Interleaved 538 push frames are
     * demultiplexed: they go to [tilt] and do not delay this call.
     */
    suspend fun <T> request(
        code: Int,
        payload: String = EMPTY_CONTENT,
        timeoutMs: Long = 2000,
        parse: (ResponseParser.Frame) -> T?,
    ): CmdResult<T> {
        val conn = connection ?: return CmdResult.ProtocolError("not connected")
        return sendMutex.withLock {
            val deferred = CompletableDeferred<ResponseParser.Frame>()
            try {
                pending[code] = deferred
                conn.write(command(code) { putRaw(payload) })
            } catch (e: Exception) {
                pending.remove(code)
                handleDisconnect(e)
                return@withLock CmdResult.ProtocolError(e.message ?: "write failed")
            }
            try {
                val frame = withTimeout(timeoutMs) { deferred.await() }
                _frames.value = frame
                val parsed = parse(frame)
                if (parsed == null) {
                    CmdResult.ProtocolError("parse failed for code $code")
                } else {
                    CmdResult.Ok(parsed)
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                // Drop the waiter so a late reply does not satisfy a
                // future request.
                pending.remove(code, deferred)
                CmdResult.Timeout
            } catch (e: kotlinx.coroutines.CancellationException) {
                pending.remove(code, deferred)
                throw e
            } catch (e: Exception) {
                pending.remove(code, deferred)
                handleDisconnect(e)
                CmdResult.ProtocolError(e.message ?: "read failed")
            }
        }
    }

    private fun handleDisconnect(e: Exception) {
        stopReader()
        connection?.close()
        connection = null
        // Fail any outstanding requests so callers do not hang until their
        // own per-call timeout fires.
        val failed = pending.values.toList()
        pending.clear()
        for (d in failed) d.completeExceptionally(e)
        _state.value = _state.value.copy(connected = false)
    }

    /** Fire-and-forget send (e.g., jog commands); no response awaited. */
    suspend fun send(code: Int, payload: String = EMPTY_CONTENT) {
        val conn = connection ?: return
        sendMutex.withLock {
            try {
                conn.write(command(code) { putRaw(payload) })
            } catch (e: Exception) {
                handleDisconnect(e)
            }
        }
    }

    fun disconnect() {
        wantConnected = false
        stopReader()
        connection?.close()
        connection = null
        // Cancel any waiters the reader did not get a chance to fail.
        val failed = pending.values.toList()
        pending.clear()
        for (d in failed) d.completeExceptionally(IllegalStateException("disconnected"))
        _state.value = _state.value.copy(connected = false)
    }

    // --- Background reader ---------------------------------------------------

    private fun startReader(conn: Connection) {
        // Idempotent: a second call before stopReader is a no-op. This
        // matters because some test harnesses may call tryConnect twice.
        if (readerJob?.isActive == true) return
        readerJob = readerScope.launch {
            runReaderLoop(conn)
        }
    }

    private fun stopReader() {
        readerJob?.cancel()
        readerJob = null
    }

    /**
     * Single owner of [Connection.read]. Parses every complete frame
     * out of the read buffer and dispatches it: 538 → [tilt]; other
     * → the matching waiter in [pending], if any. A frame whose code
     * has no waiter is published to [frames] for diagnostic visibility
     * and otherwise dropped.
     *
     * The loop terminates when the connection reports a clean close
     * (`read` returns 0), the job is cancelled, or any other read
     * failure occurs. On a non-cancellation failure we mark the
     * session disconnected and fail all pending requests.
     */
    private suspend fun runReaderLoop(conn: Connection) {
        val parser = ResponseParser()
        val buf = ByteArray(4096)
        var carry = ByteArray(0)
        while (true) {
            val n = conn.read(buf, READ_TIMEOUT_MS)
            if (n < 0) {
                // Read timeout. Go around again; this is the normal
                // idle state when the mount has nothing to send.
                // We yield here so a virtual-time test scheduler can
                // run other coroutines (e.g. a pending request waiter
                // that needs to register itself, or the test body
                // that is awaiting the deferred). Without the yield
                // the reader is a tight CPU-bound loop in virtual
                // time and starves the rest of the test.
                kotlinx.coroutines.yield()
                continue
            }
            if (n == 0) {
                // Clean close.
                handleDisconnect(IllegalStateException("connection closed"))
                return
            }
            val combined = carry + buf.copyOf(n)
            val (frames, consumed) = parser.parse(combined)
            for (f in frames) dispatchFrame(f)
            carry = combined.drop(consumed).toByteArray()
        }
    }

    private fun dispatchFrame(f: ResponseParser.Frame) {
        // Tilt push: route to the flow, do NOT touch pending.
        if (f.code == Codes.SET_TILT_STATE) {
            val sample = TiltSample.fromFrame(f)
            if (sample != null) {
                // tryEmit: if there is no active collector the sample
                // is dropped — the buffer is for absorbing stalls, not
                // for indefinite retention. The reader must not block
                // here or we will lose subsequent frames.
                _tilt.tryEmit(sample)
            }
            return
        }
        // Request/response: complete the matching waiter, if any.
        val waiter = pending.remove(f.code)
        if (waiter != null) {
            waiter.complete(f)
        } else {
            // No waiter. Publish to the StateFlow for diagnostic
            // visibility (same behaviour as the old per-request loop
            // had on its best-effort frames).
            _frames.value = f
        }
    }

    /**
     * Test seam: complete the current waiter for [code] with [frame], or
     * no-op if no waiter is registered. Lets tests drive the demux
     * without going through the reader (for sequencing assertions).
     */
    internal fun deliverFrameForTest(code: Int, frame: ResponseParser.Frame) {
        pending.remove(code)?.complete(frame)
    }

    /**
     * Test seam: push a [TiltSample] directly into the tilt flow,
     * bypassing the wire parser. Useful for tests of downstream
     * consumers (issue #6 follow-on) that should not care about wire
     * encoding.
     */
    internal fun publishTiltForTest(sample: TiltSample) {
        _tilt.tryEmit(sample)
    }

    private companion object {
        const val READ_TIMEOUT_MS = 50
        const val TILT_BUFFER_CAPACITY = 64

        fun defaultReaderScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
