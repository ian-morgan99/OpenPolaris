package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.Codes
import dev.openpolaris.core.protocol.CommandBuilder

/**
 * Source-aligned contract for the Astro WebUI's ordinary single-still path.
 *
 * This is deliberately a wire/evidence seam, not a live HTTP client. The
 * resident WebUI writes the request through `/api/link/send`, but its HTTP
 * response only proves that the resident bridge accepted the write. A caller
 * must still feed the resulting 264 lifecycle and 773 publication into the
 * normal [CaptureEvidenceGate] before declaring a photograph complete.
 */
object AstroCaptureContract {
    const val CAPTURE_CODE = Codes.CAM_CAPTURE
    const val CAPTURE_SUBTYPE = 2
    const val CAPTURE_PAYLOAD = "state:1;bulb:0;c:-1;"

    /** Exact source-compatible 264 frame used by Astro WebUI ordinary capture. */
    fun ordinaryStillFrame(): ByteArray = CommandBuilder(CAPTURE_CODE, CAPTURE_SUBTYPE).apply {
        putRaw(CAPTURE_PAYLOAD)
    }.build()

    /**
     * The WebUI link endpoint's positive HTTP result is queue/write evidence,
     * never shutter or file-publication evidence.
     */
    fun httpAcceptanceIsCaptureComplete(): Boolean = false

    /** A 272 Astro programme start is not a published still either. */
    fun programmeProgressIsCaptureComplete(): Boolean = false
}
