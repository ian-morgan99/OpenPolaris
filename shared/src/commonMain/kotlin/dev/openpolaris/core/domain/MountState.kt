package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.ResponseParser

/**
 * Internal helpers for parsing `key:value;` segments out of a raw payload.
 * Used by the [Task] / [TaskList] parsers to read fields before the standard
 * field-map collapses duplicate keys.
 */
private fun String.intField(key: String): Int? =
    split(';')
        .firstOrNull { it.startsWith("$key:") }
        ?.substringAfter(':')
        ?.trim()
        ?.toIntOrNull()

private fun String.strField(key: String): String? =
    split(';')
        .firstOrNull { it.startsWith("$key:") }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/** Mount operating mode reported by 284. */
enum class MountMode { PANORAMA, TIMELAPSE, ASTRO, UNKNOWN }

/** High-level mount state derived from 284 pushes and command results. */
data class MountState(
    val connected: Boolean = false,
    val mode: MountMode = MountMode.UNKNOWN,
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val tracking: Boolean? = null,
    val halfSpeed: Boolean = false,
    val ahrsEnabled: Boolean = false,
) {
    companion object {
        /** Derive state from a 284 push frame. Tolerant of missing fields. */
        fun fromFrame284(f: ResponseParser.Frame): MountState {
            // Mode is an int; observed values map to app modes. Unknown stays UNKNOWN.
            val mode = when (f.int("mode")) {
                0 -> MountMode.PANORAMA
                1 -> MountMode.TIMELAPSE
                2 -> MountMode.ASTRO
                else -> MountMode.UNKNOWN
            }
            val batt = f.int("battery")
            return MountState(
                connected = true,
                mode = mode,
                batteryPercent = batt?.takeIf { it in 0..100 },
                charging = (f.int("charge") ?: 0) != 0,
                tracking = f.int("track")?.let { it != 0 },
                halfSpeed = (f.int("halfSpeed") ?: 0) != 0,
                ahrsEnabled = (f.int("ahrs") ?: 0) != 0,
            )
        }
    }
}

/** Gimbal position in degrees, as read from 517. */
data class GimbalPosition(val yaw: Float, val pitch: Float, val roll: Float) {
    companion object {
        fun fromFrame517(f: ResponseParser.Frame): GimbalPosition? {
            val y = f.float("yaw") ?: return null
            val p = f.float("pitch") ?: return null
            val r = f.float("roll") ?: return null
            return GimbalPosition(y, p, r)
        }
    }
}

// ---------------------------------------------------------------------
// Burst-derived state.  These come from the post-connect burst fired by
// AppViewModel and the cli-probe.  They are kept as separate data classes
// so MountState stays focused on the 284 push frame.
// See docs/PLANNING-2026-08.md Step 5.
// ---------------------------------------------------------------------

/** 778 / 779 — battery status and detail. */
data class BatteryDetail(
    val percent: Int? = null,
    val charging: Boolean = false,
    val voltageMv: Int? = null,
    val temperatureC: Int? = null,
    val cycleCount: Int? = null,
    val healthPercent: Int? = null,
) {
    companion object {
        /**
         * 778 produces: `level:76;charge:0;vol:7400`
         * 779 produces: `level:76;temp:25;cycle:42;health:98`
         * Both frames are merged into one model.
         */
        fun fromFrame(f: ResponseParser.Frame): BatteryDetail? {
            val pct = f.int("level")
            val charge = (f.int("charge") ?: 0) != 0
            val mv = f.int("vol")
            val temp = f.int("temp")
            val cycle = f.int("cycle")
            val health = f.int("health")
            if (pct == null && mv == null && temp == null && cycle == null && health == null) return null
            return BatteryDetail(pct, charge, mv, temp, cycle, health)
        }
    }
}

/** 775 — SD card status. */
data class SdStatus(
    val present: Boolean = false,
    val totalMb: Int? = null,
    val freeMb: Int? = null,
) {
    companion object {
        /** 775 produces: `state:0;total:30000;free:24000` (state:0 = present). */
        fun fromFrame(f: ResponseParser.Frame): SdStatus? {
            val present = (f.int("state") ?: 1) == 0
            val total = f.int("total")
            val free = f.int("free")
            if (!present && total == null && free == null) return null
            return SdStatus(present, total, free)
        }
    }
}

/** 824 — OMS (On-Mount Scheduler) run state. */
data class OmsState(
    val running: Boolean = false,
    val activeTasks: Int? = null,
    val lastError: Int? = null,
) {
    companion object {
        /** 824 produces: `running:0;tasks:0;err:0` */
        fun fromFrame(f: ResponseParser.Frame): OmsState? {
            val run = f.int("running")
            val tasks = f.int("tasks")
            val err = f.int("err")
            if (run == null && tasks == null && err == null) return null
            return OmsState((run ?: 0) != 0, tasks, err)
        }
    }
}

/**
 * 825 — a single OMS (On-Mount Scheduler) task.
 *
 * Each task record in the firmware response looks like:
 *   `id:N;state:N;name:X;...;`
 * The list of records is concatenated; the standard field-map parser cannot
 * recover them because duplicate keys collide. See [TaskList.fromFrame] for
 * the record-splitter.
 */
data class Task(
    val id: Int,
    val state: Int,
    val name: String,
) {
    companion object {
        /** Parse one record segment (a `;`-separated `key:value;...` substring)
         *  into a [Task]. Returns null if id or state is missing/invalid or if
         *  name is missing. Extra fields are ignored. */
        fun fromSegment(seg: String): Task? {
            val id = seg.intField("id") ?: return null
            val state = seg.intField("state") ?: return null
            val name = seg.strField("name") ?: return null
            return Task(id, state, name)
        }
    }
}

/**
 * 825 — list of OMS tasks.
 *
 * Firmware format (RE): `count:N;id:N;state:N;name:X;id:N;state:N;name:X;...;`
 * The standard frame parser collapses duplicate keys so we split the raw
 * payload on `id:` to recover the individual records. The first chunk may
 * carry a leading `count:N;` prefix which is preserved as [count].
 *
 * The record-list parser makes a documented assumption: every record starts
 * with `id:` and `id:` never appears in any value. Both hold for the RE
 * captures and the simulated response. Validate against live firmware before
 * relying on this in production.
 */
data class TaskList(
    val count: Int? = null,
    val tasks: List<Task> = emptyList(),
) {
    companion object {
        /**
         * Parse the multi-record task list from a frame.
         *
         * Re-splits the raw payload (carried by the frame via the [Frame.raw]
         * extension) on `id:` to recover individual records. The first
         * record's leading prefix (e.g. `count:N;`) is scanned for [count].
         */
        fun fromFrame(f: ResponseParser.Frame): TaskList? {
            val raw = f.raw ?: return null
            val records = splitTaskRecords(raw)
            if (records.isEmpty()) return null
            val count = records.first().intField("count")
            val tasks = records.mapNotNull { Task.fromSegment(it) }
            return TaskList(count, tasks)
        }

        /**
         * Split a raw payload into one segment per record, splitting on the
         * first field of each record (`id:`). The first segment is the
         * prefix that may carry a `count:N;` field. The `id:` delimiter is
         * re-attached to all segments except the first.
         */
        internal fun splitTaskRecords(payload: String): List<String> {
            if (payload.isBlank()) return emptyList()
            // Split on the literal "id:" but only between segments (i.e. after
            // a ';'). This avoids false positives if "id:" ever appears in a
            // value (it doesn't in RE captures).
            val parts = payload.split(Regex("(?<=;)id:"))
            // Reattach "id:" to all but the first part.
            return listOf(parts.first()) + parts.drop(1).map { "id:$it" }
        }
    }
}

/**
 * 770 — a single SD-card file entry returned inside the FILE_LIST body.
 *
 * Firmware format per record (RE): `id:N;name:X;prot:N;`
 * The full list is `count:N;id:N;name:X;prot:N;id:N;name:X;prot:N;...;`.
 * Mirrors the [Task] / [TaskList] split-on-`id:`-with-prefix idiom.
 */
data class FileEntry(
    val id: Int,
    val name: String,
    val prot: Int,
) {
    companion object {
        fun fromSegment(seg: String): FileEntry? {
            val id = seg.intField("id") ?: return null
            val name = seg.strField("name") ?: return null
            val prot = seg.intField("prot") ?: 0
            return FileEntry(id, name, prot)
        }
    }
}

/**
 * 770 — paginated SD-card file list.
 *
 * The first record carries the `count:N;` prefix (or any other list-level
 * field); the rest are individual files. Empty payload returns an empty list
 * (the firmware does this when no files match the requested `type`).
 */
data class FileList(
    val count: Int? = null,
    val files: List<FileEntry> = emptyList(),
) {
    companion object {
        fun fromFrame(f: ResponseParser.Frame): FileList? {
            val raw = f.raw ?: return null
            val records = TaskList.splitTaskRecords(raw)
            if (records.isEmpty()) return FileList()
            val count = records.first().intField("count")
            val files = records.mapNotNull { FileEntry.fromSegment(it) }
            return FileList(count, files)
        }
    }
}

/** 524 — ex-axis state (e.g. pan head, follow focus). */
data class ExAxisState(val state: Int) {
    companion object {
        /** 524 produces: `state:0;angle:0.0` (the angle is dropped here). */
        fun fromFrame(f: ResponseParser.Frame): ExAxisState? {
            val s = f.int("state") ?: return null
            return ExAxisState(s)
        }
    }
}

/**
 * 258..278 — camera parameter snapshot (10 GETs merged into one model).
 *
 * Each individual code carries one parameter: iso, wb, fNum, ev, focus, imgSize,
 * imgFmt, color, shutter, captureMode. The post-connect burst fires them all
 * and merges results into one [CameraInfo]. All fields are nullable so an
 * absent (or unimplemented on older firmware) parameter is clearly distinct
 * from a legitimate zero.
 */
data class CameraInfo(
    val iso: Int? = null,
    val wb: Int? = null,
    val fNum: Int? = null,
    val ev: Int? = null,
    val focus: Int? = null,
    val imgSize: Int? = null,
    val imgFmt: Int? = null,
    val color: Int? = null,
    val shutter: Int? = null,
    val captureMode: Int? = null,
) {
    companion object {
        /**
         * Merge one parameter from a single response frame into a [CameraInfo].
         * Unknown codes produce an unchanged copy. Note: 266 (CAM_GET_STATE)
         * and 267 (CAM_CAPTURE) are NOT merged here — those produce a
         * [CommandTable.CaptureState] instead and have their own pipeline.
         */
        fun fromFrame(code: Int, f: ResponseParser.Frame, current: CameraInfo = CameraInfo()): CameraInfo {
            fun int(key: String): Int? = f.int(key)
            return when (code) {
                258 -> current.copy(iso = int("iso"))
                260 -> current.copy(wb = int("wb"))
                262 -> current.copy(fNum = int("fNum"))
                264 -> current.copy(ev = int("ev"))
                268 -> current.copy(focus = int("focus"))
                270 -> current.copy(imgSize = int("imgSize"))
                272 -> current.copy(imgFmt = int("imgFmt"))
                274 -> current.copy(color = int("color"))
                276 -> current.copy(shutter = int("shutter"))
                278 -> current.copy(captureMode = int("captureMode"))
                else -> current
            }
        }
    }
}

// ---------------------------------------------------------------------
// Live-captured state (2026-08-30, gimbal at 192.168.0.1).
// VERIFIED wire formats — see PROTOCOL.md §3.2 and live-capture notes.
// ---------------------------------------------------------------------

/**
 * 780 — device info push. Live-captured frame:
 * `780@hw:1.1.1.2;sw:6.0.0.54;exAxis:;sv:1;ov: ;#`
 *
 * `exAxis` and `ov` (override?) are sometimes empty when no accessory is fitted.
 * `sv` is the API/server version (integer). `hw` and `sw` are the canonical
 * hardware/software revision strings — they're what the app displays in
 * "About" / firmware screens.
 */
data class DeviceInfo(
    val hardware: String? = null,
    val software: String? = null,
    val exAxis: String? = null,
    val serverVersion: Int? = null,
    val override: String? = null,
) {
    companion object {
        fun fromFrame(f: ResponseParser.Frame): DeviceInfo? {
            val hw = f["hw"]
            val sw = f["sw"]
            val sv = f.int("sv")
            if (hw == null && sw == null && sv == null) return null
            return DeviceInfo(
                hardware = hw,
                software = sw,
                exAxis = f["exAxis"],
                serverVersion = sv,
                override = f["ov"],
            )
        }
    }
}

/**
 * 525 — temperature / IMU read. Live-captured frame:
 * `525@Tempa509ca361e0000275a ;#`
 *
 * The 16-hex-digit field is a hex-encoded payload (likely 8 bytes of fixed-point
 * temperature data) — decoding the magnitude/format is TODO. The trailing
 * space before the `#` is verbatim on the wire.
 */
data class Temperature(val rawHex: String) {
    companion object {
        fun fromFrame(f: ResponseParser.Frame): Temperature? {
            // Wire format is `Tempa<hex>` as a single token (no `:`), so it
            // doesn't land in the field map. Recover it from the raw payload
            // by stripping the `Tempa` prefix and trimming.
            val raw = f.raw ?: return null
            val idx = raw.indexOf("Tempa")
            if (idx < 0) return null
            val hex = raw.substring(idx + "Tempa".length).trimEnd(';').trim()
            if (hex.isEmpty()) return null
            return Temperature(hex)
        }
    }
}

/**
 * 286 — camera info. Live-captured frame (no camera attached):
 * `286@manufacturer:none;model:none;state:-5;storage:0;photoFormat:0;#`
 *
 * `state:-5` indicates "no camera attached" — a useful sentinel for the UI to
 * disable capture-only controls. When a camera IS attached, `manufacturer` /
 * `model` are non-empty strings and `state` is the camera's power/ready state.
 */
data class CameraAttachment(
    val manufacturer: String? = null,
    val model: String? = null,
    val state: Int? = null,
    val storageMb: Int? = null,
    val photoFormat: Int? = null,
) {
    /** Convenience: no camera fitted. */
    val isAttached: Boolean get() = (state ?: -1) >= 0 && manufacturer != "none"

    companion object {
        fun fromFrame(f: ResponseParser.Frame): CameraAttachment? {
            val m = f["manufacturer"]
            val model = f["model"]
            if (m == null && model == null) return null
            return CameraAttachment(
                manufacturer = m,
                model = model,
                state = f.int("state"),
                storageMb = f.int("storage"),
                photoFormat = f.int("photoFormat"),
            )
        }
    }
}

