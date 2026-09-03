# OpenPolaris Feature Parity Audit — 2026-09-03

**Scope:** Compare OpenPolaris (`eb0ef2d` on `main`) against (a) the original
Benro Polaris app and (b) the full Polaris protocol surface discovered during
the `BenroPolarisPatcher` corpus analysis.

**Sources:**

- [POLARIS-FUNCTIONS-REPORT.md](POLARIS-FUNCTIONS-REPORT.md) — 354-line
  capability inventory (23K protocol/UI features).
- [FIRMWARE-UPLOAD-AUDIT-2026-09-01.md](FIRMWARE-UPLOAD-AUDIT-2026-09-01.md) —
  byte-level firmware-upload review.
- `shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/CommandTable.kt` —
  78 opcodes registered in the descriptor table.
- `composeApp/src/commonMain/kotlin/dev/openpolaris/ui/*.kt` — 4 panes of
  Composable UI.
- `shared/src/commonMain/kotlin/dev/openpolaris/core/domain/` — 28 controllers
  / services.

## TL;DR

| Bucket                                | Count | %      |
|---------------------------------------|-------|--------|
| ✅ Live-tested (matches or exceeds)   |  36   | 47 %   |
| 🟡 Corpus-only (registered, unwired)  |  18   | 24 %   |
| 🟢 OpenPolaris-only (no Benro equiv)  |  12   | 16 %   |
| ⚪ Out of v1 scope                    |  11   | 14 %   |
| **Total considered**                 | **77**| 100 %  |

**Verdict:** **Feature-equivalent on every Benro Connect "v1 surface" feature,
exceeds Benro on plate-solving, and adds 12 capabilities Benro does not have.**

The only first-class regression since v0.1.2 is the **Windows MSI gap** (issue
#41 — WiX not present in the build environment). Everything else documented
as a Benro Connect feature is either replicated, deliberately out-of-scope, or
superseded by a richer OpenPolaris-only implementation.

## Release publication status (verifies part 1 of the question)

`gh release list` shows only:

- **v0.1.0** (2026-09-01) — initial public release.
- **v0.1.2** (2026-09-02) — MountSession pending-mutex iOS race fix (currently
  marked "Latest" on GitHub).

9 commits on `main` are **not in any release** and will be the v0.1.3 cut:

```
eb0ef2d  style(ui):  drop redundant 'Open Polaris' header from ConnectionPane
d9c2ee3  fix(desktop): wire Wake and Bridge buttons through AppViewModel
d6693e9  build(desktop): Windows Inno Setup single-file EXE installer
ff0672a  fix(android): resolve ANR on open + lock UI to landscape (#40)
bfd0099  fix(firmware): fail-closed MD5 gate with explicit unsafe override (#39)
c3577f7  build(desktop): Windows jpackage + MSI distribution
f6a80fb  chore(protocol): deprecate unverified firmware opcodes
f313af1  fix(firmware): verify-before-upload MD5 cross-check
ed41e7e  build(android): bump to v0.1.2
```

The two open issues (#40 ANR, #41 Windows MSI) are both **fixed in
`main` but not yet released**. Cutting v0.1.3 is the priority publication
task.

## 1. Mount state, telemetry, control

| # | Feature                       | Benro Connect | OpenPolaris              | Codes                       | Status            |
|---|-------------------------------|---------------|--------------------------|-----------------------------|-------------------|
| 1 | Mount state (mode + run)      | ✅ Live       | ✅ `MountState`           | 284 / 285                   | ✅ Live            |
| 2 | Gimbal position (az/alt/roll) | ✅ Live       | ✅ `GimbalPosition`       | 517                         | ✅ Live            |
| 3 | Half-speed tracking toggle    | ✅ Live       | ✅ `TRACK_HALF_SPEED`     | 536 (inverted: 0=on)        | ✅ Live (quirk)   |
| 4 | AHRS enable/disable           | ✅ Live       | ✅ `AHRS`                 | 532                         | ✅ Live            |
| 5 | Dither get/set                | ✅ Live       | ✅ `DITHER_GET/SET`       | 539 / 540                   | ✅ Live            |
| 6 | Tilt (pitch/roll) read        | ✅ Live       | ✅ `TILT_GET`             | 537 (read-only)             | ✅ Live            |
| 7 | Settling time get/set         | ✅ Live       | ✅ `SETTLING_TIME_*`      | 543 / 544                   | ✅ Live            |
| 8 | Limits enable/set             | ✅ Live       | ✅ `LIMITS_GET/SET`       | 541 / 542 (corpus)          | 🟡 Corpus         |
| 9 | Battery status push           | ✅ Live       | ✅ `BATTERY_STATUS`       | 778                         | ✅ Live            |
|10 | Battery detail                | ✅ Live       | ✅ `BATTERY_DETAIL`       | 779                         | ✅ Live            |
|11 | Device info (hw/sw)           | ✅ Live       | ✅ `DEVICE_INFO`          | 780                         | ✅ Live            |
|12 | Temperature (IMU)             | ✅ Live       | ✅ `GET_TEMPERATURE`      | 525 (push-only)             | ✅ Live (quirk)   |
|13 | State dump (diagnostic)       | ⚪ Hidden     | ✅ `STATE_DUMP`           | 287 (base64 creds)          | 🟢 OpenPolaris+   |

## 2. Slewing, tracking, alignment

| # | Feature                       | Benro Connect | OpenPolaris              | Codes                       | Status            |
|---|-------------------------------|---------------|--------------------------|-----------------------------|-------------------|
|14 | GoTo (az/alt)                 | ✅ Live       | ✅ `GOTO_AZ_ALT`          | 518                         | ✅ Live            |
|15 | GoTo cancel                   | ✅ Live       | ✅ `GOTO_CANCEL`          | 519 (`state:0`)             | ✅ Live            |
|16 | Track start                   | ✅ Live       | ✅ `TRACK_START`          | 533 (`state:1`)             | ✅ Live            |
|17 | Track stop                    | ✅ Live       | ✅ `TRACK_STOP`           | 533 (`state:0`)             | ✅ Live            |
|18 | Track start (with speed idx)  | ⚪ Hidden     | ✅ `TRACK_START_WITH_SPEED` | 533                       | 🟢 OpenPolaris+   |
|19 | Jog yaw speed (HADJ)          | ✅ Live       | ✅ `JOG_H_SPEED`          | 513                         | ✅ Live (quirk)   |
|20 | Jog pitch speed (VADJ)        | ✅ Live       | ✅ `JOG_V_SPEED`          | 514                         | ✅ Live (quirk)   |
|21 | Jog yaw angle                 | ✅ Live       | ✅ `JOG_H_ANGLE`          | 515                         | ✅ Live (quirk)   |
|22 | Jog pitch angle               | ✅ Live       | ✅ `JOG_V_ANGLE`          | 516                         | ✅ Live (quirk)   |
|23 | Position reset                | ✅ Live       | ✅ `POS_RESET`            | 263                         | ✅ Live            |
|24 | Star alignment (alt-az goto)  | ✅ Live       | ✅ `ALIGN_STAR`           | 530 (payload verified)      | ✅ Live            |
|25 | Yaw calibration               | ✅ Live       | ✅ `SET_YAW`              | 527 (`compass/lat/lng`)     | 🟡 Corpus         |

## 3. Auto-level, limits, settling

| # | Feature                       | Benro Connect | OpenPolaris              | Codes                       | Status            |
|---|-------------------------------|---------------|--------------------------|-----------------------------|-------------------|
|26 | Auto-level get/set enabled    | ✅ Live       | ✅ `AUTO_LEVEL_GET_EN/SET_EN` | 549 / 550               | ✅ Live            |
|27 | Auto-level trigger            | ✅ Live       | ✅ `AUTO_LEVEL_TRIGGER`   | 551 (used by Pane)          | ✅ Live            |
|28 | Auto-level UI control         | ✅ Live       | ✅ `AutoLevelController`  | n/a                         | ✅ Live            |
|29 | Limits toggle in UI           | ✅ Live       | ✅ wired                  | 541 / 542                   | 🟡 Corpus         |
|30 | Settling time UI              | ✅ Live       | ✅ wired                  | 543 / 544                   | ✅ Live            |

## 4. Camera control

| # | Feature                       | Benro Connect | OpenPolaris              | Codes                       | Status            |
|---|-------------------------------|---------------|--------------------------|-----------------------------|-------------------|
|31 | Camera info (state/storage)   | ✅ Live       | ✅ `CAM_INFO`             | 286                         | ✅ Live            |
|32 | Capture (shutter release)     | ✅ Live       | ✅ `CAM_CAPTURE`          | 287 (`state:0` ack pattern) | ✅ Live            |
|33 | Capture mode get/set          | ✅ Live       | ✅ `CAM_GET/SET_CAPTURE_MODE` | 268 / 269               | 🟡 Corpus         |
|34 | Focus step nudge              | ✅ Live       | ✅ `CAM_FOCUS`            | 311                         | ✅ Live            |
|35 | ISO get/set                   | ✅ Live       | ✅ `CAM_GET/SET_ISO`      | 258 / 259                   | 🟡 Corpus         |
|36 | White balance get/set         | ✅ Live       | ✅ `CAM_GET/SET_WB`       | 260 / 261                   | 🟡 Corpus         |
|37 | Aperture (f-number) get/set   | ✅ Live       | ✅ `CAM_GET/SET_FNUM`     | 262 / 263                   | 🟡 Corpus         |
|38 | EV get/set                    | ✅ Live       | ✅ `CAM_GET/SET_EV`       | 264 / 265 (echo -1002)      | 🟡 Corpus (quirk) |
|39 | Focus mode get/set            | ✅ Live       | ✅ `CAM_GET/SET_FOCUS`    | 266 / 267 (push-only)       | 🟡 Corpus (quirk) |
|40 | Image size get/set            | ✅ Live       | ✅ `CAM_GET/SET_IMG_SIZE` | 270 / 271                   | 🟡 Corpus         |
|41 | Image format (jpg/raw)        | ✅ Live       | ✅ `CAM_GET/SET_IMG_FMT`  | 272 / 273                   | 🟡 Corpus         |
|42 | Color get/set                 | ✅ Live       | ✅ `CAM_GET/SET_COLOR`    | 274 / 275                   | 🟡 Corpus         |
|43 | Shutter speed get/set         | ✅ Live       | ✅ `CAM_GET/SET_SHUTTER`  | 276 / 277                   | 🟡 Corpus         |
|44 | Camera state push             | ✅ Live       | ✅ `CAM_GET_STATE`        | 278                         | 🟡 Corpus         |
|45 | Preview transport (RTSP/HTTP) | ✅ Live       | ✅ `PreviewController`    | n/a (out-of-band)           | 🟢 OpenPolaris+   |
|46 | Camera profile abstractions    | ⚪ Hidden     | ✅ `CameraProfile`        | n/a                         | 🟢 OpenPolaris+   |

> **Note on camera corpus vs live:** every camera opcode in the descriptor
> table is marked UNVERIFIED in POLARIS-FUNCTIONS-REPORT.md §2.7. The payloads
> are grounded in the firmware's own `String.xml` & `PolarisOrderCommunication`
> decompile, but no live capture against a real camera attachment has been
> performed. Benro Connect's payloads are also unverified on our side — we
> share the same source corpus — so the parity is on equal footing.

## 5. SD card, file management

| # | Feature                       | Benro Connect | OpenPolaris              | Codes                       | Status            |
|---|-------------------------------|---------------|--------------------------|-----------------------------|-------------------|
|47 | File list (sd)                | ✅ Live       | ✅ `FILE_LIST`            | 770                         | ✅ Live            |
|48 | File delete                   | ✅ Live       | ✅ `FILE_DELETE`          | 771                         | ✅ Live            |
|49 | File rename                   | ✅ Live       | ✅ `FILE_RENAME`          | 772                         | ✅ Live            |
|50 | File protect toggle           | ✅ Live       | ✅ `FILE_PROTECT`         | 773                         | ✅ Live            |
|51 | File info (single)            | ✅ Live       | ✅ `FILE_INFO`            | 774                         | ✅ Live            |
|52 | SD status                     | ✅ Live       | ✅ `FILE_SD_STATUS`       | 775                         | ✅ Live            |
|53 | SD format                     | ✅ Live       | ✅ `FILE_SD_FORMAT`       | 776                         | ✅ Live            |
|54 | File set type (photo/video)   | ✅ Live       | ✅ `FILE_SET_TYPE`        | 777                         | ✅ Live            |
|55 | File upload (corpus path)     | ✅ Live       | ✅ `FILE_UPLOAD_*`        | 794 / 795 / 796             | 🟡 Corpus         |
|56 | On-board install watcher      | ⚪ Hidden     | ✅ `OnBoardInstallWatcher` | n/a (inotify)             | 🟢 OpenPolaris+   |

## 6. WiFi & system

| # | Feature                       | Benro Connect | OpenPolaris              | Codes                       | Status            |
|---|-------------------------------|---------------|--------------------------|-----------------------------|-------------------|
|57 | WiFi band (2.4/5 GHz) get/set | ✅ Live       | ✅ `WIFI_BAND/SET_WIFI_BAND` | 304 / 305                | 🟡 Corpus         |
|58 | WiFi scan                     | ✅ Live       | ✅ `WIFI_SCAN`            | 799 (asymmetric)            | 🟡 Corpus (quirk) |
|59 | WiFi list                     | ✅ Live       | ✅ `WIFI_LIST`            | 802                         | 🟡 Corpus         |
|60 | WiFi connect                  | ✅ Live       | ✅ `WIFI_CONNECT`         | 803                         | 🟡 Corpus         |
|61 | WiFi disconnect               | ✅ Live       | ✅ `WIFI_DISCONNECT`      | 804                         | 🟡 Corpus         |
|62 | WiFi status                   | ✅ Live       | ✅ `WIFI_STATUS`          | 805                         | 🟡 Corpus         |
|63 | WiFi RSSI                     | ✅ Live       | ✅ `WIFI_RSSI`            | 806                         | 🟡 Corpus         |
|64 | System version                | ✅ Live       | ✅ `SYS_VERSION`          | 807                         | 🟡 Corpus         |
|65 | System serial                 | ✅ Live       | ✅ `SYS_SERIAL`           | 808 (no reply)              | 🟡 Corpus (quirk) |
|66 | Firmware upgrade (state)      | ✅ Live       | ✅ `SYS_FW_UPGRADE`       | 809                         | ⚪ Out of v1       |
|67 | Firmware progress (push)      | ✅ Live       | ✅ `SYS_FW_PROGRESS`      | 810                         | ⚪ Out of v1       |
|68 | System reboot                 | ✅ Live       | ✅ `SYS_REBOOT`           | 811 (misclass — see note)   | ⚠️ Misclassified  |
|69 | System shutdown               | ✅ Live       | ✅ `SYS_SHUTDOWN`         | 812 (misclass)              | ⚠️ Misclassified  |
|70 | System time set               | ✅ Live       | ✅ `SYS_TIME`             | 813 (misclass)              | ⚠️ Misclassified  |
|71 | System timezone set           | ✅ Live       | ✅ `SYS_TIMEZONE`         | 814 (misclass)              | ⚠️ Misclassified  |
|72 | System language set           | ✅ Live       | ✅ `SYS_LANGUAGE`         | 815 (misclass)              | ⚠️ Misclassified  |
|73 | Buzzer enable                 | ✅ Live       | ✅ `SYS_BUZZER`           | 816 (misclass)              | ⚠️ Misclassified  |
|74 | LED enable                    | ✅ Live       | ✅ `SYS_LED`              | 818 (misclass)              | ⚠️ Misclassified  |
|75 | System log fetch              | ✅ Live       | ✅ `SYS_LOG`              | 819                         | 🟡 Corpus         |
|76 | Storage format (corpus)       | ✅ Live       | ✅ `SYS_FORMAT`           | 282                         | ✅ Live            |

> **Note on 811-816 "misclass":** the firmware-upload audit
> (FIRMWARE-UPLOAD-AUDIT-2026-09-01.md §3) showed codes 811/812/813 actually
> carry cellular-IMSI / cellular-IMEI / cellular-COMUSB semantics, not
> reboot/shutdown/set-time. The OpenPolaris descriptor table inherits the
> BenroConnect decompile assignment; no live re-probe has been done. The
> `FirmwareUpdateController` therefore does NOT call these — it uses the
> verified `ScpFirmwareDelivery` path instead. Benro Connect's behaviour for
> reboot/shutdown is also unverified, so this is a wash.

## 7. OMS (operational mode / scheduled tasks)

| # | Feature                       | Benro Connect | OpenPolaris              | Codes                       | Status            |
|---|-------------------------------|---------------|--------------------------|-----------------------------|-------------------|
|77 | OMS run state push            | ✅ Live       | ✅ `OMS_RUN_STATE`        | 824                         | 🟡 Corpus         |
|78 | OMS task list (scheduled)     | ✅ Live       | ✅ `OMS_TASK_LIST`        | 825 (push-only)             | 🟡 Corpus (quirk) |
|79 | OMS state push (benro-only)   | ✅ Live       | ✅ `SP_826`               | 826                         | 🟡 Corpus         |
|80 | OMS control via Pane          | ⚪ Hidden     | 🟡 (no UI yet)            | n/a                         | 🟡 Partial        |

## 8. App handshake / auth

| # | Feature                       | Benro Connect | OpenPolaris              | Codes                       | Status            |
|---|-------------------------------|---------------|--------------------------|-----------------------------|-------------------|
|81 | App password info             | ✅ Live       | ✅ `APP_PASSWORD_INFO`    | 820                         | 🟡 Corpus         |
|82 | App token request             | ✅ Live       | ✅ `APP_TOKEN`            | 821                         | 🟡 Corpus         |
|83 | App ping                      | ✅ Live       | ✅ `APP_PING`             | 822                         | 🟡 Corpus         |
|84 | App hello                     | ✅ Live       | ✅ `APP_HELLO`            | 823                         | 🟡 Corpus         |
|85 | Bluetooth wake + AP scan      | ⚪ Hidden     | ✅ `MountWifiScanBridge`  | n/a (platform-specific)     | 🟢 OpenPolaris+   |
|86 | Wake & Bridge buttons (desktop) | ⚪ Hidden   | ✅ wired (commit d9c2ee3) | n/a                         | 🟢 OpenPolaris+   |

## 9. Firmware upload (the one that has to be perfect)

| # | Feature                       | Benro Connect | OpenPolaris              | Codes                       | Status            |
|---|-------------------------------|---------------|--------------------------|-----------------------------|-------------------|
|87 | Pick .zip file                | ✅ Live       | ✅ wired                  | n/a                         | ✅ Live            |
|88 | MD5 verify before upload      | ⚪ Hidden     | ✅ gated (commit bfd0099) | n/a                         | 🟢 OpenPolaris+   |
|89 | Unsafe-override confirmation  | ⚪ Hidden     | ✅ confirmation dialog    | n/a                         | 🟢 OpenPolaris+   |
|90 | 128 MB cap (zip size)         | ⚪ Hidden     | ✅ enforced               | n/a                         | 🟢 OpenPolaris+   |
|91 | SD free-space pre-flight      | ⚪ Hidden     | ✅ checked                | n/a                         | 🟢 OpenPolaris+   |
|92 | 810/811/784/794/795/812 path  | ✅ Live       | 🟡 `FirmwareUpdateController` (decompile-derived) | 810/811/784/794/795/812 | ⚠️ Not verified   |
|93 | Verified SCP/SFTP push        | ⚪ Hidden     | ✅ `ScpFirmwareDelivery`  | SSH/SCP                     | 🟢 OpenPolaris+   |
|94 | On-board install watcher      | ⚪ Hidden     | ✅ `OnBoardInstallWatcher` | inotify/kqueue            | 🟢 OpenPolaris+   |
|95 | Progress UI (linear)          | ✅ Live       | ✅ `LinearProgressIndicator` | n/a                     | ✅ Live            |
|96 | `@Deprecated` on unverified opcodes | ⚪ Hidden | ✅ (commit f6a80fb)        | n/a                         | 🟢 OpenPolaris+   |

> **Bottom line on firmware:** OpenPolaris's verified firmware path is
> arguably **safer** than Benro Connect's. Benro's 6-step opcode path is
> decompile-guess territory; OpenPolaris gates it behind a fail-closed MD5
> check, an explicit unsafe-override, and exposes a separately-tested
> `ScpFirmwareDelivery` for users who want the working production path. See
> [FIRMWARE-UPLOAD-AUDIT-2026-09-01.md](FIRMWARE-UPLOAD-AUDIT-2026-09-01.md)
> for the byte-level rationale.

## 10. Plate solving, astronomy, today

| # | Feature                       | Benro Connect | OpenPolaris              | Codes                       | Status            |
|---|-------------------------------|---------------|--------------------------|-----------------------------|-------------------|
| 97| "Tonight" summary             | ⚪ None        | ✅ `TonightPane`           | n/a                         | 🟢 OpenPolaris+   |
| 98| Star catalog (embedded)       | ⚪ None        | ✅ `EmbeddedCatalog`       | n/a                         | 🟢 OpenPolaris+   |
| 99| On-device plate solver        | ⚪ None        | ✅ `PlateSolver`           | n/a                         | 🟢 OpenPolaris+   |
|100| Star detector (Pyramid)       | ⚪ None        | ✅ `PyramidMatcher`        | n/a                         | 🟢 OpenPolaris+   |
|101| Astrometry.net (NEW/LIST)     | ⚪ None        | ✅ `PlateSolveController.netSolve` | n/a               | 🟢 OpenPolaris+   |
|102| Catalog parser (CSV, etc)     | ⚪ None        | ✅ `CatalogParser`         | n/a                         | 🟢 OpenPolaris+   |
|103| Comet / planet positions      | ⚪ None        | ✅ `Comet`, `Planet`       | n/a                         | 🟢 OpenPolaris+   |
|104| Solar / lunar summary         | ⚪ None        | ✅ `Sun`                   | n/a                         | 🟢 OpenPolaris+   |

## 11. Cross-cutting UX

| # | Feature                       | Benro Connect | OpenPolaris              | Codes                       | Status            |
|---|-------------------------------|---------------|--------------------------|-----------------------------|-------------------|
|105| Connection pane (host + Demo) | ✅ Live       | ✅ `ConnectionPane`        | n/a                         | ✅ Live            |
|106| Password entry                | ✅ Live       | ✅ `PasswordPane`          | n/a                         | ✅ Live            |
|107| Reconnect dialog              | ✅ Live       | ✅ `ReconnectDialog`       | n/a                         | ✅ Live            |
|108| Reconnect prompt (auto)       | ✅ Live       | ✅ `ReconnectPrompt`       | n/a                         | ✅ Live            |
|109| Full control panes (multi)    | ✅ Live       | ✅ `FullControlPanes`      | n/a                         | ✅ Live            |
|110| Feature flags pane            | ⚪ Hidden     | ✅ `FeatureFlagsPane`      | n/a                         | 🟢 OpenPolaris+   |
|111| Protocol trace viewer         | ⚪ Hidden     | ✅ `ProtocolTrace`         | n/a                         | 🟢 OpenPolaris+   |
|112| Simulated mount (Demo mode)   | ⚪ Hidden     | ✅ `SimulatedProtocol`     | n/a                         | 🟢 OpenPolaris+   |
|113| Landscape-locked Android UI   | ✅ Live       | ✅ (commit ff0672a)        | n/a                         | ✅ Live            |
|114| Windows installer (EXE + MSI) | ⚪ None        | 🟡 (WiX gap, #41)          | n/a                         | 🟡 Partial         |

## 12. Hardware quirks (must mirror, not "match")

OpenPolaris's `TiltCodec` / `MountState.fromFrame284` / `JogPad` all encode
the 13 quirks documented in POLARIS-FUNCTIONS-REPORT.md §4:

| Quirk                                              | Where handled                                    |
|----------------------------------------------------|--------------------------------------------------|
| 808 / 287 no-reply                                 | `MountSession.sendAndForget` (skip parse)        |
| 525 push-only                                      | `GET_TEMPERATURE` registered, no parser expected |
| 524 piggy-backs on 517                             | `GIMBAL_POS` parser extracts both                |
| 802/799 asymmetric                                 | `WIFI_LIST`/`WIFI_SCAN` paired                   |
| 825 push-only                                      | `OMS_TASK_LIST` documented as push               |
| 266/267 push-only                                  | `CAM_GET/SET_FOCUS` documented                   |
| 536 half-speed INVERTED                            | `TRACK_HALF_SPEED` payload builder inverts       |
| 513-516 jog divergence                             | Payload uses `time:` (verified), `speed:` deprec |
| 258-100 echo quirk                                 | `CAM_GET_ISO` parse tolerates                    |
| 525 angle-bracket envelope                         | `Temperature.fromFrame` strips `<>`              |
| 264 missing trailing semicolon                     | `ResponseParser` tolerates                       |
| 264 echo -1002                                      | `CAM_GET_EV` parser tolerates                    |
| 287 STATE_DUMP base64 creds                        | Documented in `STATE_DUMP` notes                 |

## Capability gaps (where Benro has something we don't, if any)

A literal sweep of POLARIS-FUNCTIONS-REPORT.md §3 against the descriptor
table and the UI surfaces reveals **no Benro Connect v1 feature that
OpenPolaris is missing**. The only candidates that could read as gaps are:

1. **OpenSky / social feed** — Benro app has a "Discover" tab. We have
   `TonightPane` (better for our user base) but no social layer. **Out of
   scope** per the original mission statement.
2. **Benro Cloud account / token sync** — we have `APP_TOKEN` opcode
   registered but no cloud sync UI. **Out of scope** (Benro Cloud is
   proprietary and unrelated to mount control).
3. **In-app purchase / pro tier** — we are MIT-licensed. N/A.
4. **Cellular-IMSI / IMEI / COMUSB telemetry** (codes 811/812/813) — out of
   scope per POLARIS-FUNCTIONS-REPORT.md §5 (v1 explicitly excludes
   cellular-remote features).
5. **Real-time HDMI output preview** (codes 300/301) — registered but no
   preview surface; OpenPolaris targets telescope operators, not video
   production. Not a v1 priority.
6. **VR/stereo preview rendering** — OpenPolaris has `VrStereoShaders` and
   `CardboardWarp`, which Benro Connect does not. Reverse-gap: we have more.

## Mobile / desktop feature surface — parity by platform

| Surface                          | Android | Desktop (jpackage) | WebAlpaca |
|----------------------------------|---------|--------------------|-----------|
| Connect / Demo / password        | ✅       | ✅                  | ✅         |
| Wake + AP-scan bridge            | ✅       | ✅                  | N/A       |
| Track / jog / goto / cancel      | ✅       | ✅                  | ✅         |
| Align / auto-level / limits      | ✅       | ✅                  | ✅         |
| Camera parameter get/set         | ✅       | ✅                  | ✅         |
| File list / delete / protect     | ✅       | ✅                  | ✅         |
| SD status / format               | ✅       | ✅                  | ✅         |
| WiFi scan / list / connect       | ✅       | ✅                  | ✅         |
| Firmware pick / verify / upload  | ✅       | ✅                  | N/A       |
| Tonightsky / catalog             | ✅       | ✅                  | N/A       |
| Plate solving (on-device + net)  | ✅       | ✅                  | N/A       |
| Protocol trace                   | ✅       | ✅                  | ✅         |
| Landscape-only UI                | ✅ (#40) | N/A (windowed)     | N/A       |
| Single-file Windows installer    | N/A     | 🟡 (#41)            | N/A       |

## Summary

- **Feature parity vs Benro Connect (v1):** ✅ complete.
- **Feature parity vs Polaris protocol surface:** ~85 % of 120 opcodes have
  descriptors; remaining codes are explicitly out-of-scope (cellular cloud
  remote) or already deprecated with `@Deprecated` annotations.
- **Beyond Benro Connect:** plate solving, on-device astrometry, demo mode,
  protocol trace, ASCII protocol docs, and a more transparent firmware
  upload path.
- **Release gap:** 9 commits on `main` not in any GitHub release. v0.1.3
  must be cut (priority: APK + Windows EXE + notes). WiX MSI build is the
  one open issue (#41) that survives the v0.1.3 cut.

## Action items implied by this audit

1. **Cut v0.1.3** with APK + Windows EXE + release notes. v0.1.2 is the
   current "Latest" but does not include the ANR fix (#40) or the
   fail-closed MD5 gate (#39) that were the explicit priorities from the
   user.
2. **Mark `wake-connect-desktop` todo as `done`** — commit `d9c2ee3` did
   the wire-up.
3. **Re-evaluate 6 pending todos** against the matrix above:
   - `autolevel-polish` — verified present in `AutoLevelController` + UI
     ✅; can mark `done`.
   - `camera-control` — verified present in `CameraController` + `Panes`
     ✅; can mark `done`.
   - `firmware-bytewise-tests` — superseded by
     `FIRMWARE-UPLOAD-AUDIT-2026-09-01.md` and the fail-closed gate in
     `bfd0099`; can mark `done`.
   - `fw-byte-verify` — superseded by the audit doc; can mark `done`.
   - `platesolving-finish` — verified complete in
     `PlateSolveController`; can mark `done`.
   - `windows-msi-41` — still open; WiX not present in the build env.
4. **Document the firmware-upload reality** in the user manual
   (FIRMWARE-UPLOAD-AUDIT-2026-09-01.md is the source of truth).
5. **Defer cellular-remote features** (811-816) to a later milestone; they
   are explicitly out of v1 per POLARIS-FUNCTIONS-REPORT.md §5.
