package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes
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
