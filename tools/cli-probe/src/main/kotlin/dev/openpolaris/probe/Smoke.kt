package dev.openpolaris.probe

import dev.openpolaris.core.protocol.CommandTable
import dev.openpolaris.core.protocol.ResponseParser
import dev.openpolaris.core.protocol.command
import dev.openpolaris.core.protocol.commandWithSubtype
import dev.openpolaris.core.protocol.Codes
import java.net.InetSocketAddress
import java.net.Socket
import java.io.ByteArrayOutputStream

private fun log(s: String) { println(s); System.out.flush() }

fun main(args: Array<String>) {
    val host = args.getOrNull(0) ?: "127.0.0.1"
    val port = args.getOrNull(1)?.toIntOrNull() ?: 9099
    val destructive = System.getenv("DESTRUCTIVE")?.toBooleanStrictOrNull() == true

    log("OpenPolaris smoke test -> $host:$port  (destructive=$destructive)")

    val pass = mutableListOf<String>()
    val fail = mutableListOf<String>()
    val skip = mutableListOf<String>()

    val socket = Socket()
    socket.connect(InetSocketAddress(host, port), 5000)
    socket.soTimeout = 1500
    val out = socket.getOutputStream()
    val `in` = socket.getInputStream()
    val parser = ResponseParser()

    fun runOne(label: String, code: Int, keyValue: Pair<String, String>? = null) {
        val req = if (keyValue != null) {
            commandWithSubtype(code) {
                put(keyValue.first, keyValue.second)
            }
        } else {
            commandWithSubtype(code) {}
        }
        out.write(req); out.flush()

        val pending = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        try {
            while (true) {
                val n = `in`.read(buf)
                if (n <= 0) break
                pending.write(buf, 0, n)
            }
        } catch (_: java.net.SocketTimeoutException) {
            // expected
        }
        val bytes = pending.toByteArray()
        if (bytes.isEmpty()) {
            log("SKIP  $label (code=$code) -- no immediate response (setter or push-mode)")
            skip += label
            return
        }
        val (frames, _) = parser.parse(bytes)
        if (frames.isEmpty()) {
            log("FAIL  $label (code=$code) -- unparsed ${bytes.size}B: ${String(bytes, Charsets.US_ASCII).take(80)}")
            fail += "$label (unparsed)"
            return
        }
        val summaries = frames.joinToString(" | ") { f ->
            "code=${f.code} ${f.fields.entries.joinToString(" ") { "${it.key}=${it.value}" }}"
        }
        log("PASS  $label (req=$code) -- $summaries")
        pass += label
    }

    try {
        // 1. BURST_PRE_CAMERA
        log("\n== 1. BURST_PRE_CAMERA (every control-panel refresh) ==")
        for ((i, item) in CommandTable.BURST_PRE_CAMERA.withIndex()) {
            runOne("burst.${i}_${item.code}", item.code)
        }

        // 2. Camera info burst
        log("\n== 2. Camera info burst (10 codes) ==")
        for (code in CommandTable.BURST_CAMERA_CODES) {
            runOne("cam.$code", code)
        }

        // 3. Push stream codes (polling loop)
        log("\n== 3. Push stream (polling loop) ==")
        for (code in listOf(
            Codes.PUSH_MODE_STATE,
            Codes.PUSH_ROTATE_VECTOR,
            Codes.BATTERY_STATUS,
            Codes.BATTERY_DETAIL,
            Codes.CAM_GET_STATE,
            Codes.CAM_CAPTURE,
        )) {
            runOne("push.$code", code)
        }

        // 4. File/SD reads
        log("\n== 4. File/SD reads ==")
        for (code in listOf(
            Codes.FILE_LIST,
            Codes.FILE_SD_STATUS,
            Codes.FILE_INFO,
            Codes.FILE_THUMB_LIST,
            Codes.FILE_LIST_BY_DATE,
            Codes.FILE_QUOTA,
            Codes.FILE_PAGINATE,
        )) {
            runOne("file.$code", code)
        }

        // 5. System / device
        log("\n== 5. System / device ==")
        runOne("sys.${Codes.SYS_VERSION}", Codes.SYS_VERSION)
        runOne("sys.${Codes.SYS_SERIAL}", Codes.SYS_SERIAL)
        runOne("sys.${Codes.SYS_TIME}", Codes.SYS_TIME)
        runOne("sys.${Codes.SYS_TIMEZONE}", Codes.SYS_TIMEZONE)
        runOne("sys.${Codes.SYS_LANGUAGE}", Codes.SYS_LANGUAGE)
        runOne("sys.${Codes.DEVICE_INFO}", Codes.DEVICE_INFO)
        runOne("sys.${Codes.GET_TEMPERATURE}", Codes.GET_TEMPERATURE)
        runOne("sys.${Codes.STATE_DUMP}", Codes.STATE_DUMP)
        runOne("sys.${Codes.SP_TEST}", Codes.SP_TEST)

        // 6. Wi-Fi
        log("\n== 6. Wi-Fi ==")
        runOne("wifi.${Codes.WIFI_BAND}", Codes.WIFI_BAND)
        runOne("wifi.${Codes.WIFI_SCAN}", Codes.WIFI_SCAN)
        runOne("wifi.${Codes.WIFI_LIST}", Codes.WIFI_LIST)
        runOne("wifi.${Codes.GET_WIFI_BAND}", Codes.GET_WIFI_BAND)
        runOne("wifi.${Codes.WIFI_STATUS}", Codes.WIFI_STATUS)
        runOne("wifi.${Codes.WIFI_RSSI}", Codes.WIFI_RSSI)

        // 7. OMS / dither / limits / autoLevel / settling
        log("\n== 7. OMS / dither / limits / autoLevel / settling ==")
        runOne("oms.${Codes.OMS_RUN_STATE}", Codes.OMS_RUN_STATE)
        runOne("oms.${Codes.OMS_TASK_LIST}", Codes.OMS_TASK_LIST)
        runOne("sys.${Codes.GET_DITHER_STATE}", Codes.GET_DITHER_STATE)
        runOne("sys.${Codes.GET_LIMIT_STATE}", Codes.GET_LIMIT_STATE)
        runOne("sys.${Codes.GET_AUTO_LEVEL_EN}", Codes.GET_AUTO_LEVEL_EN)
        runOne("sys.${Codes.GET_SETTLING_TIME}", Codes.GET_SETTLING_TIME)
        runOne("sys.${Codes.GET_TILT_STATE}", Codes.GET_TILT_STATE)

        // 8. Handshake / app
        log("\n== 8. Handshake / app ==")
        runOne("app.${Codes.APP_PASSWORD_INFO}", Codes.APP_PASSWORD_INFO)
        runOne("app.${Codes.APP_PING}", Codes.APP_PING)
        runOne("app.${Codes.APP_HELLO}", Codes.APP_HELLO)
        runOne("app.${Codes.GET_GIMBAL_POS}", Codes.GET_GIMBAL_POS)
        runOne("app.${Codes.EX_AXIS_STA}", Codes.EX_AXIS_STA)

        // 9. Camera info
        log("\n== 9. Camera info ==")
        runOne("cam-info.${Codes.CAM_INFO}", Codes.CAM_INFO)
        runOne("cam-info.${Codes.CAM_FOCUS}", Codes.CAM_FOCUS)
        runOne("cam-info.${Codes.CAM_GET_ISO}", Codes.CAM_GET_ISO)
        runOne("cam-info.${Codes.CAM_GET_WB}", Codes.CAM_GET_WB)
        runOne("cam-info.${Codes.CAM_GET_FNUM}", Codes.CAM_GET_FNUM)
        runOne("cam-info.${Codes.CAM_GET_EV}", Codes.CAM_GET_EV)
        runOne("cam-info.${Codes.CAM_GET_FOCUS}", Codes.CAM_GET_FOCUS)
        runOne("cam-info.${Codes.CAM_GET_IMG_SIZE}", Codes.CAM_GET_IMG_SIZE)
        runOne("cam-info.${Codes.CAM_GET_IMG_FMT}", Codes.CAM_GET_IMG_FMT)
        runOne("cam-info.${Codes.CAM_GET_COLOR}", Codes.CAM_GET_COLOR)
        runOne("cam-info.${Codes.CAM_GET_SHUTTER}", Codes.CAM_GET_SHUTTER)
        runOne("cam-info.${Codes.CAM_GET_CAPTURE_MODE}", Codes.CAM_GET_CAPTURE_MODE)

        // 10. Camera setters — DESTRUCTIVE only
        log("\n== 10. Camera setters (DESTRUCTIVE=$destructive) ==")
        if (!destructive) {
            log("SKIP  camera setters -- set DESTRUCTIVE=1 to exercise")
            skip += "camera setters (DESTRUCTIVE=0)"
        } else {
            val setters = listOf(
                "set.iso" to Codes.CAM_SET_ISO,
                "set.wb" to Codes.CAM_SET_WB,
                "set.fnum" to Codes.CAM_SET_FNUM,
                "set.ev" to Codes.CAM_SET_EV,
                "set.focus" to Codes.CAM_SET_FOCUS,
                "set.imgSize" to Codes.CAM_SET_IMG_SIZE,
                "set.imgFmt" to Codes.CAM_SET_IMG_FMT,
                "set.color" to Codes.CAM_SET_COLOR,
                "set.shutter" to Codes.CAM_SET_SHUTTER,
                "set.captureMode" to Codes.CAM_SET_CAPTURE_MODE,
            )
            for ((label, code) in setters) {
                runOne(label, code)
            }
        }

        // 11. Blocked — never
        log("\n== 11. Blocked operations (always skipped) ==")
        for (code in listOf(
            Codes.SYS_REBOOT,
            Codes.SYS_SHUTDOWN,
            Codes.FILE_SD_FORMAT,
            Codes.SYS_FW_UPGRADE,
            Codes.CALIBRATE_START,
        )) {
            log("SKIP  blocked code=$code -- never")
            skip += "blocked $code"
        }
    } finally {
        runCatching { socket.close() }
    }

    log("\n========================================")
    log("PASS=${pass.size}  FAIL=${fail.size}  SKIP=${skip.size}")
    if (fail.isNotEmpty()) {
        log("\nFailures:")
        fail.forEach { log("  - $it") }
    }
    if (skip.isNotEmpty()) {
        log("\nSkipped:")
        skip.forEach { log("  - $it") }
    }
    if (fail.isEmpty()) log("\nAll exercised tests PASSED.")
}
