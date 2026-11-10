package dev.openpolaris.core.domain

import kotlinx.coroutines.CancellationException

/**
 * Redacted, bounded evidence intended for a user-approved support export.
 *
 * This is deliberately a snapshot rather than a live log collector: it never
 * reads credentials, raw frames, camera bytes, mount-local paths or hostnames.
 * The absence of raw logs is stated in the document so support does not mistake
 * a compact evidence bundle for a complete wire trace.
 */
data class SupportBundleSnapshot(
    val appBuild: String? = null,
    val generatedAtEpochMs: Long,
    val endpointKind: String? = null,
    val controlPort: Int? = null,
    val previewPort: Int? = null,
    val routePhase: String,
    val controlService: String,
    val previewService: String,
    val lastFailure: String? = null,
    val mountConnected: Boolean,
    val firmware: String? = null,
    val hardware: String? = null,
    val camera: String? = null,
    val storage: String? = null,
    val capture: String,
    val slowMotionLiveReady: Boolean,
    val slowMotionBlockers: List<String>,
    val rawFrameLoggingEnabled: Boolean,
    val captureTimeline: List<SupportBundleTimelineEntry> = emptyList(),
    val publication: SupportBundlePublication? = null,
) {
    init {
        require(generatedAtEpochMs >= 0L) { "support bundle time must be non-negative" }
        require(routePhase.isNotBlank()) { "route phase must not be blank" }
        require(controlService.isNotBlank()) { "control service must not be blank" }
        require(previewService.isNotBlank()) { "preview service must not be blank" }
        require(capture.isNotBlank()) { "capture summary must not be blank" }
    }
}

data class SupportBundleTimelineEntry(
    val stage: String,
    val detail: String,
) {
    init {
        require(stage.isNotBlank()) { "timeline stage must not be blank" }
    }
}

data class SupportBundlePublication(
    val basename: String,
    val type: Int?,
    val sizeBytes: Long?,
) {
    init {
        require(basename.isNotBlank()) { "publication basename must not be blank" }
        require("/" !in basename && "\\" !in basename) { "publication must contain only a basename" }
        require(sizeBytes == null || sizeBytes >= 0L) { "publication size must be non-negative" }
    }
}

object SupportBundleDocument {
    const val SCHEMA_VERSION: Int = 1
    const val MAX_CHARS: Int = 64 * 1024
    private const val REDACTED = "<redacted>"

    fun render(snapshot: SupportBundleSnapshot): String {
        val text = buildString {
            line("openpolaris_support_bundle_schema", SCHEMA_VERSION.toString())
            line("app_build", snapshot.appBuild)
            line("generated_at_epoch_ms", snapshot.generatedAtEpochMs.toString())
            line("endpoint_kind", snapshot.endpointKind)
            line("endpoint_host", REDACTED)
            line("control_port", snapshot.controlPort?.toString())
            line("preview_port", snapshot.previewPort?.toString())
            line("route_phase", snapshot.routePhase)
            line("control_service", snapshot.controlService)
            line("preview_service", snapshot.previewService)
            line("last_failure", snapshot.lastFailure)
            line("mount_connected", snapshot.mountConnected.toString())
            line("firmware", snapshot.firmware)
            line("hardware", snapshot.hardware)
            line("camera", snapshot.camera)
            line("storage", snapshot.storage)
            line("capture", snapshot.capture)
            line("slow_motion_live_ready", snapshot.slowMotionLiveReady.toString())
            line("slow_motion_blockers", snapshot.slowMotionBlockers.joinToString(" | "))
            line("raw_frame_logging", if (snapshot.rawFrameLoggingEnabled) "enabled" else "disabled")
            line("raw_logs", "not included by this bounded export")
            line("redaction", "hostnames, credentials, mount-local paths and raw camera bytes omitted")

            snapshot.captureTimeline.takeLast(20).forEachIndexed { index, entry ->
                line("capture_timeline_" + index + "_stage", entry.stage)
                line("capture_timeline_" + index + "_detail", entry.detail)
            }
            snapshot.publication?.let { publication ->
                line("publication_basename", publication.basename)
                line("publication_type", publication.type?.toString())
                line("publication_size_bytes", publication.sizeBytes?.toString())
            }
        }
        require(text.length <= MAX_CHARS) { "support bundle exceeds the bounded size" }
        return text
    }

    private fun StringBuilder.line(key: String, value: String?) {
        append(key)
        append('=')
        append(clean(value))
        append(10.toChar())
    }

    private fun clean(value: String?, limit: Int = 512): String {
        val bounded = value.orEmpty().take(limit)
        val printable = buildString(bounded.length) {
            bounded.forEach { character ->
                append(if (character.isISOControl()) ' ' else character)
            }
        }
        return printable
            .replace(Regex("""(?i)(password|token|secret)=([^\s;]+)"""), "$1=<redacted>")
            .ifBlank { "unknown" }
    }
}

data class SupportBundleExportRequest(
    val suggestedName: String,
    val text: String,
) {
    init {
        require(suggestedName == "openpolaris-support-bundle.txt") {
            "support bundle filename must remain stable and safe"
        }
        require(text.isNotBlank()) { "support bundle text must not be blank" }
        require(text.length <= SupportBundleDocument.MAX_CHARS) {
            "support bundle text exceeds the bounded size"
        }
    }
}

sealed interface SupportBundleExportResult {
    data class Stored(val location: String? = null) : SupportBundleExportResult
    data class Failed(val reason: String) : SupportBundleExportResult
}

fun interface SupportBundleExportSink {
    suspend fun export(request: SupportBundleExportRequest): SupportBundleExportResult
}

sealed interface SupportBundleExportState {
    data object Idle : SupportBundleExportState
    data class Ready(
        val request: SupportBundleExportRequest,
    ) : SupportBundleExportState
    data class Exporting(
        val request: SupportBundleExportRequest,
    ) : SupportBundleExportState
    data class Complete(
        val request: SupportBundleExportRequest,
        val location: String?,
    ) : SupportBundleExportState
    data class Failed(
        val request: SupportBundleExportRequest?,
        val reason: String,
    ) : SupportBundleExportState
}

/**
 * Prepares and saves a bounded, redacted support snapshot. It has no retry
 * path: a cancelled/failed platform save must be explicitly requested again.
 */
class SupportBundleExportController(
    private val sink: SupportBundleExportSink,
) {
    private var current: SupportBundleExportState = SupportBundleExportState.Idle
    val state: SupportBundleExportState
        get() = current

    fun prepare(snapshot: SupportBundleSnapshot): Boolean {
        val text = try {
            SupportBundleDocument.render(snapshot)
        } catch (error: IllegalArgumentException) {
            current = SupportBundleExportState.Failed(null, error.message ?: "invalid support snapshot")
            return false
        }
        current = SupportBundleExportState.Ready(
            SupportBundleExportRequest(
                suggestedName = "openpolaris-support-bundle.txt",
                text = text,
            ),
        )
        return true
    }

    suspend fun export(): SupportBundleExportState {
        val ready = current as? SupportBundleExportState.Ready ?: return current
        current = SupportBundleExportState.Exporting(ready.request)
        return try {
            when (val result = sink.export(ready.request)) {
                is SupportBundleExportResult.Stored ->
                    SupportBundleExportState.Complete(ready.request, result.location).also { current = it }
                is SupportBundleExportResult.Failed ->
                    SupportBundleExportState.Failed(ready.request, result.reason).also { current = it }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            SupportBundleExportState.Failed(
                ready.request,
                "support bundle export failed: " +
                    (error.message ?: error::class.simpleName ?: "unknown error"),
            ).also { current = it }
        }
    }

    fun reset() {
        current = SupportBundleExportState.Idle
    }
}

expect fun createSupportBundleExportSink(): SupportBundleExportSink
