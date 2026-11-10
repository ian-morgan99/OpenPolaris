package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class CameraReleaseModeTest {
    @Test
    fun labelsKeepManualAndBulbDistinct() {
        assertEquals(CameraReleaseMode.MANUAL, CameraReleaseMode.fromOption("M"))
        assertEquals(CameraReleaseMode.MANUAL, CameraReleaseMode.fromOption(" manual "))
        assertEquals(CameraReleaseMode.BULB, CameraReleaseMode.fromOption("B"))
        assertEquals(CameraReleaseMode.BULB, CameraReleaseMode.fromOption("Bulb"))
    }

    @Test
    fun unknownDoesNotBecomeAReleaseMode() {
        assertEquals(CameraReleaseMode.UNKNOWN, CameraReleaseMode.fromOption(null))
        assertEquals(CameraReleaseMode.UNKNOWN, CameraReleaseMode.fromOption(""))
        assertEquals(CameraReleaseMode.OTHER, CameraReleaseMode.fromOption("1/100"))
    }
}
