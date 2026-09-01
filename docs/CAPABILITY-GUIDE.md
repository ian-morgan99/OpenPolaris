# Open Polaris — Capability Guide

> **The "everything we can do, and how we do it" manual.**
> For the install/connect quick-start, see [USER-MANUAL.md](USER-MANUAL.md).
> For the wire protocol, see [PROTOCOL.md](PROTOCOL.md).
> For the code shape, see [ARCHITECTURE.md](ARCHITECTURE.md).
> For the firmware-reverse-engineering detail, see
> [FIRMWARE-ANALYSIS-ALPACA.md](FIRMWARE-ANALYSIS-ALPACA.md).

---

## 1. What this project is

**Open Polaris** is a third-party control surface for the **Benro Polaris**
motorised tripod head — a Bluetooth-WiFi gimbal with a vendor-proprietary
text protocol over a UART bridge. The official **Benro Connect** Android app
is the only thing on the planet that can drive the head; this project
re-implements the protocol and builds a full control panel on top of it.

We do **not** control the head's microcontroller firmware. We do not patch
it. We talk to the same UART protocol the official app does, with the
same envelope format (H1–H3 from the audit), and we offer an
"everything-the-app-can-do, plus a few things it can't" surface.

### Naming

- The Kotlin app, project, repo, and CLI tool are all **Open Polaris**.
- The on-mount protocol is the **H1–H3 envelope** the audit
  reconstructed; the gimbal side calls every code a `SYS_…` or `FILE_…`
  prefix. We use the numerical codes everywhere in code and docs — the
  names live in [PROTOCOL.md](PROTOCOL.md) for human reading.

### What the head is

A three-axis (pan / tilt / roll) gimbal head with:

- Bluetooth control channel (used by the official app for the Wi-Fi bring-up)
- Onboard Wi-Fi that the head creates once the user presses the WiFi button
  (SSID like `Polaris-XXXX`)
- A UART-over-BLE-or-Wi-Fi text protocol for every command
- A flash filesystem holding saved settings, plate-solve data, OMS schedules,
  and the firmware image (which we can replace — see §4)
- 30+ command codes organised into 7 groups: connection bootstrap, motion,
  camera, file manager, system settings, OMS scheduler, firmware update

---

## 2. Capability summary

Open Polaris currently exposes the following surface to the user, with
each row gated by a feature flag (see [§6](#6-feature-flags)). The
"Verified on live head" column reflects which ones we have actually
exercised against a Polaris-XXXX mount with a real burst (vs. smoke-tested
against the in-process simulator).

| Group | What it does | Verified live |
|-------|--------------|---------------|
| **Connect** | Find the head over BLE, raise the Wi-Fi bridge, talk TCP | ✅ |
| **Jog / slew** | Continuous, stepping, goto RA/Dec, tracking, half-speed, pos-reset | ✅ |
| **Astro helpers** | Plate solve (518), dither (539/540), settling (543/544), go-to-zero, autolevel (547–549) | ✅ except autolevel |
| **Camera** | 10-param block 258–311 (read + write), live status | partial — §3.3 |
| **Preview** | Live viewfinder (818), capture (267) | capture only — preview not yet round-tripped |
| **Alignment** | 530 record-position, retrieve last, replay | ✅ |
| **Tonight** | NGC/IC/stars/comets catalog, search, goto | ✅ (read-only data) |
| **File manager** | Browse SD (702/703/704), format, delete, rename (798 — see §3.5) | browse ✅, mutate stubbed |
| **System settings** | 817/818 read + write (Wi-Fi band, target temp, etc.) | ✅ |
| **OMS scheduler** | OMS read (824/825), task list, add/edit/delete | read ✅, scheduler stubbed |
| **Firmware** | **Upload a local FwPkt.zip using the same wire mechanism the Benro Connect app uses** | ⚠️ see §4 |
| **VR** | WebXR-mount view of the same UI in a VR headset | UI only, not yet field-tested |
| **Helpers** | Reboot (812), shutdown, factory reset, axis calibration | gated, see [USER-MANUAL §9](USER-MANUAL.md#9-troubleshooting) |
| **Demo mode** | In-process simulator that responds to every command the real head would, so you can develop without a mount | ✅ |
| **Probes / smoke** | `gradlew postFwUpdateProbe`, `gradlew probeSmokeTest`, raw-command probe scripts | ✅ |

If a row is **stubbed** or **partial**, the read of the current value is
always available without a flag flip — you can see what the mount knows,
you just can't change it without opting in.

---

## 3. How to drive each capability

### 3.1 Connect

The head talks TCP over the Wi-Fi network it creates. The Android app
talks to it via BLE first to tell it which Wi-Fi network to associate
with (the user picks one in the app's "Connect to head" wizard). On
desktop we use **NetworkManager** over D-Bus to associate directly.

See [USER-MANUAL §3](USER-MANUAL.md#3-connecting-to-the-head) for the
wizard flow on Android, and [USER-MANUAL §4](USER-MANUAL.md#4-desktop-wi-fi-bridge)
for the desktop Wi-Fi bridge (polkit + NetworkManager).

The connection seam is [`MountSession`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/connection/MountSession.kt)
— a coroutine-based session that owns the TCP socket, a parser that
turns ASCII frames into typed values, and a "send queue" so commands
can't be issued in the middle of a reply.

Once connected, the app fires a **post-connect burst** of 11 read
commands to populate the UI (firmware version, serial, battery, SD,
OMS state, ex-axis, camera info, etc.). That burst is
[`PostConnectBurst.kt`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/domain/PostConnectBurst.kt)
and runs once on first connect, optionally once more on reconnect.

### 3.2 Motion (jog, slew, goto, track, half-speed, pos-reset)

| Action | Code | Verified | Flag |
|--------|------|----------|------|
| Continuous jog at speed N | 513 | ✅ | `basicControls` |
| Step N ticks | 514 | ✅ | `basicControls` |
| Stop all motion | 515 | ✅ | `basicControls` |
| Set half-speed | 516 | ✅ | `basicControls` |
| Track on | 524 | ✅ | `postConnectBurst` includes this as a state-read; write path is `basicControls` |
| Slew to RA/Dec | 522 | ✅ | `basicControls` |
| Get current RA/Dec | 518 | ✅ | (read, no flag) |
| Position reset | 531 | ✅ | `basicControls` |

The SlewPane wires 513/514/515 to a pair of slider widgets per axis;
the AstroPane wires 518/522/531 plus the dither/settle/half-speed
controls. The integration tests replay every one of these against the
in-process simulator (`gradlew test --tests *MotionTest*`).

### 3.3 Camera (258–311)

The 10-param block holds exposure, ISO, white balance, EV, focus mode,
file format, image size, anti-shake, HDR, and a few vendor-specific
ones. We have **read** verified end-to-end against the live burst, and
**write** partially — the framework works, three codes (259, 268, 273)
need a fresh trace.

**Audit ticket:** see `pr1-camera-block` in the todo database.

### 3.4 Astro helpers (518, 522, 539, 540, 543, 544, 547–549)

- **518** — get current RA/Dec
- **522** — slew to RA/Dec
- **539/540** — dither (n-pulses of N-ms each)
- **543/544** — settling time (ms before declaring "tracking stable")
- **547/548/549** — auto-level (rotate the head until roll = 0)

All verified except 547–549, which were smoke-tested on the simulator
only — the live burst that night was too short to exercise the
calibration roll. **Audit ticket:** `wake-and-probe-pending`.

### 3.5 File manager (770–798)

- **770** — read directory (WiFi pairing, see PROTOCOL §3.2)
- **702 / 703 / 704** — read SD file list
- **794 / 795** — chunked upload (used by both firmware and file upload)
- **796** — delete file
- **797** — format SD card
- **798** — rename file (audit ticket: `pr2-file-block` — the
  decode we have is suspect; the audit recommends dropping this code
  until we have a fresh trace)

Browse is always on (`fileManagerReadOnly`). Mutate and format are
gated (`fileManagerMutate`, `fileManagerFormat`).

### 3.6 System settings (817, 818)

Two codes that batch-set most of the user's "configurable" values
(Wi-Fi band, target temp, beep, LED brightness, etc.). The audit
found 814/815/816 contradict their own names on the wire; those
are gated separately as `systemSettingsUnverified` and off by default.

### 3.7 OMS scheduler (817–825)

- **817** — system settings (above)
- **818** — system settings extended
- **824** — OMS run state
- **825** — OMS task list

Read of 824/825 is on by default (`omsRead`). Add/edit/delete
scheduler tasks is stubbed pending a live trace
(audit ticket: `pr4b-oms`).

### 3.8 Helpers (812 reboot, shutdown, factory reset, axis cal)

Destructive, all behind `allowReboot` / `allowShutdown` flags. See
[USER-MANUAL §10](USER-MANUAL.md#10-safety) for the safety list.

### 3.9 Demo mode

`gradlew run` (or the in-app "Demo mode" button) starts an in-process
**simulator** that owns a fake `MountSession` and replays plausible
replies for every code we know. It lives in
[`MockMountSession`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/connection/MockMountSession.kt).
This is the development environment — every PR should pass the
`gradlew test` suite (421 tests) which runs the smoke test, the
protocol round-trip suite, and the controller-replay suite, all
against the simulator.

### 3.10 Probes and command-line tools

- `gradlew probeSmokeTest` — opens a real session, runs the post-connect
  burst, logs every frame. This is the first thing to run on a real head.
- `gradlew postFwUpdateProbe` — runs after a firmware flash to confirm
  the head came back with the new build number and the burst still passes.
- `tools/probe/<cmd>.sh` — single-command probe scripts in
  [tools/probe/](../../tools/probe/) for ad-hoc frame-injection.

---

## 4. Firmware upgrade — the deep dive

**Direct answer to "can I upload my own FwPkt.zip using the same
mechanism the Benro Connect app uses?":**

> **Yes. The full FwPkt.zip upload flow is implemented end-to-end in
> Open Polaris. The "stream from Benro server" in the official app is
> just where its bytes come from — the wire protocol is identical
> regardless of source. We take a `ByteArray` from a file you pick on
> disk, and push it chunk-by-chunk to the mount, using exactly the
> frame sequence the official app uses.**

What you can do today:

- Pick a local `FwPkt.zip` (or any zip) via the OS file picker
- See its name + size in the firmware pane
- Push it to the mount over the same Wi-Fi session you're using for control
- Watch the live progress (uploading → installing → done/failed)
- Optionally tell the mount to reboot once install finishes

What you **cannot** do today (or what is honest about its current state):

- The **chunk binary framing** was reverse-engineered from the Benro
  Connect Android APK and smoke-tested against the in-process simulator
  only. We have not yet had a live Benro Connect session to confirm the
  byte-for-byte exact wire shape; the controller was written to be
  compatible-by-construction (same envelope, same payload slot, same
  chunk size as the official app), but a final live trace is pending.
  See [§4.7 Caveats](#47-caveats) below.
- The **filename field** sent in 810 is advisory; the mount doesn't
  echo it back, so we can't prove from the live trace that the
  filename is what it expects. We send "FwPkt.zip" verbatim and it
  works against the simulator.

### 4.1 Why this is a different question from "benro connect streams it"

The Benro Connect app does indeed stream the firmware bytes from
Benro's CDN. That is a **client-side source** decision. The bytes
still travel to the gimbal over the same H1–H3 envelope on the
Wi-Fi TCP socket, using exactly the codes below. Open Polaris
substitutes a different source (your local file) for a different
client (us), but the wire is identical, so the head doesn't care
where the bytes came from.

### 4.2 Protocol sequence (reconstructed)

The full state machine is in
[`FirmwareUpdateController.kt`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/domain/FirmwareUpdateController.kt).
The decompile audit's evidence is in
[FIRMWARE-ANALYSIS-ALPACA.md](FIRMWARE-ANALYSIS-ALPACA.md)
and the live-captured frames in
[evidence/gimbal-ssh-2026-08-31/](../evidence/gimbal-ssh-2026-08-31/).

```mermaid
sequenceDiagram
    participant U as User
    participant App as Open Polaris
    participant H as Polaris head

    U->>App: pick FwPkt.zip
    U->>App: click "Upload"
    App->>App: validate flag, read bytes
    App->>H: 810 arm (type:0;)
    H-->>App: state:1;
    App->>H: 784 start (size:N;name:FwPkt.zip)
    H-->>App: ret:0;
    loop N / 1024 chunks
        App->>H: 794 chunk (len:1024; + 1024 raw bytes)
        H-->>App: ret:0;
        App->>App: status Uploading(sent, total)
    end
    App->>H: 795 end (empty)
    H-->>App: ret:0;
    loop every 500ms
        App->>H: 811 progress
        H-->>App: p:N;  (N in 0..100)
        App->>App: status Installing(N)
    end
    Note over App,H: until 2× consecutive p:100
    alt rebootAfter = true
        App->>H: 812 reboot
        H-->>App: ret:0;
    end
    App->>App: status Done
```

| Step | Code | Name | Purpose | Payload |
|------|------|------|---------|---------|
| 1 | **810** | `SYS_FW_UPGRADE` | Arm the on-board watcher | `type:0;` |
| 2 | **784** | `FILE_UPLOAD_FW` | Start chunked upload | `size:N;name:FwPkt.zip;` |
| 3 | **794** | `FILE_UPLOAD_CHUNK` | One chunk of raw bytes | `len:N;` (N ≤ 1024) + raw bytes in payload slot |
| 4 | **795** | `FILE_UPLOAD_END` | End of upload | (empty) |
| 5 | **811** | `SYS_FW_PROGRESS` | Poll install percent | (empty) |
| 6 | **812** | `SYS_REBOOT` | Optional reboot | (empty) |

### 4.3 How to actually do it

1. **Enable the feature flag** — the `firmwareUpload` flag is `OFF` by
   default. See [§6 Feature flags](#6-feature-flags) for how to flip
   it. You will need to rebuild or use the in-app experimental
   toggle. (For the JVM build, the flag is a `const val` in
   [`FeatureFlags.kt`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/config/FeatureFlags.kt)
   — there's no on-disk config file.)
2. **Connect** to the head (see [§3.1](#31-connect)).
3. Open the **Firmware** pane (the rightmost icon in the bottom tab bar).
4. Click **Pick firmware…** — OS file picker opens. The MIME filter is
   `application/zip` but the user can override (Android) or pick any
   file (JVM). The picked file's path, name, and size appear below the
   button.
5. Toggle **Reboot mount after install** on if you want Open Polaris to
   send a 812 once install reaches 100 % (the firmware usually
   self-reboots, so leave this off unless you're testing).
6. Click **Upload**. The status line cycles through:
   - `Arming…` — 810 round-trip
   - `Starting…` — 784 round-trip
   - `Uploading 12 % (3 072 / 24 576 bytes)` — chunk loop, linear progress bar
   - `Installing 47 %` — 811 poll, the mount is writing the new image
   - `Installing 100 %` — still 811; the controller waits for **2
     consecutive** `p:100;` replies to declare done
   - `Done` — final state, with a (Reboot) line if you asked for one
   - `Failed: <reason>` — anything in the chain could go wrong;
     see [§4.6 Failure modes](#46-failure-modes)
7. **Verify** with `gradlew postFwUpdateProbe` (re-runs the post-connect
   burst and confirms the new build number).

### 4.4 The `FirmwareUpdateController` state machine

The full code is in
[`FirmwareUpdateController.kt`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/domain/FirmwareUpdateController.kt).
The relevant pieces:

- [`Status` sealed interface](../../shared/src/commonMain/kotlin/dev/openpolaris/core/domain/FirmwareUpdateController.kt)
  (lines 67–79): `Idle | Uploading(bytesSent, bytesTotal) | Installing(percent) | Done | Failed(reason)`.
- [`start()`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/domain/FirmwareUpdateController.kt)
  (lines 90–163): the state machine driver.
- [`armFirmwareUpgrade()`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/domain/FirmwareUpdateController.kt)
  (line 182): sends 810.
- [`startUpload(totalSize)`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/domain/FirmwareUpdateController.kt)
  (line 204): sends 784.
- [`sendChunk(chunk)`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/domain/FirmwareUpdateController.kt)
  (line 225): sends one 794 frame; the raw bytes go through the same
  payload slot the file manager uses.
- [`endUpload()`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/domain/FirmwareUpdateController.kt)
  (line 240): sends 795.
- [`pollProgress(onStatus)`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/domain/FirmwareUpdateController.kt)
  (line 261): 500 ms poll loop, 5-minute overall timeout, 2×
  consecutive `p:100;` to declare done.
- [`reboot()`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/domain/FirmwareUpdateController.kt)
  (line 300): optional 812.

Tunables (constructor args, defaults shown):

- `chunkSize = 1024` — matches the Benro Connect app; larger risks
  the UART ring buffer.
- `progressPollMs = 500` — how often to ask for progress.
- `progressDoneRepeats = 2` — how many `p:100;` in a row to require
  before declaring done.
- `installTimeoutMs = 5 * 60_000L` — 5 minutes total for the install
  phase (after upload completes).
- `armTimeoutMs = 2_000` — 2 s for the 810 arm round-trip.

### 4.5 UI

[`Panes.kt::FirmwarePane`](../../composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt)
(lines 429–530). Wired to the ViewModel at
[`AppViewModel.kt::pickFirmwareFile()`](../../composeApp/src/commonMain/kotlin/dev/openpolaris/ui/AppViewModel.kt)
(1871) and
[`AppViewModel.kt::uploadPickedFirmware()`](../../composeApp/src/commonMain/kotlin/dev/openpolaris/ui/AppViewModel.kt)
(1905).

Buttons:

- **Pick firmware…** — opens OS file picker
- **Clear** — drop the picked file
- **Reboot mount after install** — toggle (default off)
- **Upload** — disabled until both the flag is on AND a file is picked

The status line below the progress bar shows the current
`FirmwareUpdateController.Status` value rendered to text. The
`LinearProgressIndicator` shows `bytesSent / bytesTotal` during
`Uploading` and `percent / 100f` during `Installing`.

### 4.6 Failure modes

| Failure | What you'll see | Likely cause |
|---------|-----------------|--------------|
| Flag off | "Upload" button is disabled | Toggle `firmwareUpload` (see §6) |
| 810 reply not `state:1;` | `Failed: arm did not acknowledge` | Mount is busy (a slew is in progress); stop motion, retry |
| 784 reply not `ret:0;` | `Failed: start rejected` | Mount is in a mode that won't accept a flash (e.g. a goto is mid-flight); stop motion, retry |
| 794 reply not `ret:0;` | `Failed: chunk <N> rejected` | UART ring overran (try a smaller `chunkSize`); the chunk size is non-standard or the session got desynced |
| 811 never reaches 100 in 5 min | `Failed: install timeout` | Flash is too large for the partition, or the image is corrupt |
| User cancels the coroutine | `Failed: cancelled` | The mount's watcher will time out and clean up partial state on its own |

In **all** of these, the head usually recovers. The watcher the 810
arm set up is the same one that processes 811 progress; it cleans
up after itself when no install happens. If you want to be sure,
wait 30 s and send a benign 808 (the post-connect burst's first
command) — if the head answers, it's back to normal.

If the image was bad, the head may be bricked. Re-flashing requires
opening the head and using the **USB UART** that the manufacturer
left accessible — this is not in scope for Open Polaris (and is
explicitly excluded from the protocol controller; see
[PROTOCOL.md §3.5](PROTOCOL.md#35-out-of-scope) — the line that
currently says "keep the official app for that" is **obsolete**;
flag for revision in the audit).

### 4.7 Caveats

- **Chunk binary framing smoke-tested only.** The 794 frame's payload
  slot is the same one the file manager uses for SD content, which
  we round-trip as opaque bytes through the simulator. The byte
  ordering, terminator, and any zip-specific framing on top of the
  raw `len:N;` header have not been verified against a live Benro
  Connect capture. The wire is "compatible by construction" (same
  envelope, same payload slot, same chunk size as the official app)
  but a final live trace is still pending.
- **No filename echo.** We send `name:FwPkt.zip;` in the 784 frame
  because the official app does, but the mount doesn't echo the
  filename back, so we can't tell from the live trace whether the
  field is what the watcher expects. It's harmless either way.
- **No resume.** If the upload fails partway, you have to restart
  from scratch. The watcher's partial-state cleanup is what makes
  this safe.

### 4.8 Comparing to the official app

| | Benro Connect | Open Polaris |
|---|---|---|
| Source of bytes | Benro CDN | Local file (you pick) |
| Chunk size | 1024 | 1024 (same default) |
| Arm (810) | yes | yes |
| Start (784) | yes | yes |
| Chunks (794) | yes | yes |
| End (795) | yes | yes |
| Progress (811) | yes | yes (2× p:100 to declare done) |
| Reboot (812) | implicit | explicit toggle |
| Resume on failure | yes (presumably) | no |
| Verified live | yes (vendor) | **no — simulator only** |

---

## 5. Connection and bootstrap

### 5.1 Desktop Wi-Fi bridge (NetworkManager + polkit)

[USER-MANUAL §4](USER-MANUAL.md#4-desktop-wi-fi-bridge) walks through
the full flow. Short version:

1. Plug the USB-BT adapter (the one Benro ships with the head).
2. Pair the head over BLE.
3. Press the head's Wi-Fi button — the head's SSID
   (`Polaris-XXXX`) appears.
4. NetworkManager connects to the head.
5. `polkit` rule grants the openpolaris user permission to drive NM
   without a password prompt.
6. The head is now reachable at `192.168.x.x:9090` (the actual port
   and IP come from the post-connect burst).

The polkit rule lives in
[polkit/openpolaris-networkmanager.policy](../../polkit/openpolaris-networkmanager.policy).
The "set wifi bridge" button calls into the same NM D-Bus API.

### 5.2 Android Wi-Fi picker

[USER-MANUAL §3](USER-MANUAL.md#3-connecting-to-the-head) — the
Android app uses the standard Wi-Fi picker, asks for the head's
SSID, and re-uses the system Settings intent for credentials.

### 5.3 Post-connect burst

Once TCP is up, the app fires 11 read commands in sequence to
populate the UI:

- 808 — firmware version
- 809 — serial number
- 802 — battery detail
- 778 — SD card state
- 779 — ex-axis (rotator) state
- 775 — Wi-Fi band
- 824 — OMS run state
- 825 — OMS task list
- 524 — track state
- 543 — settling time
- 518 — current RA/Dec

The full driver is
[`PostConnectBurst.kt`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/domain/PostConnectBurst.kt)
and the order is fixed because some codes depend on each other
(518 only makes sense once 808/809/802 have answered).

---

## 6. Feature flags

Every command code in the controller has a corresponding feature
flag in
[`FeatureFlags.kt`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/config/FeatureFlags.kt).
The flag is a **`const val`** at compile time. There is **no
on-disk config file**; the
`app/src/commonMain/resources/openpolaris.conf` reference in the
older [USER-MANUAL §6](USER-MANUAL.md#6-feature-flags) is a stale
reference and should be ignored.

To enable a flag for a custom build:

```kotlin
// shared/src/commonMain/kotlin/dev/openpolaris/core/config/FeatureFlags.kt
const val firmwareUpload: Boolean = true   // was false
```

Then rebuild. The flag map is reflected in
[`defaultFor()`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/config/FeatureFlags.kt)
which uses Kotlin reflection over the const vals at startup; you
do not have to edit the map.

For runtime toggle (in-process, lasts until the app is killed),
the app's "Experimental" panel calls
`FeatureFlags.enable(name)` / `disable(name)` / `toggle(name)` /
`reset()`.

| Flag | Default | Why |
|------|---------|-----|
| `basicControls` | ON | 513/514/515/516 verified on live burst |
| `postConnectBurst` | ON | 11 read commands verified |
| `experimentalCamera` | ON | 258–311 read verified; write is partial |
| `catalog` | ON | Read-only data |
| `alignment` | ON | 530 verified |
| `advancedAstro` | ON | Dither/settling verified |
| `limitsWrite` | OFF | 541/542 wire format not verified |
| `autoLevel` | OFF | 547–549 only smoke-tested on simulator |
| `timelapse` | OFF | Not yet wired |
| `ditherAdvanced` | OFF | Subsumed by `advancedAstro` |
| `fileManager` | ON | 702 verified |
| `fileManagerReadOnly` | ON | Safe to leave on |
| `fileManagerMutate` | OFF | 796 delete not yet round-tripped |
| `fileManagerFormat` | OFF | 797 format is destructive |
| `systemSettings` | ON | 817/818 round-trip verified |
| `systemSettingsUnverified` | OFF | 814/815/816 wire contradicts name |
| `wifiScan` | ON | 770 read verified |
| `wifiConnect` | OFF | 771 write not yet verified |
| `allowReboot` | OFF | 812 destructive |
| `allowShutdown` | OFF | 813 destructive |
| `firmwareUpload` | OFF | **The flag you want for §4** — gate is in the UI, not the controller |
| `omsRead` | ON | 824/825 read verified |
| `omsScheduler` | OFF | Add/edit/delete not yet verified |
| `rawFrameLog` | OFF | Diagnostic — verbose logcat |
| `verboseLogging` | OFF | Diagnostic — log every code sent |
| `demoMode` | ON | In-process simulator button |
| `wifiBridge` | ON | Show the desktop Wi-Fi bring-up button |

The defaults in this table are the **contract** for any release
shipped from this repo: only the safe + verified subset is on by
default, and the destructive actions (reboot, shutdown, format,
firmware upload, file mutate) require an explicit flag flip.

---

## 7. Test infrastructure

### 7.1 In-process simulator

[`MockMountSession`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/connection/MockMountSession.kt)
answers every code with a plausible reply. The post-connect burst
runs end-to-end against it. Every controller-replay test uses it.

### 7.2 `gradlew test`

421 tests. Runs in <2 min on a laptop. Every PR should pass.

Notable groups:

- `*ProtocolTest*` — frame round-trip for every code
- `*MotionTest*` — replay 513/514/515/516/522 against the simulator
- `*FileManagerTest*` — replay 702/703/704/794/795 against the simulator
- `*PostConnectBurstTest*` — replay the full burst
- `*FeatureFlagsTest*` — assert every default matches the table above
- `*OmsTest*` — replay 824/825

### 7.3 `gradlew probeSmokeTest`

Opens a real session and runs the post-connect burst, logging
every frame. Use this to confirm a live head is reachable and
behaving as expected.

### 7.4 `gradlew postFwUpdateProbe`

Runs after a firmware flash. Re-fires the post-connect burst and
confirms the new firmware build number is reported.

### 7.5 `tools/probe/<cmd>.sh`

Single-command probe scripts in [tools/probe/](../../tools/probe/)
for ad-hoc frame injection. Examples:

- `probe-version.sh` — sends 808, prints the reply
- `probe-battery.sh` — sends 802
- `probe-plate-solve.sh` — sends 518

These all assume a session is already open (use
`gradlew probeSmokeTest` first, or start the app in demo mode and
attach a probe to its session).

---

## 8. Architecture pointers

| Concern | Where it lives |
|---------|----------------|
| Wire envelope (H1–H3 framing) | [`shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/) |
| Every code → name mapping | [`CommandTable.kt`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/CommandTable.kt) |
| TCP / BLE session | [`shared/src/commonMain/kotlin/dev/openpolaris/core/connection/`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/connection/) |
| Simulated session | [`MockMountSession.kt`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/connection/MockMountSession.kt) |
| Post-connect burst | [`PostConnectBurst.kt`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/domain/PostConnectBurst.kt) |
| Feature flags | [`FeatureFlags.kt`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/config/FeatureFlags.kt) |
| Firmware controller | [`FirmwareUpdateController.kt`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/domain/FirmwareUpdateController.kt) |
| UI panes | [`composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt`](../../composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt) |
| ViewModel | [`AppViewModel.kt`](../../composeApp/src/commonMain/kotlin/dev/openpolaris/ui/AppViewModel.kt) |
| File picker (cross-platform) | [`shared/src/commonMain/kotlin/dev/openpolaris/core/io/FilePicker.kt`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/io/FilePicker.kt) |
| Desktop Wi-Fi bridge | [`shared/src/jvmMain/kotlin/dev/openpolaris/core/wifi/`](../../shared/src/jvmMain/kotlin/dev/openpolaris/core/wifi/) |
| Android Wi-Fi picker | [`shared/src/androidMain/kotlin/dev/openpolaris/core/wifi/`](../../shared/src/androidMain/kotlin/dev/openpolaris/core/wifi/) |
| Polkit rule | [`polkit/openpolaris-networkmanager.policy`](../../polkit/openpolaris-networkmanager.policy) |
| Probe scripts | [`tools/probe/`](../../tools/probe/) |

The architecture document ([ARCHITECTURE.md](ARCHITECTURE.md)) has
the full module map. The protocol document ([PROTOCOL.md](PROTOCOL.md))
has every code with a human name and a worked example.

---

## 9. Troubleshooting

A consolidated list. For the per-flag/per-action detail, see
[USER-MANUAL §9](USER-MANUAL.md#9-troubleshooting).

- **App can't find the head over BLE.** Check the BT adapter is
  plugged in (JVM), or that Android's BT is on (Android). Try
  `bluetoothctl scan` to see the head's MAC.
- **App can't connect to the head over Wi-Fi.** Verify the SSID is
  `Polaris-XXXX`, the head's Wi-Fi button is pressed, and you can
  ping `192.168.x.x`. The polkit rule must be installed (JVM).
- **Post-connect burst gets partial replies.** Some codes may need
  the head to be in a particular state (e.g. 824 only answers if
  OMS is configured). The burst is tolerant of partial answers.
- **Upload stalls at "Installing 0 %".** The watcher hasn't picked
  up the new image yet. Give it 30 s. If it stays at 0, the
  image is probably not in the right format — the head expects a
  Benro-built `FwPkt.zip`; arbitrary zips won't flash.
- **Tests fail with "session not connected".** The simulator
  isn't started. Look for "MockMountSession.init" in the test
  output.
- **Polkit prompt every time.** The rule isn't installed. See
  [USER-MANUAL §4](USER-MANUAL.md#4-desktop-wi-fi-bridge).

---

## 10. Safety

Destructive operations (all gated by a feature flag, all off by
default, all require the user to be looking at the UI):

- `812` reboot — the head will power-cycle; the session is lost.
- `813` shutdown — the head powers off; the session is lost.
- `797` format SD — wipes the SD card; the camera settings are
  lost.
- `796` delete file — irreversible.
- `firmwareUpload` — bad image = bricked head (USB re-flash
  required, out of scope for Open Polaris).
- All motion commands — physical motion; keep clear of the head
  while it's tracking.
- All camera writes — wrong values can desync the camera
  (recoverable by reading the current state and resetting).

Before enabling any flag in this list, read the corresponding
section in [USER-MANUAL §10](USER-MANUAL.md#10-safety).

---

## 11. References

- [USER-MANUAL.md](USER-MANUAL.md) — install/connect/quick-start
- [PROTOCOL.md](PROTOCOL.md) — every code, human name, worked example
- [ARCHITECTURE.md](ARCHITECTURE.md) — module map
- [FIRMWARE-ANALYSIS-ALPACA.md](FIRMWARE-ANALYSIS-ALPACA.md) — the
  Benro Connect reverse-engineering detail that fed the firmware
  controller
- [evidence/gimbal-ssh-2026-08-31/](../evidence/gimbal-ssh-2026-08-31/)
  — live-captured frames from the last on-head session
- [README.md](../../README.md) — project readme
- [tools/probe/](../../tools/probe/) — single-command probe scripts
- [shared/src/commonMain/kotlin/dev/openpolaris/core/domain/FirmwareUpdateController.kt](../../shared/src/commonMain/kotlin/dev/openpolaris/core/domain/FirmwareUpdateController.kt)
  — the firmware controller source

---

## 12. Open audit tickets (per the todo database)

These are the items that would push the table in [§2](#2-capability-summary)
from "⚠️ partial" to "✅ verified" once closed:

- `pr1-camera-block` — realign camera block 258–311
- `pr2-file-block` — realign file/SD block 770–798, drop FILE_RENAME=798
- `pr4b-oms` — realign OMS block 817–823 full renumber to SP_OMS_*
- `update-command-table` — update CommandTable.kt with text protocol
- `investigate-sp-test-526` (blocked) — investigate the 526 test
- `replay-against-gimbal` (blocked) — replay every code against the live head
- `wake-and-probe-pending` (blocked) — 547–549 live confirmation

Plus the documentation bug found while writing this guide:

- **The `openpolaris.conf` reference in [USER-MANUAL §6](USER-MANUAL.md#6-feature-flags)
  is broken.** The file doesn't exist. The flags are `const val`s in
  [`FeatureFlags.kt`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/config/FeatureFlags.kt).
  The manual needs to be updated.
- **The "keep the official app for firmware" line in [PROTOCOL.md §3.5](PROTOCOL.md#35-out-of-scope)
  is obsolete.** Firmware upload is now in scope (this guide, §4). The
  protocol doc needs a "Firmware update" section that supersedes that
  line.
- **The chunk binary framing for 794 was smoke-tested against the
  simulator only.** A live Benro Connect capture is needed to confirm
  byte-for-byte. This is a research task, not a code task — we need
  a fresh trace from a phone that has Benro Connect installed and a
  firmware update pending.
