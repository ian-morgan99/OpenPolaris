@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.openpolaris.core.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import dev.openpolaris.core.protocol.Codes

/** APK-derived contract tests for video (263) and photo (264 subtype 2) status. */
class CameraRecordStatusContractTest {
    private fun newSession(
        conn: FakeConnection,
        scope: CoroutineScope,
    ): Pair<MountSession, CameraController> {
        val session = MountSession({ conn }, readerScope = scope)
        return session to CameraController(session)
    }

    @Test
    fun `video start sends subtype 2 state 1 and requires matching echo`() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&263&2&state:1;#".toByteArray(Charsets.US_ASCII)
        val (session, controller) = newSession(conn, backgroundScope)
        session.connect()

        val result = controller.setVideoRecordStatus(recording = true)

        assertEquals("1&263&2&state:1;#", String(conn.written.last(), Charsets.US_ASCII))
        assertTrue(result.accepted)
        assertEquals("recordStart", result.recordState)
        assertEquals(0, result.ret)
        session.disconnect()
    }

    @Test
    fun `video error state is never accepted`() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&263&2&state:-1;#".toByteArray(Charsets.US_ASCII)
        val (session, controller) = newSession(conn, backgroundScope)
        session.connect()

        val result = controller.setVideoRecordStatus(recording = true)

        assertFalse(result.accepted)
        assertEquals(-1, result.ret)
        session.disconnect()
    }

    @Test
    fun `photo start sends subtype 2 with exact state bulb c fields`() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&264&2&state:1;#".toByteArray(Charsets.US_ASCII)
        val (session, controller) = newSession(conn, backgroundScope)
        session.connect()

        val result = controller.setPhotoRecordStatus(
            recording = true,
            bulbSeconds = 12,
            continuationCount = -1,
        )

        assertEquals("1&264&2&state:1;bulb:12;c:-1;#", String(conn.written.last(), Charsets.US_ASCII))
        assertTrue(result.accepted)
        assertEquals("1", result.state)
        session.disconnect()
    }

    @Test
    fun `photo reply with mismatched state is not accepted`() = runTest {
        val conn = FakeConnection()
        conn.responses += "1&264&2&state:0;#".toByteArray(Charsets.US_ASCII)
        val (session, controller) = newSession(conn, backgroundScope)
        session.connect()

        val result = controller.setPhotoRecordStatus(recording = true)

        assertFalse(result.accepted)
        session.disconnect()
    }

    @Test
    fun `invalid bulb or continuation values fail before writing`() = runTest {
        val conn = FakeConnection()
        val (session, controller) = newSession(conn, backgroundScope)
        session.connect()
        val writesBefore = conn.written.size

        val negativeBulb = controller.setPhotoRecordStatus(recording = true, bulbSeconds = -1)
        val invalidCount = controller.setPhotoRecordStatus(recording = true, continuationCount = -2)

        assertFalse(negativeBulb.sent)
        assertFalse(invalidCount.sent)
        assertEquals(writesBefore, conn.written.size)
        session.disconnect()
    }

    @Test
    fun `photo status does not alter subtype 4 still capture contract`() = runTest {
        val conn = FakeConnection()
        val (session, controller) = newSession(conn, backgroundScope)
        session.connect()

        controller.capture()

        assertEquals(
            "1&264&4&state:1;bulb:0;c:-1;#",
            String(conn.written.last(), Charsets.US_ASCII),
        )
        session.disconnect()
    }

    @Test
    fun `status constants keep the overloaded 264 contract explicit`() {
        assertEquals(263, Codes.BenroCamera.SET_VIDEO_RECORD_STATUS)
        assertEquals(264, Codes.BenroCamera.SET_PHOTO_RECORD_STATUS)
        assertEquals(264, Codes.CAM_CAPTURE)
        assertEquals(4, Codes.CAM_CAPTURE_SUBTYPE)
    }
}
