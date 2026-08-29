package dev.openpolaris.core.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Best-effort live-preview stream. Wraps the per-platform
 * [PreviewTransport] and exposes the latest raw JPEG bytes as a
 * [StateFlow]. Decoding the JPEG into a bitmap is the caller's job
 * (kept out of the shared module so [PreviewController] stays
 * platform-free and unit-testable on the JVM).
 *
 * The controller itself is "dumb" on purpose:
 *  - the network read runs on [Dispatchers.Default] (we cannot use
 *    [Dispatchers.IO] from commonMain; it's JVM/Native-only in
 *    kotlinx-coroutines 1.9.x);
 *  - the latest frame is published via [bytes], conflated to the
 *    freshest value if a slow consumer falls behind;
 *  - [state] reports connection lifecycle so the UI can show
 *    "Connecting…" / "Stream unavailable" without polling bytes.
 *
 * Spec: ARCHITECTURE.md §3.4 — preview is best-effort, drops late
 * frames, and is independent from the control socket.
 */
class PreviewController(
    private val transportFactory: ((ByteArray) -> Boolean, (Throwable) -> Unit) -> PreviewTransport = ::createPreviewTransport,
    parent: Job? = null,
) {
    // We use `Dispatchers.Default` because `Dispatchers.IO` is not exposed in
    // commonMain (it's JVM/Native-only in kotlinx-coroutines 1.9.x). The
    // preview loop is CPU-bound (sockets on JVM map to Default just fine) and
    // JPEG decode is also CPU-bound and runs on Default in the ViewModel.
    // We construct the SupervisorJob explicitly to avoid the deprecated
    // `SupervisorJob(parent) + Dispatcher` pattern; the explicit
    // SupervisorJob is preserved on the LHS of the `+` operator.
    private val scope = run {
        val sj: Job = if (parent != null) SupervisorJob(parent) else SupervisorJob()
        CoroutineScope(sj + Dispatchers.Default)
    }

    private val _bytes = MutableStateFlow<ByteArray?>(null)
    val bytes: StateFlow<ByteArray?> = _bytes.asStateFlow()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    // `transport` and `job` are written in `start()` and read in `stop()` and
    // `shutdown()`. All three are called from the caller's coroutine context
    // (typically the UI thread on Android, the main scope on JVM), and they
    // never suspend between read and write. We do not annotate them as
    // `@Volatile` because that annotation is JVM-only and we want to stay in
    // commonMain. The StateFlows above carry all cross-coroutine state.
    private var transport: PreviewTransport? = null
    private var job: Job? = null

    /**
     * Begin streaming from `http://host:port`. Any prior stream is
     * stopped first. Idempotent: calling [start] while already running
     * restarts the stream on the new host.
     */
    fun start(host: String, port: Int = 8080) {
        stop()
        _state.value = State.Connecting
        val t = transportFactory(
            { jpeg -> consumeFrame(jpeg) },
            { err ->
                _state.value = State.Error(err.message ?: err::class.simpleName ?: "error")
            },
        )
        transport = t
        job = scope.launch {
            withContext(Dispatchers.Default) { t.start(host, port) }
            if (_state.value is State.Connecting) {
                // start() returned without producing an error — caller stopped it.
                _state.value = State.Stopped
            }
        }
    }

    fun stop() {
        transport?.stop()
        transport = null
        job?.cancel()
        job = null
        _bytes.value = null
        if (_state.value !is State.Error) _state.value = State.Stopped
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    private fun consumeFrame(jpeg: ByteArray): Boolean {
        _bytes.value = jpeg
        if (_state.value is State.Connecting) _state.value = State.Streaming
        return true
    }

    sealed class State {
        data object Idle : State()
        data object Connecting : State()
        data object Streaming : State()
        data object Stopped : State()
        data class Error(val message: String) : State()
    }
}
