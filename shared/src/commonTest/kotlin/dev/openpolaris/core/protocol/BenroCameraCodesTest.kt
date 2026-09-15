package dev.openpolaris.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the camera subset extracted from Benro Connect v3.0.30 build 240930. */
class BenroCameraCodesTest {
    @Test
    fun `setting and info codes match Benro PolarisCMD`() {
        val c = Codes.BenroCamera
        assertEquals(258, c.SET_ISO)
        assertEquals(259, c.SET_WB)
        assertEquals(260, c.SET_EV)
        assertEquals(261, c.SET_SHUTTER)
        assertEquals(262, c.SET_FOCUS)
        assertEquals(263, c.SET_VIDEO_RECORD_STATUS)
        assertEquals(264, c.SET_PHOTO_RECORD_STATUS)
        assertEquals(265, c.GET_ISO_INFO)
        assertEquals(266, c.GET_WB_INFO)
        assertEquals(267, c.GET_EV_INFO)
        assertEquals(268, c.GET_SHUTTER_INFO)
        assertEquals(275, c.GET_FNUM_INFO)
        assertEquals(276, c.SET_FNUM)
        assertEquals(282, c.GET_IMG_FORMAT)
        assertEquals(311, c.SET_FOCUS_ADJ)
    }

    @Test
    fun `workflow codes are individually pinned`() {
        val c = Codes.BenroCamera
        assertEquals(270, c.FOCUS_STACK)
        assertEquals(271, c.PANORAMIC)
        assertEquals(272, c.DELAY_SHOT)
        assertEquals(277, c.SUN_SHOT)
        assertEquals(280, c.HDR)
        assertEquals(289, c.REMOVE_PEOPLE_SHOT)
        assertEquals(305, c.HOLY_GRAIL)
    }

    @Test
    fun `control-mode and exposure-time codes are individually pinned`() {
        val c = Codes.BenroCamera
        assertEquals(296, c.GET_CONTROL_MODE)
        assertEquals(297, c.SET_CONTROL_MODE)
        assertEquals(298, c.GET_EX_TIME)
        assertEquals(299, c.SET_EX_TIME)
        assertEquals(306, c.GET_INTERVAL_TYPE)
        assertEquals(307, c.SET_INTERVAL_TYPE)
    }

    /**
     * #62: until every camera parameter mapping has captured-wire + physical
     * read-back evidence, the release safety gate must stay closed so inferred
     * mappings never hit hardware. Flipping this to `true` is a deliberate,
     * reviewed decision that should land with the physical-qualification
     * results (K-3 III + K-1 II).
     */
    @Test
    fun `release safety gate stays closed until mappings are physically verified`() {
        assertEquals(false, Codes.CAMERA_PARAMETERS_VERIFIED)
    }
}
