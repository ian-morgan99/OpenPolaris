package dev.openpolaris.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AstroWebUiMediaTest {
    @Test
    fun parsesSourceAlignedMediaList() {
        val result = AstroWebUiMediaParser.parseList(
            """
            {"ok":true,"count":2,"items":[
              {"path":"normal/IMG_0001.jpg","cat":"normal","size":1234,"mtime":1700000000},
              {"path":"Lapse/session_01/frame.jpeg","cat":"Lapse","size":4567,"mtime":1700000001}
            ]}
            """.trimIndent(),
        )

        val success = assertIs<AstroMediaListResult.Success>(result)
        assertEquals(2, success.reportedCount)
        assertEquals("normal/IMG_0001.jpg", success.items.first().path)
        assertEquals("Lapse", success.items.last().category)
    }

    @Test
    fun rejectsUnsafeAndMismatchedMediaEntries() {
        val traversal = AstroWebUiMediaParser.parseList(
            """{"ok":true,"count":1,"items":[{"path":"normal/../secret.jpg","cat":"normal","size":1,"mtime":1}]}""",
        )
        val mismatched = AstroWebUiMediaParser.parseList(
            """{"ok":true,"count":1,"items":[{"path":"HDR/image.jpg","cat":"normal","size":1,"mtime":1}]}""",
        )

        assertIs<AstroMediaListResult.Failure>(traversal)
        assertIs<AstroMediaListResult.Failure>(mismatched)
    }

    @Test
    fun enforcesEndpointAndHostPolicy() {
        assertTrue(AstroMediaPolicy.isSafeHost("192.168.4.1", 8080))
        assertFalse(AstroMediaPolicy.isSafeHost("192.168.4.1/path", 8080))
        assertTrue(AstroMediaPolicy.isSafePath("focusStack/session/frame.JPG"))
        assertFalse(AstroMediaPolicy.isSafePath("normal/../frame.jpg"))
        assertFalse(AstroMediaPolicy.isSafePath("normal/frame.png"))
        assertFalse(AstroMediaPolicy.isSafePath("unknown/frame.jpg"))
    }
}
