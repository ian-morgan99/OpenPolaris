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
 *  - the network read runs on [Dispatchers.IO];
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
    private val scope = CoroutineScope(SupervisorJob(parent) + Dispatchers.IO)

    private val _bytes = MutableStateFlow<ByteArray?>(null)
    val bytes: StateFlow<ByteArray?> = _bytes.asStateFlow()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var transport: PreviewTransport? = null
    @Volatile private var job: Job? = null

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
            withContext(Dispatchers.IO) { t.start(host, port) }
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
