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

    // -------- Response envelope (`<code>@<k:v>;...;#`) — captured live 2026-08-30 --------

    @Test
    fun `parses battery response 778 from live wire`() {
        val wire = "778@capacity:63;charge:1;#"
        val (frames, consumed) = parser.parse(wire.toByteArray())
        assertEquals(1, frames.size)
        assertEquals(wire.length, consumed)
        assertEquals(778, frames[0].code)
        assertEquals(63, frames[0].int("capacity"))
        assertEquals(1, frames[0].int("charge"))
    }

    @Test
    fun `parses SD card response 775 from live wire`() {
        val wire = "775@status:1;totalspace:121866;freespace:121799;usespace:67;#"
        val (frames, _) = parser.parse(wire.toByteArray())
        val f = frames.single()
        assertEquals(775, f.code)
        assertEquals(1, f.int("status"))
        assertEquals(121866, f.int("totalspace"))
        assertEquals(121799, f.int("freespace"))
        assertEquals(67, f.int("usespace"))
    }

    @Test
    fun `parses OMS state response 824 from live wire`() {
        val wire = "824@state:0;#"
        val (frames, _) = parser.parse(wire.toByteArray())
        val f = frames.single()
        assertEquals(824, f.code)
        assertEquals(0, f.int("state"))
    }

    @Test
    fun `parses wifi band response 802 from live wire`() {
        val wire = "802@band:0;#"
        val (frames, _) = parser.parse(wire.toByteArray())
        val f = frames.single()
        assertEquals(802, f.code)
        assertEquals(0, f.int("band"))
    }

    @Test
    fun `parses ret-style error response 809 from live wire`() {
        val wire = "809@ret:-1;#"
        val (frames, _) = parser.parse(wire.toByteArray())
        val f = frames.single()
        assertEquals(809, f.code)
        assertEquals(-1, f.int("ret"))
    }

    @Test
    fun `handles mixed request and response envelopes in one buffer`() {
        val a = "1&284&2&mode:1;#"            // request-shape status
        val b = "778@capacity:50;charge:0;#"   // response-shape battery
        val c = "1&517&2&yaw:0;#"              // request-shape position
        val (frames, _) = parser.parse((a + b + c).toByteArray())
        assertEquals(listOf(284, 778, 517), frames.map { it.code })
        assertEquals(50, frames[1].int("capacity"))
    }

    @Test
    fun `response envelope with empty payload is accepted`() {
        val wire = "284@;#"
        val (frames, _) = parser.parse(wire.toByteArray())
        assertEquals(1, frames.size)
        assertEquals(284, frames[0].code)
        assertTrue(frames[0].fields.isEmpty())
    }

    // -------- Wire-format quirks discovered 2026-08-31 (Phase 3 live capture) --------

    @Test
    fun `parses 264 error with missing trailing semicolon (quirk 1)`() {
        // Live: "264@state:-1#" — note the absent ';' before '#'
        val wire = "264@state:-1#"
        val (frames, _) = parser.parse(wire.toByteArray())
        val f = frames.single()
        assertEquals(264, f.code)
        assertEquals(-1, f.int("state"))
    }

    @Test
    fun `strips -100 echo prefix from 258 camera-code error (quirk 2)`() {
        // Live: "258@-100ret:-1;#" — the -100 is the request payload echoed, with no ';' before ret
        val wire = "258@-100ret:-1;#"
        val (frames, _) = parser.parse(wire.toByteArray())
        val f = frames.single()
        assertEquals(258, f.code)
        assertEquals(-1, f.int("ret"))
    }

    @Test
    fun `strips -100 echo prefix from 259 camera-code error (quirk 3)`() {
        val wire = "259@-100ret:-1;#"
        val (frames, _) = parser.parse(wire.toByteArray())
        val f = frames.single()
        assertEquals(259, f.code)
        assertEquals(-1, f.int("ret"))
    }

    @Test
    fun `strips -100 echo prefix from 260 camera-code error (quirk 4)`() {
        val wire = "260@-100ret:-1;#"
        val (frames, _) = parser.parse(wire.toByteArray())
        val f = frames.single()
        assertEquals(260, f.code)
        assertEquals(-1, f.int("ret"))
    }

    @Test
    fun `strips -100 echo prefix from 261 camera-code error (quirk 5)`() {
        val wire = "261@-100ret:-1;#"
        val (frames, _) = parser.parse(wire.toByteArray())
        val f = frames.single()
        assertEquals(261, f.code)
        assertEquals(-1, f.int("ret"))
    }

    @Test
    fun `parses 263 state-only single-field error (quirk 6)`() {
        val wire = "263@state:-1;#"
        val (frames, _) = parser.parse(wire.toByteArray())
        val f = frames.single()
        assertEquals(263, f.code)
        assertEquals(-1, f.int("state"))
    }

    @Test
    fun `parses 525 connect-burst Temp-angle-aid envelope with no colon (quirk 7)`() {
        // Live (connect-burst): "525@Temp<a509ca361e0000275a>;#" — XML-like wrapper, no ':'
        val wire = "525@Temp<a509ca361e0000275a>;#"
        val (frames, _) = parser.parse(wire.toByteArray())
        val f = frames.single()
        assertEquals(525, f.code)
        assertTrue("Temp" in f.fields, "expected Temp key, got keys=${f.fields.keys}")
        assertEquals("<a509ca361e0000275a>", f["Temp"])
    }

    @Test
    fun `strips -100 echo prefix from 276 camera-code error (quirk 8)`() {
        val wire = "276@-100ret:-1;#"
        val (frames, _) = parser.parse(wire.toByteArray())
        val f = frames.single()
        assertEquals(276, f.code)
        assertEquals(-1, f.int("ret"))
    }

    @Test
    fun `parses 264 state-mirror with transformed negative value`() {
        // Observed: request 264@state:1; yielded "264@state:-1002;#" — echo is transformed
        // Encoding of -1002 is unknown (bitfield? hex?). Parser must accept the value as-is.
        val wire = "264@state:-1002;#"
        val (frames, _) = parser.parse(wire.toByteArray())
        val f = frames.single()
        assertEquals(264, f.code)
        assertEquals(-1002, f.int("state"))
    }

    @Test
    fun `connect-burst stream with 525 and standard 524 parses as 2 frames`() {
        // Real connect-burst: 525 + 524 interleave during TCP-connect
        val wire = "525@Temp<a509ca361e0000275a>;#524@state:0;#"
        val (frames, _) = parser.parse(wire.toByteArray())
        assertEquals(2, frames.size)
        assertEquals(525, frames[0].code)
        assertEquals("<a509ca361e0000275a>", frames[0]["Temp"])
        assertEquals(524, frames[1].code)
        assertEquals(0, frames[1].int("state"))
    }

    @Test
    fun `mixed camera-error quirks and standard responses parse cleanly`() {
        // Realistic burst: 258 (camera error) + 524 (state) + 778 (battery) all interleaved
        val wire = "258@-100ret:-1;#524@state:0;#778@capacity:63;charge:1;#"
        val (frames, _) = parser.parse(wire.toByteArray())
        assertEquals(3, frames.size)
        assertEquals(258, frames[0].code)
        assertEquals(-1, frames[0].int("ret"))
        assertEquals(524, frames[1].code)
        assertEquals(0, frames[1].int("state"))
        assertEquals(778, frames[2].code)
        assertEquals(63, frames[2].int("capacity"))
        assertEquals(1, frames[2].int("charge"))
    }

    @Test
    fun `parseFields directly handles -100 empty sentinel as empty map`() {
        // Regression: -100 alone (no ';' after, no other segment) should still yield {}
        val fields = ResponseParser.parseFields("-100")
        assertTrue(fields.isEmpty(), "got=$fields")
    }
}
