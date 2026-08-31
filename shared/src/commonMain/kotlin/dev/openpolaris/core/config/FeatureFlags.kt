package dev.openpolaris.core.config

/**
 * Compile-time + runtime feature switches.
 *
 * The OpenPolaris app is organised as a "full control panel" — every descriptor
 * in [dev.openpolaris.core.protocol.CommandTable] is reachable from the UI —
 * but each one is gated by a flag here so a freshly-flashed build can ship
 * with only the safe subset enabled.
 *
 * Two layers:
 * - **Compile defaults** (the constants below) are the *baseline* for a
 *   release build. They are kept conservative: only the features already
 *   verified on the live 808/809/802/778/779/775/824/524/543 burst are on
 *   by default. Anything that can damage files, reboot the mount, or move
 *   the hardware to a state the user has to recover from is OFF by default.
 * - **Runtime override** via [enable], [disable], [toggle] for the in-app
 *   "Advanced" / "Experimental" panels. Persists for the lifetime of the
 *   process; there is no on-disk file because the experimental code paths
 *   are themselves short-lived and we don't want stale flags surviving a
 *   refactor.
 *
 * Conventions:
 * - `advanced*` flags gate UI affordances that send settings the firmware
 *   accepts but that we have not verified round-trip on hardware.
 * - `risky*` flags gate actions that are irreversible (format, reboot,
 *   shutdown, firmware upload, file delete).
 * - `full*` flags bundle larger surface areas (file manager, OMS
 *   scheduler, system settings) so the user can flip them on as a group
 *   instead of per-cmd.
 */
object FeatureFlags {

    // ---- always on (safe + verified) --------------------------------------

    /** Slew / track / half-speed / AHRS / pos-reset / 513-516 jog. */
    const val basicControls: Boolean = true
    /** Post-connect burst (808, 809, 802, 778, 779, 775, 824, 524, 543). */
    const val postConnectBurst: Boolean = true
    /** Camera 10-param GET/SET steppers (258-311). Experimental — see
     *  CameraController. Lives behind this flag so the unsafe-codes warning
     *  banner can be turned off in test rigs. */
    const val experimentalCamera: Boolean = true
    /** Tonight catalog/comets (read-only, no wire side effect). */
    const val catalog: Boolean = true
    /** Star alignment record (530). */
    const val alignment: Boolean = true

    // ---- advanced astro (verified, but convenience) ------------------------

    /** Helpers pane: dither (539/540), settling time (543/544), limits
     *  (541/542). The wire format for 541/542 is a best-effort guess. */
    const val advancedAstro: Boolean = true

    /** Auto-level en/trigger (547/548/549) and live tilt push (537/538). */
    const val autoLevel: Boolean = true

    /** Timelapse + dynamic lapse controls (258-262, 535, etc.). */
    const val timelapse: Boolean = false

    /** Dither pattern parameters (separate from the dither on/off flag). */
    const val ditherAdvanced: Boolean = false

    // ---- file manager -----------------------------------------------------

    /** SD file list / delete / protect (702-705). Read-only flag below. */
    const val fileManager: Boolean = true

    /** Delete / rename / protect are gated separately so a user can browse
     *  files without exposing the destructive actions. */
    const val fileManagerReadOnly: Boolean = true
    const val fileManagerMutate: Boolean = false

    /** SD card format (707) — destructive, requires explicit confirmation. */
    const val fileManagerFormat: Boolean = false

    // ---- system / WiFi ----------------------------------------------------

    /** System settings (810-829): time, timezone, language, buzzer, LED. */
    const val systemSettings: Boolean = true

    /** WiFi scan / list / connect (770-773) — needs hardware validation
     *  before the connect path is opened up. */
    const val wifiScan: Boolean = true
    const val wifiConnect: Boolean = false

    /** Reboot (830) / shutdown (831). */
    const val allowReboot: Boolean = true
    const val allowShutdown: Boolean = true

    /** Firmware upload (780-782). Destructive in the sense that an
     *  interrupted upload can brick the firmware. Off by default. */
    const val firmwareUpload: Boolean = false

    // ---- OMS --------------------------------------------------------------

    /** OMS run state (824) and task list (825). Read-only. */
    const val omsRead: Boolean = true

    /** OMS scheduler: add / edit / delete tasks. */
    const val omsScheduler: Boolean = false

    // ---- diagnostic -------------------------------------------------------

    /** Frame raw dump to logcat / console — for live debugging. */
    const val rawFrameLog: Boolean = false

    /** Verbose status messages (every code sent, every reply). */
    const val verboseLogging: Boolean = false

    /** Toggle the dev-only "Demo mode" button (in-process simulator). */
    const val demoMode: Boolean = true

    /** Show the Wi-Fi bridge bring-up button (BT wake → NM up). */
    const val wifiBridge: Boolean = true

    // -----------------------------------------------------------------------

    /** Map for fast `id → boolean` lookup. Mutable on purpose; see
     *  [enable] / [disable] / [toggle]. */
    private val overrides: MutableMap<String, Boolean> = mutableMapOf()

    /** Effective value for a flag (overrides win over the compile default). */
    fun isEnabled(flag: String): Boolean = overrides[flag] ?: defaultFor(flag)

    fun enable(flag: String) { overrides[flag] = true }
    fun disable(flag: String) { overrides[flag] = false }
    fun toggle(flag: String): Boolean = !isEnabled(flag).also { overrides[flag] = it }

    /** Reset all runtime overrides back to compile defaults. */
    fun reset() = overrides.clear()

    /** Reflective lookup of the compile-time default. Keeps the public
     *  surface declarative — adding a new flag only means adding a const
     *  line above; this switch picks it up automatically. */
    private fun defaultFor(flag: String): Boolean = when (flag) {
        "basicControls" -> basicControls
        "postConnectBurst" -> postConnectBurst
        "experimentalCamera" -> experimentalCamera
        "catalog" -> catalog
        "alignment" -> alignment
        "advancedAstro" -> advancedAstro
        "autoLevel" -> autoLevel
        "timelapse" -> timelapse
        "ditherAdvanced" -> ditherAdvanced
        "fileManager" -> fileManager
        "fileManagerReadOnly" -> fileManagerReadOnly
        "fileManagerMutate" -> fileManagerMutate
        "fileManagerFormat" -> fileManagerFormat
        "systemSettings" -> systemSettings
        "wifiScan" -> wifiScan
        "wifiConnect" -> wifiConnect
        "allowReboot" -> allowReboot
        "allowShutdown" -> allowShutdown
        "firmwareUpload" -> firmwareUpload
        "omsRead" -> omsRead
        "omsScheduler" -> omsScheduler
        "rawFrameLog" -> rawFrameLog
        "verboseLogging" -> verboseLogging
        "demoMode" -> demoMode
        "wifiBridge" -> wifiBridge
        else -> false
    }
}
