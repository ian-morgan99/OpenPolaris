package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.EMPTY_CONTENT
import dev.openpolaris.core.protocol.ResponseParser
import dev.openpolaris.core.protocol.TiltCodec
import dev.openpolaris.core.protocol.command
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

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
 * [disconnect] / [handleDisconnect]. Use [shutdown] for the terminal
 * release of the entire session (including the underlying reader
 * scope) — see issue #20.
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
    /**
     * App handshake parameters. The connection is now considered
     * "fully established" only after [authenticate] has run a 820
     * probe and either a 821 token (if requested) plus a 823 hello.
     * Without this, the gimbal responds to almost every command with
     * `ret:-1` (issue: 526 SP_TEST evidence, see PROTOCOL.md §3).
     *
     * Default is [AuthConfig] (app `openpolaris`, no password). Most
     * production gimbal firmware doesn't require a password, so the
     * default works out-of-the-box; only networks that have set a
     * connection password need to supply one.
     */
    private val auth: AuthConfig = AuthConfig(),
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
     * Hot push of decoded 538 (SET_TILT_STATE) samples. This is the
     * authoritative source of tilt data for
     * [AutoLevelController.runAndAwait] on real hardware (issue #6):
     *
     *  - **Every** 538 frame the reader parses is delivered, in arrival
     *    order, to every active collector, with no conflation. A
     *    [StateFlow] mirror of 538 would drop intermediate samples when
     *    they arrive faster than the controller samples; a buffered
     *    [kotlinx.coroutines.flow.MutableSharedFlow] does not.
     *  - 538 frames do **not** appear in [frames] and do **not** complete
     *    a [pending] waiter. 538 is a push, not a response — there is no
     *    inflight [request] for it.
     *  - Malformed 538 frames (TiltCodec returns null) are silently
     *    dropped: the flow stays open, the reader keeps reading, no
     *    caller crashes on a bad payload.
     *  - The flow is **never** closed — a `SharedFlow` survives
     *    reconnects. Disconnect only stops new emissions. Collectors from
     *    a previous session that are still active will see an idle flow
     *    until the next session emits; collectors should cancel their
     *    own [kotlinx.coroutines.Job] on disconnect.
     *  - The buffer is 64 with `DROP_OLDEST` overflow. A slow consumer
     *    (e.g. UI thread doing a plate-solve) may cause older samples to
     *    be dropped. The reader MUST NOT block on a full buffer — it
     *    owns the socket and a stalled reader would wedge the entire
     *    mount channel. We prefer losing a few intermediate samples over
     *    blocking. In practice the only consumer is the AutoLevel
     *    controller, which samples far faster than 538 frames arrive
     *    (every ~100 ms), so drops should be zero in normal operation.
     *
     *  - **What the drop counter actually counts**: [tiltDropsNoSubscriber]
     *    increments only when an emit occurs with **no live collector
     *    attached** (the SharedFlow's subscriptionCount is zero at emit
     *    time). Those emits are guaranteed to never reach a downstream
     *    consumer — there is no buffer drain that can ever catch up
     *    because nothing is reading. Evictions caused by a slow but
     *    *live* collector (the buffer's DROP_OLDEST path firing while
     *    the collector is still attached but unable to keep up) are
     *    **not** separately counted: the only honest observability
     *    hook for that case is to compare the number of 538 frames
     *    published by the reader against the number of samples a
     *    consumer actually receives. [TiltStreamTest] pins both paths.
     *
     * Use [TiltSampleSource]-style adapters to bridge between this hot
     * [Flow] and the `suspend () -> Tilt?` shape [AutoLevelController]
     * expects.
     */
    private val _tilt = MutableSharedFlow<TiltSample>(
        replay = 0,
        // 538 frames arrive at ~10 Hz on the Polaris. Channel.BUFFERED is a
        // Channel-specific sentinel (-2), not a number, so we use an explicit
        // 64 here. 64 gives ~6 seconds of headroom for a slow consumer
        // (e.g. UI thread doing a plate-solve) before DROP_OLDEST starts
        // evicting the oldest unread sample. Going lower risks visible
        // jitter during a stall; going higher wastes memory for no
        // practical benefit.
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val tilt: Flow<TiltSample> = _tilt

    /**
     * Number of 538 (SET_TILT_STATE) samples the reader attempted to
     * publish on [tilt] while **no live collector was attached** (the
     * SharedFlow's subscriptionCount was zero at the moment of emit).
     * These emits are guaranteed to never reach a downstream consumer:
     * with no one reading, the buffer is irrelevant — every sample
     * disappears into the void. The counter exists for observability —
     * `0` is the expected steady state. A non-zero value means the
     * AutoLevel controller (or any other [tilt] consumer) was not
     * subscribed when frames arrived.
     *
     * **This counter does NOT include evictions from a slow but live
     * collector.** When a collector is attached but cannot keep up with
     * the ~10 Hz push rate, the SharedFlow's DROP_OLDEST policy silently
     * evicts the oldest queued sample to make room. The reader cannot
     * observe that eviction through the SharedFlow API — `tryEmit`
     * returns success because the new sample *was* accepted, even
     * though an older queued sample was dropped to make room. The only
     * way to detect that case is to compare the number of frames
     * published by the reader against the number of samples a consumer
     * actually received (see [TiltStreamTest] for the regression).
     *
     * Incremented on the reader coroutine; safe to read from any
     * collector because [MutableStateFlow.update] is atomic. Reset to
     * zero on [connect] so the count always reflects the current
     * session.
     */
    private val _tiltDropsNoSubscriber = MutableStateFlow(0L)
    val tiltDropsNoSubscriber: StateFlow<Long> = _tiltDropsNoSubscriber

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
     * Test-only seam: directly publish a [TiltSample] on [tilt] without
     * going through the reader loop's 538 demux. Mirrors
     * [publishFrameForTest] for the new flow — tests for the tilt
     * channel can drive it without standing up a [ResponseParser] frame.
     */
    internal fun publishTiltForTest(sample: TiltSample) {
        // tryEmit is non-suspending; returns false if the buffer is
        // full. We ignore the failure in tests — the test scope owns
        // its own collector lifecycle.
        _tilt.tryEmit(sample)
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
     * Set to true by [shutdown] and never reset. A [MountSession] is a
     * single-use object: once [shutdown] has been called, [connect] is
     * forbidden (it throws [IllegalStateException]). The flag exists so
     * the JVM-leak test (issue #20) can stand up a fresh session for
     * every cycle without worrying about a stale state from a previous
     * [disconnect] leaking across.
     */
    @kotlin.concurrent.Volatile
    private var closed = false

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
        // Shutdown is terminal — a fresh MountSession must be created
        // after shutdown. We check this here rather than in
        // tryConnect() because the connect() public surface is the
        // contract; the private helper is allowed to be called from
        // tests that drive the state directly.
        if (closed) throw IllegalStateException("session is shut down")
        wantConnected = true
        return tryConnect()
    }

    private suspend fun tryConnect(): Boolean {
        // 3b.5-BUG: connectionFactory() can throw (e.g. the test harness
        // intentionally returns a throwing factory, or a real factory
        // hits a permission failure before the socket is opened). Move
        // it inside the try so the caller's `connect()` sees a clean
        // `false` instead of an uncaught exception that would crash the
        // launched coroutine. On failure, `conn` is null so the cleanup
        // branch skips conn.close().
        return try {
            val conn = connectionFactory()
            try {
                conn.connect(host, port, timeoutMs = 5000)
                connection = conn
                // Clear any previous protocol error so observers can tell
                // "the mount came back" from "no error has happened yet".
                _state.value = _state.value.copy(connected = true, lastErrorMessage = null)
                // Reset drop counter so it always reflects the current
                // session. Drift across reconnects would make the metric
                // meaningless.
                _tiltDropsNoSubscriber.value = 0L
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
                // App handshake: 820 → 821 (if needed) → 823. Without this
                // the gimbal silently drops almost every command (526
                // evidence: 810@ret:-1, 519@ret:-1, etc.). The reader is
                // already running so the 820/821/823 reply frames are
                // dispatched to the in-flight waiters. See
                // [authenticate] and PROTOCOL.md §3.
                authenticate()
                true
            } catch (e: Exception) {
                stopReader()
                conn.close()
                connection = null
                // 2026-09-01: surface the actual failure into lastErrorMessage
                // so AppViewModel.connect() can include it in the user-visible
                // status string. Without this, every failure mode — TCP down,
                // 284 timeout, 820 needed:1, 821 reject, 823 timeout, parser
                // error — collapses into a single opaque "Could not reach
                // $host" line. The user then has to dig through
                // /tmp/openpolaris-desktop.log to find which step failed.
                // Tagging with the step name (handshake / 820 / 821 / 823 /
                // other) makes the message self-disclosing.
                val reason = when {
                    e is java.io.IOException &&
                        e.message?.startsWith("handshake failed:") == true ->
                        "no response to 284 handshake (gimbal may be in deep sleep — try Wake)"
                    e is java.io.IOException &&
                        e.message?.startsWith("auth probe (820) failed:") == true ->
                        "no response to 820 auth probe within 10s"
                    e is java.io.IOException &&
                        e.message?.startsWith("auth token (821) failed:") == true ->
                        "no response to 821 token within 10s"
                    e is java.io.IOException &&
                        e.message?.startsWith("app hello (823) failed:") == true ->
                        "no response to 823 hello within 10s"
                    e is java.io.IOException &&
                        e.message?.startsWith("gimbal rejected connection password") == true ->
                        "821 reported ret≠0 — wrong password"
                    e is java.io.IOException &&
                        e.message?.startsWith("gimbal requires connection password") == true ->
                        "gimbal requires a connection password (none configured)"
                    else -> e.message ?: e::class.simpleName ?: "unknown"
                }
                _state.value = _state.value.copy(
                    connected = false,
                    lastErrorMessage = reason,
                )
                false
            }
        } catch (e: Exception) {
            // Outer catch: connectionFactory() itself threw before any
            // socket was opened. Tag with "factory:" so the user can tell
            // this is a wiring problem, not a gimbal problem.
            val factoryReason = e.message ?: e::class.simpleName ?: "unknown"
            _state.value = _state.value.copy(
                connected = false,
                lastErrorMessage = "factory: $factoryReason",
            )
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
     * Send the 820 → 821 → 823 app-handshake that the gimbal firmware
     * (sw:6.0.0.54 and later) requires before it will process any other
     * command. Without it, every other opcode returns `ret:-1` (see
     * PROTOCOL.md §3, 526 SP_TEST evidence, issue: 810/519/525
     * `ret:-1` flood).
     *
     * Wire summary (live-captured 2026-09-01):
     *
     *   1. `1&820&2&-100#` → `820@needed:0;#` (or `needed:1;`)
     *      The `-100` payload is what the Benro app sends; the
     *      gimbal treats it as a "query" request and replies with
     *      whether a connection password is required. The literal
     *      `-100` also doubles as the empty-payload sentinel for
     *      these specific codes (so we can't just send
     *      [EMPTY_CONTENT]).
     *
     *   2. If `needed:1` AND [AuthConfig.password] is non-null:
     *      `1&821&2&token:<password>#`
     *      → `821@token:<echo>;ret:0;#` on accept
     *      → `821@token:<echo>;ret:1;` on reject
     *      Skipping the 821 step when the gimbal says it needs a
     *      password is the original bug: the gimbal just kept
     *      returning `ret:-1` for every other opcode, with no
     *      obvious reason. We surface that as a connect failure
     *      here so the UI can prompt the user.
     *
     *   3. `1&823&2&app:<appName>;ver:<appVersion>#`
     *      → `823@app:openpolaris;ver:<firmwareVersion>;#`
     *      The response carries the firmware's own `ver:` value,
     *      not the one we sent — useful for telemetry. We don't
     *      branch on it (the decompiled Android app doesn't either).
     *
     * Throws [java.io.IOException] (via the [tryConnect] catch) on
     * any non-`Ok` step, so a connect-time handshake failure
     * surfaces as `connect() == false` to the caller.
     *
     * Timeouts: 2000ms per step (matches the rest of [request]).
     */
    private suspend fun authenticate() {
        // 1. Probe: does this gimbal require a connection password?
        // The Benro app sends `-100` as the 820 payload (live
        // captures, 2026-09-01). The gimbal responds with
        // `needed:0;` (no password) or `needed:1;` (password
        // required). We do NOT send a bare EMPTY_CONTENT because
        // the gimbal treats the empty payload as a no-op and
        // returns no `needed:` field — that was the early-2026
        // bug we kept running into.
        val probe = request<ResponseParser.Frame>(
            code = Codes.APP_PASSWORD_INFO,
            payload = "-100",
            timeoutMs = 10000L,
        ) { it }
        if (probe !is CmdResult.Ok) {
            throw java.io.IOException("auth probe (820) failed: $probe")
        }
        val needed = probe.value[NEEDED]?.trim() == "1"
        // 2. Token step: only when the gimbal demands one AND we
        // have a password configured. If the gimbal requires a
        // password but the caller didn't supply one, we abort
        // connect — the gimbal will not accept anything else
        // from us, and there's no point pretending the
        // connection is up. The UI surfaces a "password
        // required" status via the resulting [connect] == false.
        if (needed) {
            val password = auth.password
                ?: throw java.io.IOException(
                    "gimbal requires connection password but AuthConfig.password is null",
                )
            val token = request<ResponseParser.Frame>(
                code = Codes.APP_TOKEN,
                payload = "token:$password;",
                timeoutMs = 10000L,
            ) { it }
            if (token !is CmdResult.Ok) {
                throw java.io.IOException("auth token (821) failed: $token")
            }
            // The token reply also carries ret:0/1. ret:1 means the
            // gimbal rejected our password. Convert to a clean
            // connect failure rather than leaving a half-open
            // session.
            val ret = token.value[RET]?.trim()
            if (ret != null && ret != "0") {
                throw java.io.IOException(
                    "gimbal rejected connection password (821 ret=$ret)",
                )
            }
        }

        // 3. Hello: identify ourselves. Always sent (even on
        // passwordless networks), per the live captures. The
        // gimbal replies with its OWN `ver:` value (the firmware
        // version), which we log for the next maintainer's
        // benefit. We don't currently surface it on the wire
        // anywhere, but [request] already records the parsed
        // frame in [frames] for any subscriber.
        val helloPayload = "app:${auth.appName};ver:${auth.appVersion};"
        ProtocolTrace.log(
            "auth",
            "823 hello → payload='$helloPayload' timeout=10000ms",
        )
        val hello = request<ResponseParser.Frame>(
            code = Codes.APP_HELLO,
            payload = helloPayload,
            timeoutMs = 10000L,
        ) { it }
        when (hello) {
            is CmdResult.Ok ->
                ProtocolTrace.log(
                    "auth",
                    "823 hello ← OK fields=${hello.value.fields}",
                )
            is CmdResult.Timeout ->
                ProtocolTrace.log(
                    "auth",
                    "823 hello ← TIMEOUT after 10000ms (gimbal did not ack)",
                )
            is CmdResult.ProtocolError ->
                ProtocolTrace.log(
                    "auth",
                    "823 hello ← ProtocolError: ${hello.message}",
                )
        }
        if (hello !is CmdResult.Ok) {
            throw java.io.IOException("app hello (823) failed: $hello")
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
                    ProtocolTrace.logBytes(
                        "reader",
                        "read $n bytes (carry=${carry.size})",
                        buf.copyOf(n),
                    )
                    val combined = carry + buf.copyOf(n)
                    val (frames, consumed) = parser.parse(combined)
                    carry = combined.drop(consumed).toByteArray()
                    for (f in frames) {
                        ProtocolTrace.log(
                            "reader",
                            "frame code=${f.code} fields=${f.fields}",
                        )
                        // Demux by code (issue #6 / PLAN-CRITICAL-REVIEW §F):
                        // 538 is a push, not a response. Route it to the
                        // tilt channel and skip both the StateFlow mirror
                        // and the per-code waiter map.
                        //
                        // Why not also publish 538 to `_frames`? Doing so
                        // would re-create the StateFlow-drop problem the
                        // channel solves — a StateFlow conflates, and the
                        // conflated value is the LAST frame observed, not
                        // every one in order. Anyone who needs every 538
                        // must collect from `tilt` instead.
                        if (f.code == Codes.SET_TILT_STATE) {
                            val tilt = TiltCodec.parse(f)
                            if (tilt != null) {
                                // _tilt is configured with DROP_OLDEST,
                                // so tryEmit is non-suspending and never
                                // returns false: if no collector has
                                // drained the buffer, the oldest queued
                                // sample is evicted to make room. The
                                // return value is therefore ignored.
                                //
                                // The reader MUST stay non-suspending
                                // because it owns the socket; a stalled
                                // reader would wedge the entire mount
                                // channel. We prefer losing a few
                                // intermediate samples to a slow
                                // consumer over blocking.
                                //
                                // Drop counting policy: this counter
                                // [tiltDropsNoSubscriber] only
                                // increments when no collector is
                                // attached at emit time. Those emits
                                // are guaranteed to be lost because
                                // nothing is reading the buffer. A
                                // slow but *live* collector causes
                                // DROP_OLDEST to evict the oldest
                                // queued sample, but `tryEmit` cannot
                                // observe that eviction (the new
                                // sample was accepted, an older one
                                // was dropped). [TiltStreamTest]
                                // pins that case by comparing
                                // published vs received counts.
                                _tilt.tryEmit(
                                    TiltSample(
                                        pitchDeg = tilt.pitchDeg,
                                        rollDeg = tilt.rollDeg,
                                        timestampMs = currentEpochMillis(),
                                    ),
                                )
                                if (_tilt.subscriptionCount.value == 0) {
                                    _tiltDropsNoSubscriber.update { it + 1 }
                                }
                            }
                            continue
                        }
                        _frames.value = f
                        val waiter = synchronized(pending) { pending.remove(f.code) }
                        if (waiter != null) {
                            waiter.complete(f)
                            // Yield after completing a waiter so the
                            // writer coroutine whose `await()` we just
                            // satisfied gets a chance to resume before
                            // we race ahead and read its next request's
                            // response before it's been written. The
                            // tight loop test ([TiltStreamTest]'s
                            // `liveSlowCollector...`) emits pure 538
                            // pushes, no waiters, so this yield is a
                            // no-op there.
                            yield()
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
                    val frame = command(code) { putRaw(payload) }
                    ProtocolTrace.logBytes("writer", "→ code=$code", frame)
                    conn.write(frame)
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
        // Cancel any coroutines still hanging off the reader scope
        // (e.g. a still-suspended request() waiter, or a poll loop
        // like SimulatedMount's). We use cancelChildren() rather than
        // cancel() so the scope itself stays valid for subsequent
        // reconnects on a long-lived MountSession — the same scope is
        // reused by [startReader] on the next [connect]. The child
        // cancellation is also what makes JVM tests exit cleanly: a
        // Dispatchers.Default-backed readerScope would otherwise keep
        // the JVM alive past runTest().
        readerScope.coroutineContext.cancelChildren()
    }

    /**
     * Terminal operation: permanently tear down the [MountSession] and
     * release the [readerScope] itself (not just its children, which is
     * what [disconnect] does). After [shutdown]:
     *  - [connect] is forbidden and throws [IllegalStateException].
     *  - The reader coroutine is cancelled; any in-flight [request]
     *    waiters are failed with a [java.io.IOException] ("session
     *    closed") the same way they would be on [disconnect].
     *  - All state flows ([state], [frames], [tiltDropsNoSubscriber]) are reset so a
     *    caller that still holds references sees a clean "never used"
     *    view rather than a stale snapshot.
     *
     * Idempotent: calling [shutdown] more than once is a no-op. The
     * [readerScope] is cancelled exactly once; subsequent calls observe
     * [closed] = true and return without touching the scope.
     *
     * This is the seam the JVM-leak test in
     * [dev.openpolaris.core.domain.SessionShutdownLeakTest] (issue #20)
     * relies on to keep [DebugProbes.dumpCoroutines] count bounded
     * across many connect→disconnect cycles: without cancelling the
     * scope itself, a [Dispatchers.Default]-backed readerScope in
     * production would outlive every [MountSession] and accumulate
     * reader jobs (and their socket-poll loops) forever.
     */
    fun shutdown() {
        if (closed) return
        closed = true
        wantConnected = false
        // disconnect() handles the per-cycle cleanup; it's safe to call
        // even when nothing is connected (it null-checks connection and
        // pending is empty when nothing is in flight).
        disconnect()
        // Now cancel the scope itself — this is what disconnect() does
        // NOT do. After cancel() the scope's coroutineContext.job is
        // in CANCELLED state, and any future launch on the scope
        // becomes a no-op. This is the leak fix: a Dispatchers.Default
        // readerScope would otherwise survive every MountSession that
        // uses it.
        readerScope.coroutineContext.cancel() // INTENTIONAL: this is the fix; leave in place for normal builds
        _state.value = MountState()
        _frames.value = null
        _tiltDropsNoSubscriber.value = 0L
    }

    private companion object {
        const val READ_RETRY_MS = 10L
        const val READ_TIMEOUT_MS = 200L
        // Field names used in 820/821 response frames. Kept as
        // constants so the live-wire tests can reference them.
        const val NEEDED = "needed"
        const val RET = "ret"
    }
}
