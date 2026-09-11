package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.EMPTY_CONTENT
import kotlinx.coroutines.delay

/**
 * Camera parameter control (ISO / WB / aperture / EV / capture).
 *
 * Semantics recovered from polestar_app: values are INDICES into firmware-sorted
 * option lists (SP_SetCameraIsoIndex etc.), not raw ISO/shutter numbers. The app
 * queries the list, presents it sorted, and sends the chosen index.
 *
 * The legacy ten-pair methods below remain for simulator compatibility. Physical
 * qualification uses the individually evidenced [Codes.BenroCamera] map via
 * [queryBenroInfo] and [qualifyBenroSetting].
 */
class CameraController(private val session: MountSession) {

    data class Info(
        val code: Int,
        val raw: String,
        val index: Int?,
        val options: List<String> = emptyList(),
        val ready: Int? = null,
    )

    data class QualificationResult(
        val label: String,
        val requestedIndex: Int,
        val before: Info,
        val after: Info,
        val sent: Boolean = true,
        val error: String? = null,
        val setRaw: String? = null,
        val setRet: Int? = null,
    ) {
        val setAccepted: Boolean get() = sent && setRaw != null && setRaw != "TIMEOUT" &&
            !setRaw.startsWith("ERROR:") && (setRet == null || setRet >= 0)
        val verified: Boolean get() = setAccepted && after.index == requestedIndex
    }

    /**
     * Result of a focus jog (262) or MF adjust (311). These codes have no INFO
     * read-back pair — the stock app's parser extracts only `ret:` — so success is
     * "the camera acknowledged the jog", not "the lens moved". Hardware evidence is
     * still required before trusting actual focus movement.
     */
    data class FocusJogResult(
        val code: Int,
        val sent: Boolean = true,
        val error: String? = null,
        val raw: String? = null,
        val ret: Int? = null,
    ) {
        val accepted: Boolean get() = sent && raw != null && raw != "TIMEOUT" &&
            !raw.startsWith("ERROR:") && ret != null && ret >= 0
    }

    /** Current camera parameter snapshot; null while unknown. */
    data class Params(
        val isoIndex: Int? = null,
        val wbIndex: Int? = null,
        val fNumIndex: Int? = null,
        val evIndex: Int? = null,
        val focusIndex: Int? = null,
        val imgSizeIndex: Int? = null,
        val imgFmtIndex: Int? = null,
        val colorIndex: Int? = null,
        val shutterIndex: Int? = null,
        val captureModeIndex: Int? = null,
        val isoOptions: List<String> = emptyList(),
        val wbOptions: List<String> = emptyList(),
        val fNumOptions: List<String> = emptyList(),
        val evOptions: List<String> = emptyList(),
        val shutterOptions: List<String> = emptyList(),
    )

    suspend fun queryIso(): Int? = queryIndex(Codes.CAM_GET_ISO, "iso")
    suspend fun setIso(index: Int) = session.send(Codes.CAM_SET_ISO, "iso:$index;")
    suspend fun queryWb(): Int? = queryIndex(Codes.CAM_GET_WB, "wb")
    suspend fun setWb(index: Int) = session.send(Codes.CAM_SET_WB, "wb:$index;")
    suspend fun queryFNum(): Int? = queryIndex(Codes.CAM_GET_FNUM, "fNum")
    suspend fun setFNum(index: Int) = session.send(Codes.CAM_SET_FNUM, "fNum:$index;")
    suspend fun queryEv(): Int? = queryIndex(Codes.CAM_GET_EV, "ev")
    suspend fun setEv(index: Int) = session.send(Codes.CAM_SET_EV, "ev:$index;")
    suspend fun queryFocus(): Int? = queryIndex(Codes.CAM_GET_FOCUS, "focus")
    suspend fun setFocus(index: Int) = session.send(Codes.CAM_SET_FOCUS, "focus:$index;")
    suspend fun queryImgSize(): Int? = queryIndex(Codes.CAM_GET_IMG_SIZE, "imgSize")
    suspend fun setImgSize(index: Int) = session.send(Codes.CAM_SET_IMG_SIZE, "imgSize:$index;")
    suspend fun queryImgFmt(): Int? = queryIndex(Codes.CAM_GET_IMG_FMT, "imgFmt")
    suspend fun setImgFmt(index: Int) = session.send(Codes.CAM_SET_IMG_FMT, "imgFmt:$index;")
    suspend fun queryColor(): Int? = queryIndex(Codes.CAM_GET_COLOR, "color")
    suspend fun setColor(index: Int) = session.send(Codes.CAM_SET_COLOR, "color:$index;")
    suspend fun queryShutter(): Int? = queryIndex(Codes.CAM_GET_SHUTTER, "shutter")
    suspend fun setShutter(index: Int) = session.send(Codes.CAM_SET_SHUTTER, "shutter:$index;")
    suspend fun queryCaptureMode(): Int? = queryIndex(Codes.CAM_GET_CAPTURE_MODE, "captureMode")
    suspend fun setCaptureMode(index: Int) = session.send(Codes.CAM_SET_CAPTURE_MODE, "captureMode:$index;")

    suspend fun queryBenroInfo(code: Int, key: String): Info =
        when (val result = session.request(code) { it }) {
            is MountSession.CmdResult.Ok -> {
                val frame = result.value
                parseBenroInfo(code, key, frame)
            }
            is MountSession.CmdResult.Timeout -> Info(code, "TIMEOUT", null)
            is MountSession.CmdResult.ProtocolError -> Info(code, "ERROR: ${result.message}", null)
        }

    suspend fun qualifyBenroSetting(
        label: String,
        infoCode: Int,
        setCode: Int,
        key: String,
        index: Int,
    ): QualificationResult {
        val before = queryBenroInfo(infoCode, key)
        if (before.index == null || before.options.isEmpty()) {
            return QualificationResult(
                label, index, before, before, sent = false,
                error = "camera did not provide a current value and option list",
            )
        }
        if (index !in before.options.indices) {
            return QualificationResult(
                label, index, before, before, sent = false,
                error = "index $index is outside camera option range ${before.options.indices}",
            )
        }
        val setReply = session.request(
            code = setCode,
            payload = "$key:$index;",
            timeoutMs = 10_000,
            subtype = 1,
        ) { it }
        val (setRaw, setRet) = when (setReply) {
            is MountSession.CmdResult.Ok -> setReply.value.raw.orEmpty() to setReply.value.int("ret")
            is MountSession.CmdResult.Timeout -> "TIMEOUT" to null
            is MountSession.CmdResult.ProtocolError -> "ERROR: ${setReply.message}" to null
        }
        delay(150)
        val after = queryBenroInfo(infoCode, key)
        return QualificationResult(label, index, before, after, setRaw = setRaw, setRet = setRet)
    }

    /**
     * Focus jog (262), derived from the Benro Connect APK contract:
     * `mod:<m>;f:<s>;` with subtype 1. The stock app repeats this every 300 ms
     * while a focus-speed button is held and sends `mod:0;f:0;` to stop.
     * Speed values observed at call sites: left fast/middle/slow = 6/5/4,
     * right fast/middle/slow = 2/1/0 (mod 1 while moving).
     */
    suspend fun jogFocus(mod: Int, speed: Int): FocusJogResult {
        val valid = (mod == 0 && speed == 0) || (mod == 1 && speed in FOCUS_JOG_SPEEDS)
        if (!valid) return FocusJogResult(
            code = Codes.BenroCamera.SET_FOCUS,
            sent = false,
            error = "Unsupported focus jog mod=$mod speed=$speed",
        )
        return sendBenroJog(Codes.BenroCamera.SET_FOCUS, "mod:$mod;f:$speed;")
    }

    /**
     * Manual-focus adjust inside focus-track mode (311), derived from the Benro
     * Connect APK contract: `mode:<m>;adj:<a>;` with subtype 1. Observed values:
     * add fast/slow = -4/-1, drop fast/slow = 4/1, always mode 1.
     */
    suspend fun adjustManualFocus(mode: Int, adj: Int): FocusJogResult {
        if (mode != 1 || adj !in MANUAL_FOCUS_ADJUSTMENTS) return FocusJogResult(
            code = Codes.BenroCamera.SET_FOCUS_ADJ,
            sent = false,
            error = "Unsupported manual-focus adjustment mode=$mode adj=$adj",
        )
        return sendBenroJog(Codes.BenroCamera.SET_FOCUS_ADJ, "mode:$mode;adj:$adj;")
    }

    /**
     * Semantic wrappers for UI controls. Hardware qualification on the K-3 III
     * established that Polaris passes the signed adjustment through to
     * libgphoto2: positive values move Near and negative values move Far.
     * Keeping that conversion here prevents UI labels from reinterpreting the
     * APK's ambiguous "add"/"drop" names backwards.
     */
    suspend fun adjustManualFocusNear(fast: Boolean = false): FocusJogResult =
        adjustManualFocus(mode = 1, adj = if (fast) 4 else 1)

    suspend fun adjustManualFocusFar(fast: Boolean = false): FocusJogResult =
        adjustManualFocus(mode = 1, adj = if (fast) -4 else -1)

    private suspend fun sendBenroJog(code: Int, payload: String): FocusJogResult {
        val reply = session.request(
            code = code,
            payload = payload,
            timeoutMs = 10_000,
            subtype = 1,
        ) { it }
        return when (reply) {
            is MountSession.CmdResult.Ok -> FocusJogResult(
                code = code,
                raw = reply.value.raw.orEmpty(),
                ret = reply.value.int("ret"),
            )
            is MountSession.CmdResult.Timeout -> FocusJogResult(code, sent = false, error = "TIMEOUT", raw = "TIMEOUT")
            is MountSession.CmdResult.ProtocolError -> FocusJogResult(
                code, sent = false, error = reply.message, raw = "ERROR: ${reply.message}",
            )
        }
    }

    /**
     * Result of a camera liveview (preview) command.
     *
     * Contract (docs/PROTOCOL.md §3.4.2, K-3 III v9d live-verified 2026-09-11):
     *  - SET 291 `state:1;`/`state:0;` replies `state:<n>;ret:<code>;` — success
     *    requires an explicit `ret >= 0`.
     *  - GET 292 (empty payload) replies `state:<0|1>;` with NO `ret` field — a
     *    missing ret is normal here, so [accepted] only applies to SET results.
     * The image data itself streams on the separate 8080 multipart endpoint;
     * these commands only control the stream.
     */
    data class PreviewResult(
        val code: Int,
        val sent: Boolean = true,
        val error: String? = null,
        val raw: String? = null,
        val state: Int? = null,
        val ret: Int? = null,
    ) {
        /** True only when the reply carried an explicit non-negative `ret`. */
        val accepted: Boolean get() = sent && raw != null && raw != "TIMEOUT" &&
            !raw.startsWith("ERROR:") && ret != null && ret >= 0
    }

    /**
     * Start or stop the camera liveview (291), APK contract `state:<0|1>;` with
     * subtype 2. Starting opens the 8080 multipart JPEG stream; stopping closes
     * it. Live-verified on K-3 III v9d (issue #80 evidence).
     */
    suspend fun setCameraPreview(on: Boolean): PreviewResult {
        val payload = "state:${if (on) 1 else 0};"
        return sendBenroPreview(Codes.BenroCamera.SET_CAMERA_PREVIEW, payload)
    }

    /**
     * Query the current liveview state (292), empty payload, subtype 2. The reply
     * carries `state:` but no `ret` — [PreviewResult.accepted] is not meaningful
     * for GET results; use [PreviewResult.state] instead.
     */
    suspend fun queryCameraPreview(): PreviewResult {
        return sendBenroPreview(Codes.BenroCamera.GET_CAMERA_PREVIEW, EMPTY_CONTENT)
    }

    private suspend fun sendBenroPreview(code: Int, payload: String): PreviewResult {
        val reply = session.request(
            code = code,
            payload = payload,
            timeoutMs = 10_000,
            subtype = 2,
        ) { it }
        return when (reply) {
            is MountSession.CmdResult.Ok -> PreviewResult(
                code = code,
                raw = reply.value.raw.orEmpty(),
                state = reply.value.int("state"),
                ret = reply.value.int("ret"),
            )
            is MountSession.CmdResult.Timeout -> PreviewResult(code, sent = false, error = "TIMEOUT", raw = "TIMEOUT")
            is MountSession.CmdResult.ProtocolError -> PreviewResult(
                code, sent = false, error = reply.message, raw = "ERROR: ${reply.message}",
            )
        }
    }

    /** Trigger a single exposure. */
    suspend fun capture() = session.send(
        Codes.CAM_CAPTURE,
        Codes.CAM_CAPTURE_PAYLOAD,
        Codes.CAM_CAPTURE_SUBTYPE,
    )

    /** Query one parameter index by code. Returns null on timeout/absent key. */
    private suspend fun queryIndex(code: Int, key: String): Int? =
        when (val r = session.request(code) { f -> f.fields[key]?.trim()?.toIntOrNull() }) {
            is MountSession.CmdResult.Ok -> r.value
            else -> null
        }

    private fun firstInteger(raw: String?): Int? = raw
        ?.trim()
        ?.removeSuffix(";")
        ?.toIntOrNull()

    private companion object {
        val FOCUS_JOG_SPEEDS = setOf(0, 1, 2, 4, 5, 6)
        val MANUAL_FOCUS_ADJUSTMENTS = setOf(-4, -1, 1, 4)
    }

}

internal fun parseBenroInfo(
    code: Int,
    key: String,
    frame: dev.openpolaris.core.protocol.ResponseParser.Frame,
): CameraController.Info = CameraController.Info(
    code = code,
    raw = frame.raw.orEmpty(),
    index = frame.int("V") ?: frame.int(key) ?: frame.raw
        ?.trim()
        ?.removeSuffix(";")
        ?.toIntOrNull(),
    options = frame["R"].orEmpty()
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty),
    ready = frame.int("RD"),
)
