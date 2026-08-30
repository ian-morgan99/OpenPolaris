package dev.openpolaris.core.domain

import dev.openpolaris.core.protocol.ResponseParser

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

