package dev.openpolaris.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BtFrameTest {

    @Test
    fun buildEmptyBody() {
        // No fields → body collapses to ""; envelope still has the inner "#" pair
        val bytes = BtFrame.build(BtFrame.BT_HELLO)
        assertEquals("code:1;#", bytes.decodeToString())
    }

    @Test
    fun buildWithFields() {
        val bytes = BtFrame.build(
            code = 524,
            fields = linkedMapOf("state" to "0"),
        )
        assertEquals("code:524;#state:0;#", bytes.decodeToString())
    }

    @Test
    fun buildIsAscii() {
        // 7-bit clean per Ascii.encodeAscii convention
        for (b in BtFrame.build(BtFrame.BT_HELLO, linkedMapOf("a" to "b"))) {
            assertTrue((b.toInt() and 0x80) == 0, "byte $b is not 7-bit clean")
        }
    }

    @Test
    fun parseReusesWifiCodes() {
        // Camera GET_ISO (258) over BT — same code, different envelope
        val wire = "code:258;#iso:100;wb:auto;#".encodeToByteArray()
        val p = BtFrame.parse(wire)
        assertNotNull(p)
        assertEquals(258, p.code)
        assertEquals("100", p["iso"])
        assertEquals("auto", p["wb"])
        assertEquals(100, p.int("iso"))
    }

    @Test
    fun parseEmptyBody() {
        val p = BtFrame.parse("code:1;#".encodeToByteArray())
        assertNotNull(p)
        assertEquals(1, p.code)
        assertTrue(p.fields.isEmpty())
    }

    @Test
    fun parseRejectsWifiFrame() {
        // A WiFi frame must NOT parse as a BT frame
        assertNull(BtFrame.parse("1&284&2&-100#".encodeToByteArray()))
    }

    @Test
    fun parseRejectsGarbage() {
        assertNull(BtFrame.parse("not-a-bt-frame".encodeToByteArray()))
        assertNull(BtFrame.parse("code:notanint;#".encodeToByteArray()))
    }

    @Test
    fun btHandshakeCodesAre1Through5() {
        // Sanity: the BT-only handshake codes are confined to 1..5
        assertEquals(1, BtFrame.BT_HELLO)
        assertEquals(2, BtFrame.BT_HELLO_ACK)
        assertEquals(3, BtFrame.BT_TOKEN_EXCHANGE)
        assertEquals(4, BtFrame.BT_TOKEN_EXCHANGE_ACK)
        assertEquals(5, BtFrame.BT_WIFI_TRIGGER)
    }

    @Test
    fun gattUuidsMatchPlanningDoc() {
        // PLANNING-2026-08.md §Bluetooth
        assertEquals("0000fff0-0000-1000-8000-00805f9b34fb", BtFrame.SERVICE_UUID)
        assertEquals("0000fff1-0000-1000-8000-00805f9b34fb", BtFrame.TX_CHARACTERISTIC_UUID)
        assertEquals("0000fff2-0000-1000-8000-00805f9b34fb", BtFrame.RX_CHARACTERISTIC_UUID)
        assertEquals(512, BtFrame.PREFERRED_MTU)
    }
}
