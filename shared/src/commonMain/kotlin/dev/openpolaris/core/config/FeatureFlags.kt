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
 * The defaults below are the *contract*: a release shipped with these
 * constants will only run the safe subset. Every flag whose default was
 * `true` was either a code path verified on the live burst or a read-only
 * browse surface. Every flag whose default is `false` either writes an
 * unverified code, moves the mount, or destructively modifies state.
 *
 * Conventions:
 * - `advanced*` flags gate UI affordances that send settings the firmware
 *   accepts but that we have not verified round-trip on hardware.
 * - `risky*` flags gate actions that are irreversible (format, reboot,
 *   shutdown, firmware upload, file delete).
 * - `full*` flags bundle larger surface areas (file manager, OMS
 *   scheduler, system settings) so the user can flip them on as a group
 *   instead of per-cmd.
 *
 * Policy (issue tracker: "show every parameter, every safe switch"):
 * - **Every read** the protocol returns is rendered in the DeviceInfo pane,
 *   with no flag required. Reads are safe.
 * - **Every verified write** is reachable without a config flag flip. The
 *   `systemSettings` and `advancedAstro` toggles default to ON. Kiosk
 *   builds can opt out by setting them to false. Unverified writes
 *   (e.g. `systemSettingsUnverified` for 814/815/816) stay behind a
 *   separate flag that defaults to OFF — the read of the current value
 *   is always visible, but writing is opt-in.
 * - **Every unverified or destructive write** stays behind its flag and
 *   degrades to a read-only display of the current value when the flag is
 *   off. The user always sees the mount's state — they just can't change
 *   it without an explicit config flip.
 */
object FeatureFlags {

    // ---- always on (safe + verified) --------------------------------------

    /** Slew / track / half-speed / AHRS / pos-reset / 513-516 jog. */
    const val basicControls: Boolean = true
    /** Post-connect burst (808, 809, 802, 778, 779, 775, 824, 524, 543). */
    const val postConnectBurst: Boolean = true
    /** Fine-rate correction remains disabled until its firmware and stop contract is qualified. */
    const val fineRateControl: Boolean = false
    /** SLOW manual motion remains disabled until its exact wire and stop contract is qualified. */
    const val slowMotionControl: Boolean = false
    /** Camera qualification surface. The Camera pane requires an additional
     * per-visit acknowledgement before any evidenced SET can be sent. */
    const val experimentalCamera: Boolean = false
    /** Manual focus adjustments remain off until direction and stop behaviour
     * are qualified against a named camera and firmware build. */
    const val cameraFocusControl: Boolean = false
    /** Video record control remains off until the 263 lifecycle is qualified
     * alongside preview and still-capture ownership. */
    const val cameraVideo: Boolean = false
    /** Tonight catalog/comets (read-only, no wire side effect). */
    const val catalog: Boolean = true
    /** Star alignment record (530). */
    const val alignment: Boolean = true

    // ---- advanced astro (verified, but convenience) ------------------------

    /** Helpers pane: dither (539/540), settling time (543/544). Verified
     *  round-trip on the live burst. ON by default — every safe switch
     *  should be reachable. The "Run auto-level now" action and the
     *  unverified limit-write path stay behind [autoLevel] and [limitsWrite]. */
    const val advancedAstro: Boolean = true

    /** Device-info pane: every read-back the burst pulls in (firmware, serial,
     *  WiFi band, battery detail, SD, OMS, ex-axis, camera info, device info,
     *  temperature). Read-only, no wire side effect, ON by default. Setting
     *  this to false hides the pane — does NOT stop the post-connect burst
     *  itself, which is governed by [postConnectBurst]. */
    const val deviceInfo: Boolean = true

    /** Limits (541/542) — wire format is a best-effort guess, NOT verified
     *  on real hardware. Split out from [advancedAstro] so the verified
     *  dither/settling controls don't implicitly enable unverified writes.
     *  Default OFF. */
    const val limitsWrite: Boolean = false

    /** Auto-level en/trigger (547/548/549) and live tilt push (537/538).
     *  537/538 are read-only (TILT_GET; no TILT_SET in Polaris protocol —
     *  see CommandTable.kt). 547/548/549 are live-confirmed (Functions
     *  Report §2.3). Default ON so the "Run auto-level now" action is
     *  reachable. Toggle off if you want to hide the button without
     *  touching code. */
    const val autoLevel: Boolean = true

    /** Timelapse + dynamic lapse controls (258-262, 535, etc.). */
    const val timelapse: Boolean = false

    /** Dither pattern parameters (separate from the dither on/off flag). */
    const val ditherAdvanced: Boolean = false

    // ---- file manager -----------------------------------------------------

    /** SD file list (read-only browse). Verified. */
    const val fileManager: Boolean = true

    /** Optional read-only Astro WebUI media index, thumbnails and full JPEG fetches. */
    const val astroWebUiMedia: Boolean = true
    /** Optional read-only Astro WebUI mission-state status. */
    const val astroWebUiStatus: Boolean = true
    /** Optional read-only Astro WebUI guide telemetry. */
    const val astroWebUiGuideStatus: Boolean = true
    /** Astro WebUI guide writes; disabled until solve, ownership and recovery qualify. */
    const val astroWebUiGuiding: Boolean = false
    /** Optional read-only Astro WebUI autofocus progress. */
    const val astroWebUiAutofocusStatus: Boolean = true
    /** Optional read-only Astro WebUI focal-source state. */
    const val astroWebUiFocalStatus: Boolean = true
    /** Optional read-only Astro WebUI travel-limit state. */
    const val astroWebUiLimitsStatus: Boolean = true
    /** Optional read-only Astro WebUI Wi-Fi keepalive state. */
    const val astroWebUiKeepWifiStatus: Boolean = true
    /** Astro WebUI Wi-Fi keepalive writes; disabled until recovery qualification. */
    const val astroWebUiKeepWifiWrite: Boolean = false
    /** Astro WebUI focal override writes; disabled until solve/recovery qualification. */
    const val astroWebUiFocalWrite: Boolean = false
    /** Astro WebUI autofocus start/stop; disabled until camera/focus recovery qualifies. */
    const val astroWebUiAutofocus: Boolean = false
    /** Optional read-only Astro WebUI observing-site status. */
    const val astroWebUiSite: Boolean = true
    /** Astro WebUI observing-site save; disabled until mutation and recovery are qualified. */
    const val astroWebUiSiteWrite: Boolean = false
    /** Optional read-only Astro WebUI target/last-frame solve state. */
    const val astroWebUiSolveStatus: Boolean = true
    /** Optional read-only Astro WebUI run state; mount position is explicit-only. */
    const val astroWebUiRunState: Boolean = true
    /** Optional read-only Astro WebUI programme status. */
    const val astroWebUiProgrammeStatus: Boolean = true
    /** Astro WebUI enter/leave/track writes; disabled until route and recovery qualification. */
    const val astroWebUiSessionControls: Boolean = false
    /** Astro WebUI 264 M/B dispatch; queue-only until lifecycle/file qualification. */
    const val astroWebUiCapture: Boolean = false
    /** Astro WebUI programme pause/stop writes; disabled until route qualification. */
    const val astroWebUiProgrammeControls: Boolean = false
    /** Astro WebUI lapse/panorama/HDR/focus start writes; disabled until named-head qualification. */
    const val astroWebUiProgrammeStarts: Boolean = false

    /** Delete / rename / protect are gated separately so a user can browse
     *  files without exposing the destructive actions. */
    const val fileManagerReadOnly: Boolean = true
    const val fileManagerMutate: Boolean = false

    /** SD card format (707) — destructive, requires explicit confirmation. */
    const val fileManagerFormat: Boolean = false

    // ---- system / WiFi ----------------------------------------------------

    /** Verified system settings writes (817 buzzer en, 818 LED en). ON by
     *  default — the action is reversible and the user can always toggle
     *  the switch back. If you want to forbid setBuzzer / setLed calls
     *  (e.g. on a kiosk build), set this to false. */
    const val systemSettings: Boolean = true

    /** Unverified system settings writes (814 time, 815 timezone, 816
     *  language). Per docs/PROTOCOL-CODE-AUDIT-2026-08-31.md §4.6 the wire
     *  payload contradicts the SYS_TIME / SYS_TIMEZONE / SYS_LANGUAGE name
     *  (live captures show cellular / auto-off SW payloads). OFF by
     *  default so the user has to opt in to writing a value the mount may
     *  silently drop or mis-interpret. */
    const val systemSettingsUnverified: Boolean = false

    /** WiFi scan / list (770-771) — read-only, verified. */
    const val wifiScan: Boolean = true

    /** WiFi connect / disconnect / set-band (772-773, 802) — UNVERIFIED
     *  write paths. OFF by default. */
    const val wifiConnect: Boolean = false

    /** Reboot (830). OFF by default per the [FeatureFlags] KDoc contract. */
    const val allowReboot: Boolean = false

    /** Shutdown (831). OFF by default per the [FeatureFlags] KDoc contract. */
    const val allowShutdown: Boolean = false

    /** Firmware upload (780-782). Destructive in the sense that an
     *  interrupted upload can brick the firmware. Off by default. */
    const val firmwareUpload: Boolean = false

    /** Plate solving via nova.astrometry.net. Off by default because
     *  the call hits a public internet endpoint (privacy + bandwidth
     *  cost) and the resulting RA/Dec is only as trusted as the
     *  captured frame's headers. Enable explicitly per-session. */
    const val plateSolve: Boolean = false

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
    fun toggle(flag: String): Boolean {
        val flipped = !isEnabled(flag)
        overrides[flag] = flipped
        return flipped
    }

    /** Reset all runtime overrides back to compile defaults. */
    fun reset() = overrides.clear()

    /** Reflective lookup of the compile-time default. Keeps the public
     *  surface declarative — adding a new flag only means adding a const
     *  line above; this switch picks it up automatically. */
    private fun defaultFor(flag: String): Boolean = when (flag) {
        "basicControls" -> basicControls
        "postConnectBurst" -> postConnectBurst
        "fineRateControl" -> fineRateControl
        "slowMotionControl" -> slowMotionControl
        "experimentalCamera" -> experimentalCamera
        "cameraFocusControl" -> cameraFocusControl
        "cameraVideo" -> cameraVideo
        "catalog" -> catalog
        "alignment" -> alignment
        "advancedAstro" -> advancedAstro
        "limitsWrite" -> limitsWrite
        "autoLevel" -> autoLevel
        "timelapse" -> timelapse
        "ditherAdvanced" -> ditherAdvanced
        "fileManager" -> fileManager
        "astroWebUiMedia" -> astroWebUiMedia
        "astroWebUiStatus" -> astroWebUiStatus
        "astroWebUiGuideStatus" -> astroWebUiGuideStatus
        "astroWebUiGuiding" -> astroWebUiGuiding
        "astroWebUiAutofocusStatus" -> astroWebUiAutofocusStatus
        "astroWebUiFocalStatus" -> astroWebUiFocalStatus
        "astroWebUiLimitsStatus" -> astroWebUiLimitsStatus
        "astroWebUiKeepWifiStatus" -> astroWebUiKeepWifiStatus
        "astroWebUiKeepWifiWrite" -> astroWebUiKeepWifiWrite
        "astroWebUiFocalWrite" -> astroWebUiFocalWrite
        "astroWebUiAutofocus" -> astroWebUiAutofocus
        "astroWebUiSite" -> astroWebUiSite
        "astroWebUiSiteWrite" -> astroWebUiSiteWrite
        "astroWebUiSolveStatus" -> astroWebUiSolveStatus
        "astroWebUiRunState" -> astroWebUiRunState
        "astroWebUiProgrammeStatus" -> astroWebUiProgrammeStatus
        "astroWebUiSessionControls" -> astroWebUiSessionControls
        "astroWebUiProgrammeControls" -> astroWebUiProgrammeControls
        "astroWebUiProgrammeStarts" -> astroWebUiProgrammeStarts
        "astroWebUiCapture" -> astroWebUiCapture
        "fileManagerReadOnly" -> fileManagerReadOnly
        "fileManagerMutate" -> fileManagerMutate
        "fileManagerFormat" -> fileManagerFormat
        "systemSettings" -> systemSettings
        "systemSettingsUnverified" -> systemSettingsUnverified
        "wifiScan" -> wifiScan
        "wifiConnect" -> wifiConnect
        "allowReboot" -> allowReboot
        "allowShutdown" -> allowShutdown
        "firmwareUpload" -> firmwareUpload
        "plateSolve" -> plateSolve
        "omsRead" -> omsRead
        "omsScheduler" -> omsScheduler
        "rawFrameLog" -> rawFrameLog
        "verboseLogging" -> verboseLogging
        "demoMode" -> demoMode
        "wifiBridge" -> wifiBridge
        else -> false
    }
}
