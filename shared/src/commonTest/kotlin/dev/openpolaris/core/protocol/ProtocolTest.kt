package dev.openpolaris.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandBuilderTest {
    @Test
    fun `empty payload uses -100 sentinel`() {
        val bytes = command(Codes.PUSH_MODE_STATE)
        assertEquals("1&284&2&-100#", bytes.decodeToString())
    }

    @Test
    fun `tracking start sidereal matches stock app frame`() {
        val bytes = command(Codes.SET_TRACK_AU_STATE) {
            put("state", 1); put("speed", 0)
        }
        assertEquals("1&531&2&state:1;speed:0;#", bytes.decodeToString())
    }

    @Test
    fun `halfSpeed enable sends inverted zero`() {
        // Quirk: halfSpeed:0 ENABLES half-speed (PROTOCOL.md §5.1)
        val bytes = command(Codes.SET_TRACK_HALF_SPEED) { put("halfSpeed", 0) }
        assertEquals("1&536&2&halfSpeed:0;#", bytes.decodeToString())
    }

    @Test
    fun `goto frame contains all keys in order`() {
        val bytes = command(Codes.SET_GOTO_AU_STATE) {
            put("state", 1); put("yaw", "45.0"); put("pitch", "30.0")
            put("lat", "51.5"); put("track", 1); put("speed", 0); put("lng", "-0.1")
        }
        assertEquals(
            "1&519&2&state:1;yaw:45.0;pitch:30.0;lat:51.5;track:1;speed:0;lng:-0.1;#",
            bytes.decodeToString()
        )
    }
}

class ResponseParserTest {
    private val parser = ResponseParser()

    @Test
    fun `parses status poll response`() {
        val wire = "1&284&2&mode:3;state:1;remNum:2;runTime:120;battery:87;extra:unknown;#"
        val (frames, consumed) = parser.parse(wire.toByteArray(Charsets.US_ASCII))
        assertEquals(1, frames.size)
        assertEquals(wire.length, consumed)
        val f = frames[0]
        assertEquals(284, f.code)
        assertEquals(3, f.int("mode"))
        assertEquals(87, f.int("battery"))
        assertNull(f["missing"])
        assertTrue("extra" in f.fields) // tolerant: unknown keys retained, never fatal
    }

    @Test
    fun `parses gimbal position floats`() {
        val wire = "1&517&2&yaw:123.45;pitch:-12.5;roll:0.25;#"
        val (frames, _) = parser.parse(wire.toByteArray())
        val f = frames.single()
        assertEquals(123.45f, f.float("yaw")!!, 0.001f)
        assertEquals(-12.5f, f.float("pitch")!!, 0.001f)
    }

    @Test
    fun `handles multiple frames and keeps partial tail`() {
        val a = "1&284&2&mode:1;#"
        val b = "1&517&2&yaw:1.0;#"
        val c = "1&517&2&yaw:1.0;pit" // incomplete frame, no terminator
        val buf = (a + b + c).toByteArray()
        val (frames, consumed) = parser.parse(buf)
        assertEquals(listOf(284, 517), frames.map { it.code })
        assertEquals(a.length + b.length, consumed) // partial tail excluded
    }

    @Test
    fun `-100 payload yields empty fields`() {
        val wire = "1&539&2&-100#"
        val (frames, _) = parser.parse(wire.toByteArray())
        assertTrue(frames.single().fields.isEmpty())
    }

    @Test
    fun `value containing colon parses after first colon`() {
        val fields = ResponseParser.parseFields("weird:key:with:colons;x:1;")
        assertEquals("key:with:colons", fields["weird"])
        assertEquals("1", fields["x"])
    }
}
