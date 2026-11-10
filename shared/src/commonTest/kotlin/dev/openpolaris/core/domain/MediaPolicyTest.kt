package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaPolicyTest {
    @Test
    fun classifiesAstroMediaWithoutTreatingUnknownAsImage() {
        assertEquals(MediaKind.IMAGE, MediaPolicy.kind("SP_0001.DNG"))
        assertEquals(MediaKind.IMAGE, MediaPolicy.kind("preview.jpg"))
        assertEquals(MediaKind.VIDEO, MediaPolicy.kind("clip.MP4"))
        assertEquals(MediaKind.OTHER, MediaPolicy.kind("README.TXT"))
    }

    @Test
    fun acceptsOnlyMountLocalNonTraversalPaths() {
        assertTrue(MediaPolicy.isSafeMountPath("/app/sd/normal/SP_0001.dng"))
        assertTrue(MediaPolicy.isSafeMountPath("/app/sd/video/clip.mp4"))
        assertFalse(MediaPolicy.isSafeMountPath("/app/sd/../etc/passwd"))
        assertFalse(MediaPolicy.isSafeMountPath("/tmp/SP_0001.dng"))
        assertFalse(MediaPolicy.isSafeMountPath("http://mount/app/sd/SP_0001.dng"))
        assertFalse(MediaPolicy.isSafeMountPath("/app/sd/normal\\SP_0001.dng"))
        assertFalse(MediaPolicy.isSafeMountPath("/app/sd/normal/SP_0001.dng?download=1"))
    }
}
