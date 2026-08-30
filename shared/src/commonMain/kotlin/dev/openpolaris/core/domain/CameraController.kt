package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes

/**
 * Camera parameter control (ISO / WB / aperture / EV / capture).
 *
 * Semantics recovered from polestar_app: values are INDICES into firmware-sorted
 * option lists (SP_SetCameraIsoIndex etc.), not raw ISO/shutter numbers. The app
 * queries the list, presents it sorted, and sends the chosen index.
 *
 * WARNING: numeric codes are inferred (see Codes.kt). Callers must keep controls
 * disabled until validated on hardware.
 */
class CameraController(private val session: MountSession) {

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

    /** Trigger a single exposure. */
    suspend fun capture() = session.send(Codes.CAM_CAPTURE)

    /** Query one parameter index by code. Returns null on timeout/absent key. */
    private suspend fun queryIndex(code: Int, key: String): Int? =
        when (val r = session.request(code) { f -> f.fields[key]?.trim()?.toIntOrNull() }) {
            is MountSession.CmdResult.Ok -> r.value
            else -> null
        }
}
