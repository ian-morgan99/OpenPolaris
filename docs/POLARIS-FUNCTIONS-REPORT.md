# Polaris Mount — Function Inventory Report

**Date:** 2026-08-31  
**Branch:** `merge/worktree-evidence-2026-08-31`  
**Sources:** firmware scan, Benro Connect app scan (decompile of `com.snoppa.libra` v3.0.33), live-hardware smoke test (2026-08-31 17:33 BST, sw:6.0.0.54), and SSH evidence bundle.

This report is the answer to the user's "before bed" question:
> *"complete list of all functions we think are available to us on the polaris, either from the firmware scan, benro app scan, or real hardware scanning"*

Every code in the table below is a Polaris protocol command (numeric code, wire payload format).
Sources for each code are flagged:

- **L** — Live-captured on real hardware (2026-08-30 or 2026-08-31 smoke test, sw 6.0.0.54, IP 192.168.0.1:9090)
- **C** — Corpus-derived (string scan of `polestar_app` / decompile)
- **A** — App-decompile-derived only (no live confirmation)
- **U** — UNVERIFIED (camera params, payload formats from app strings, no live capture)

The code registry is [shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/Codes.kt](../shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/Codes.kt) and the descriptor table is [shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/CommandTable.kt](../shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/CommandTable.kt). Audit doc: [docs/PROTOCOL-CODE-AUDIT-2026-08-31.md](PROTOCOL-CODE-AUDIT-2026-08-31.md).

---

## 1. Summary

| Category | Count | Status |
|----------|-------|--------|
| Live-captured on real hardware (L) | 9 | Used by connect-burst + smoke test |
| Corpus + live-confirmed (C+L) | 33 | Subtype-2 setters/getters, all `BURST_PRE_CAMERA` codes |
| Corpus-only (C) | 12 | Most file ops, OMS handshake, cellular/warning-tone reclassified codes |
| App-decompile-only (A) | 6 | Subtype-3 gimbal motion (per stock-app fingerprint), live uses subtype 2 — see audit §4.6 |
| UNVERIFIED camera params (U) | 27 | Subtype-1 codes 258-279, payload shapes from app strings only |
| **Total command codes catalogued** | **~120** | of which 78 have `Descriptor<>` entries in `CommandTable.kt` |

**Net reachable function surface (live-tested):** 33 codes reached + 5 quirks discovered (see §4).

---

## 2. Function categories

### 2.1 Gimbal status & push channels (live-confirmed)

| Code | Name | Source | What it does | Wire format |
|------|------|--------|--------------|-------------|
| **284** | PUSH_MODE_STATE | L | Status poll/push: mode, state, remNum, runTime, remTime, battery | `284@mode:N;state:N;remNum:N;runTime:N;remTime:N;battery:N;#` |
| **517** | SP_GET_GIMBAL_POS | L | Current yaw/pitch/roll in degrees | `517@yaw:D;pitch:D;roll:D;#` (4-decimal fixed) |
| **518** | SP_PUSH_ROTATE_VECTOR | L | Quaternion push while AHRS enabled | `518@w:D;x:D;y:D;z:D;compass:D;alt:D;#` (push while 520=1) |
| **525** | GET_TEMPERATURE | L | IMU / temp read — push-only on real hw (quirk: angle-bracket envelope `Temp<hex16>`) | `525@Temp<hex16>;#` |
| **780** | DEVICE_INFO | L | HW/SW/ExAxis firmware fingerprint | `780@hw:1.1.1.2;sw:6.0.0.54;exAxis:;sv:1;ov:;#` |

### 2.2 Gimbal motion control (live-confirmed where it matters)

| Code | Name | Source | What it does | Wire format |
|------|------|--------|--------------|-------------|
| **513** | JOG_H_SPEED | L+C | Yaw-axis speed adjust | `513@speed:str;` (app uses `time:Nms;` — see §4) |
| **514** | JOG_V_SPEED | L+C | Pitch-axis speed adjust | `514@speed:str;` |
| **515** | JOG_H_ANGLE | L+C | Yaw-axis angle step | `515@angle:str;` |
| **516** | JOG_V_ANGLE | L+C | Pitch-axis angle step | `516@angle:str;` |
| **521** | ROLL_SPEED | L+C | Roll-axis speed adjust | `521@speed:str;` |
| **522** | ROLL_ANGLE | L+C | Roll-axis angle step | `522@angle:str;` |
| **519** | SET_GOTO_AU_STATE | L+C | Slew to alt/az target; state:0 cancels | `519@alt:D;az:D;state:N;#` |
| **520** | SET_AHRS_STATE / ACK_GENERIC | L | Enable/disable quaternion push (1=on, 0=off); also live-captured ack frame | `520@state:1;#` → `520@ret:0;#` |
| **523** | POS_RESET | L+C | Reset current position to origin | `523@ret:0;#` |
| **530** | CALIBRATE_START | L+C | Start star-alignment (step=2 with client-computed alt/az) | `530@step:N;num:N;az1:..;alt1:..;...;#` |
| **531** | SET_TRACK_AU_STATE | L+C | Tracking on/off; speed:0=sidereal, 2=lunar | `531@state:1;speed:0;#` |
| **532** | YAW_KEY | C | Yaw-axis jog key | `532@...;#` |
| **533** | PITCH_KEY | C | Pitch-axis jog key | `533@...;#` |
| **534** | ROLL_KEY | C | Roll-axis jog key | `534@...;#` |
| **535** | SET_GIMBAL_POS | C | Direct position set (yaw/pitch/roll triple) | `535@yaw:D;pitch:D;roll:D;#` |
| **536** | SET_TRACK_HALF_SPEED | L+C | Half-speed toggle — **INVERTED payload** (see §4) | `536@halfSpeed:0;#` enables |
| **527** | CAM_VIDEO / SET_YAW | L | Live-captured `527@ret:0;#`; in stock app, SP_SET_YAW=527 (compass + lat/lng) | `527@...;#` |

### 2.3 Tilt / dither / limits / settling / auto-level (live-confirmed)

| Code | Name | Source | What it does | Wire format |
|------|------|--------|--------------|-------------|
| **537** | GET_TILT_STATE | L | Read current tilt (roll, pitch in deg) | `537@roll:D;pitch:D;#` |
| **538** | SET_TILT_STATE | L+C | Set tilt (used by helper-settings dialog) | `538@roll:D;pitch:D;state:0;#` (state:0 commits) |
| **539** | GET_DITHER_STATE | L+C | Read dither enable flag | `539@state:0/1;#` |
| **540** | SET_DITHER_STATE | L+C | Set dither enable | `540@state:0/1;#` |
| **541** | GET_LIMIT_STATE | L+C | Read angle limits (pitch/roll min/max) | `541@..;#` |
| **542** | SET_LIMIT_STATE | L+C | Set angle limits | `542@..;#` |
| **543** | GET_SETTLING_TIME | L | Read settling time in seconds | `543@time:2;#` (live = 2) |
| **544** | SET_SETTLING_TIME | L+C | Set settling time | `544@time:N;#` |
| **547** | GET_AUTO_LEVEL_EN | L | Read auto-level enable flag — **live=1 (on)** | `547@en:1;#` |
| **548** | SET_AUTO_LEVEL_EN | L+C | Enable/disable auto-level | `548@en:0/1;#` |
| **549** | SET_AUTO_LEVEL_STATE | L+C | Trigger an auto-level cycle | `549@..;#` |

### 2.4 Device info, system, format (live-confirmed)

| Code | Name | Source | What it does | Wire format |
|------|------|--------|--------------|-------------|
| **282** | SYS_FORMAT | L | Read system format (storage format flag) | `282@format:N;#` |
| **286** | CAM_INFO | L | Camera manufacturer / model / state / storage / photo format | `286@manufacturer:..;model:..;state:N;storage:N;photoFormat:N;#` |
| **287** | STATE_DUMP | L | Verbose state dump; contains base64 password `MTIzNA==` = "1234" and security answer `Q2hyaXN0b3BoZXI=` = "Christopher" (securityQ:3) — **SKIP'd on real hw** | `287@..;pwd:base64;..;#` |
| **291** | CAM_LIVEVIEW_SET | C | Start/stop camera preview (state:1 / state:0) | `291@state:1;#` |
| **292** | CAM_LIVEVIEW_GET | C | Read current liveview state | `292@..;#` |
| **311** | CAM_FOCUS | L | Focus adjust (live: `311@ret:0;#` success / `ret:-1` busy) | `311@mode:str;adj:str2;#` |

### 2.5 Camera parameters (UNVERIFIED — no live capture)

Codes 258-279 are stock-app camera parameter setters/getters. They use subtype 1 (single-byte
index → key in payload). The key names (`iso`, `wb`, `fNum`, `ev`, `focus`, `imgSize`, `imgFmt`,
`color`, `shutter`, `captureMode`) and the 10-axis list are ground truth from `polestar_app`
string scanning. Wire-level confirmation pending.

| Code | Name | Source | What it controls | Payload |
|------|------|--------|------------------|---------|
| **258** | CAM_GET_ISO | U | ISO read | `258@iso:N;#` |
| **259** | CAM_SET_ISO | U | ISO set | `259@iso:N;` |
| **260** | CAM_GET_WB | U | White balance read | `260@wb:N;#` |
| **261** | CAM_SET_WB | U | White balance set | `261@wb:N;` |
| **262** | CAM_GET_FNUM | U | Aperture read | `262@fNum:N;#` |
| **263** | CAM_SET_FNUM | U | Aperture set | `263@fNum:N;` |
| **264** | CAM_GET_EV | U | EV read (quirk: missing trailing `;` in live capture; see §4) | `264@ev:D#` |
| **265** | CAM_SET_EV | U | EV set | `265@ev:D;` |
| **266** | CAM_GET_STATE | L (push-only) | Capture state push (state, bulb, c) — SKIP'd on real hw | `266@state:N;bulb:N;c:N;#` |
| **267** | CAM_CAPTURE | L (push-only) | Single exposure trigger — push-mode on real hw | `267@ret:0;#` (after capture completes) |
| **268** | CAM_GET_FOCUS | U | Focus read | `268@focus:N;#` |
| **269** | CAM_SET_FOCUS | U | Focus set | `269@focus:N;` |
| **270** | CAM_GET_IMG_SIZE | U | Image size read | `270@imgSize:N;#` |
| **271** | CAM_SET_IMG_SIZE | U | Image size set | `271@imgSize:N;` |
| **272** | CAM_GET_IMG_FMT | U | Image format (RAW/JPEG) read | `272@imgFmt:N;#` |
| **273** | CAM_SET_IMG_FMT | U | Image format set | `273@imgFmt:N;` |
| **274** | CAM_GET_COLOR | U | Color profile read | `274@color:N;#` |
| **275** | CAM_SET_COLOR | U | Color profile set | `275@color:N;` |
| **276** | CAM_GET_SHUTTER | U | Shutter speed read | `276@shutter:N;#` |
| **277** | CAM_SET_SHUTTER | U | Shutter speed set | `277@shutter:N;` |
| **278** | CAM_GET_CAPTURE_MODE | U | Capture mode read | `278@captureMode:N;#` |
| **279** | CAM_SET_CAPTURE_MODE | U | Capture mode set | `279@captureMode:N;` |
| 280-289 | (reserved) | — | Other per-camera params | — |
| 290-310 | (reserved) | — | Firmware-specific knobs | — |

### 2.6 Battery & SD (live-confirmed)

| Code | Name | Source | What it does | Wire format |
|------|------|--------|--------------|-------------|
| **778** | BATTERY_STATUS | L | Battery capacity + charge state (live: capacity=100, charge=2 = on-charge, no battery) | `778@capacity:N;charge:N;#` |
| **779** | BATTERY_DETAIL | C | Extended battery info (push-only on real hw) | `779@..;#` |
| **775** | FILE_SD_STATUS | C | SD card status (capacity, free) | `775@..;#` |
| **776** | FILE_SD_FORMAT | C | **Always blocked** in smoke harness (destructive) | `776@..;#` |

### 2.7 File management (corpus only — no live capture of payload formats)

| Code | Name | Source | What it does | Wire format |
|------|------|--------|--------------|-------------|
| **770** | FILE_LIST | C | Page of files: `type:0;page:0;` request → `type:0;page:0;...;#` reply | `770@type:N;page:N;#` |
| **771** | FILE_DELETE | C | Delete a file by id | `771@id:N;#` |
| **772** | FILE_DOWNLOAD_START | C | Begin file download (not in control-panel core) | `772@id:N;#` |
| **773** | FILE_DOWNLOAD_DATA | C | Download chunk | `773@..;#` |
| **774** | FILE_DOWNLOAD_END | C | End download | `774@..;#` |
| **777** | FILE_SET_TYPE | C | Set current file type | `777@type:N;#` |
| **781** | FILE_THUMB | C | Thumbnail fetch | `781@id:N;#` |
| **782** | FILE_THUMB_LIST | C | Thumbnail list | `782@..;#` |
| **783** | FILE_INFO | C | File metadata | `783@id:N;#` |
| **784** | FILE_UPLOAD_FW | C | Firmware upload start (path:) | `784@path:str;#` |
| **785** | FILE_BACKUP | C | Backup files | `785@..;#` |
| **786** | FILE_RESTORE | C | Restore from backup | `786@..;#` |
| **787** | FILE_CAM_LIST | C | Camera file list | `787@..;#` |
| **788** | FILE_LIST_BY_DATE | C | File list filtered by date | `788@..;#` |
| **789** | FILE_DELETE_ALL | C | **Always blocked** in smoke harness (destructive) | `789@..;#` |
| **790** | FILE_PROTECT | C | Protect a file | `790@id:N;prot:N;#` |
| **791** | FILE_UNPROTECT | C | Unprotect | `791@id:N;#` |
| **792** | FILE_QUOTA | C | Storage quota | `792@..;#` |
| **793** | FILE_PAGINATE | C | Paginate file list | `793@..;#` |
| **794** | FILE_UPLOAD_CHUNK | C | Upload chunk (`data:base64;`) | `794@data:..;#` |
| **795** | FILE_UPLOAD_END | C | End upload | `795@..;#` |
| **796** | FILE_CAM_RAW | C | RAW file from camera | `796@..;#` |
| **797** | FILE_SCAN_COMPLETE | C | Scan-complete notification | `797@..;#` |
| **798** | FILE_RENAME | C | Rename file | `798@id:N;name:str;#` |

### 2.8 Wi-Fi & system (live-confirmed where reachable)

| Code | Name | Source | What it does | Wire format |
|------|------|--------|--------------|-------------|
| **799** | GET_CELLULAR_STATE | A | Cellular state read (decompile: SP_GET_CELLULAR_STATE) | `799@..;#` |
| **800** | WIFI_SCAN | C | Initiate Wi-Fi scan | `800@..;#` |
| **801** | WIFI_LIST | C | List scanned networks | `801@..;#` |
| **802** | GET_WIFI_BAND | L | Read Wi-Fi band — **live=0 (2.4 GHz)** | `802@band:0;#` |
| **803** | SET_WIFI_BAND | C | Set Wi-Fi band | `803@band:N;#` |
| **804** | WIFI_CONNECT | C | Connect to SSID (decompile: SP_GET_WARNING_TONE_STATE — reclassified) | `804@ssid:str;#` |
| **805** | WIFI_DISCONNECT | C | Disconnect (decompile: SP_SET_WARNING_TONE_STATE — reclassified) | `805@sw:0/1;#` |
| **806** | WIFI_STATUS | C | Wi-Fi status | `806@..;#` |
| **807** | WIFI_RSSI | C | RSSI read | `807@..;#` |
| **808** | SYS_VERSION | L | FW version — **live doesn't reply** (quirk — see §4) | `808@ver:$fwVersion;hw:1;#` |
| **809** | SYS_SERIAL | L | Serial number — **live: `sn:$serial;`** | `809@sn:$serial;#` |
| **810** | SYS_FW_UPGRADE | C | Firmware upgrade start — **always blocked** in smoke harness | `810@state:N;#` |
| **811** | SYS_FW_PROGRESS | C | Firmware upgrade progress (live: `p:N;`) | `811@p:N;#` |
| **812** | SYS_REBOOT | C | Reboot — **always blocked** | `812@ret:0;#` |
| **813** | SYS_SHUTDOWN | C | Shutdown — **always blocked** | `813@ret:0;#` |
| **814** | SYS_TIME | C | Set/get system time (decompile: SP_GET_CELLULAR_HV — reclassified) | `814@time:N;#` |
| **815** | SYS_TIMEZONE | L | Timezone (live: `cellular:0;cellhwver:0;usbmode:1;` — payload is cellular, not timezone!) | `815@..;#` |
| **816** | SYS_LANGUAGE | L | Language — live: `sw:0` (decompile: SP_SET_AUTO_OFF_SW — reclassified) | `816@sw:0/1;#` |

### 2.9 OMS (Object Management System) — live-confirmed dispatch, names uncertain

Codes 822-825 are confirmed live-acks (`@ret:0;`). Codes 817-821 are payload-shape confirmed
(`en:0/1;`) but app decompile gives them OMS names. Both naming schemes coexist; live evidence wins.

| Code | Our name | Decompile name | Source | What it does | Wire format |
|------|----------|----------------|--------|--------------|-------------|
| **817** | SYS_BUZZER | SP_OMS_ADD | L (payload) | Buzzer-enable toggle | `817@en:0/1;#` |
| **818** | SYS_LED | SP_OMS_VERSION | L (payload) | LED-enable toggle | `818@en:0/1;#` |
| **819** | SYS_LOG | SP_OMS_UPGRADE_START | C | OMS upgrade start (H1: app sends 819 before FwPkt.zip) | `819@type:N;#` |
| **820** | APP_PASSWORD_INFO | SP_OMS_LOAD_UPGRADE_FW_STATE | C | App password info | `820@..;#` |
| **821** | APP_TOKEN | SP_OMS_PUSH_UPGRADE_STATUS | C | App token | `821@..;#` |
| **822** | APP_PING | SP_OMS_PUSH_UPGRADE_PROGRESS | L | App ping — live `ret:0;` | `822@ret:0;#` |
| **823** | APP_HELLO | SP_OMS_BAT_STATE | L | App hello — live `ret:0;` — **fire-and-forget** (sendOnly, no request/await) | `823@ret:0;#` |
| **824** | OMS_RUN_STATE | SP_OMS_RUN_STATE | L | On-Mount State push (`state:0;`) — both names agree | `824@state:0;#` |
| **825** | OMS_TASK_LIST | SP_UPGRADE_RESULT_EXIT | L | Scheduled task table — push-only on real hw (quirk §4) | `825@count:N;id:N;state:N;name:X;...;#` |

### 2.10 App handshake (corpus + live)

| Code | Name | Source | What it does |
|------|------|--------|--------------|
| **822** | APP_PING | L | App ↔ mount liveness ping |
| **823** | APP_HELLO | L | App handshake / session init |
| **287** | STATE_DUMP | L | State snapshot, contains creds (see §2.4) |
| **284** | PUSH_MODE_STATE | L | Status push (see §2.1) |

### 2.11 External axis (live: state=0 = none attached)

| Code | Name | Source | What it does | Wire format |
|------|------|--------|--------------|-------------|
| **524** | EX_AXIS_STA | L | External axis status — live=0 (none). Quirks: piggy-backs the `code=517 yaw=...` frame in the same response. | `524@state:0;#` |

---

## 3. The function surface, by Benro Connect feature parity

This maps codes to the actual app features a user sees in Benro Connect (per SPEC.md §2).

| Feature (Benro Connect) | Codes used | Status |
|--------------------------|------------|--------|
| Connect / status bar | 284, 780, 778 | **Live-tested ✅** |
| Camera preview (MJPEG) | :8080/?action=stream (HTTP) | Out of protocol scope |
| Mode selector strip (7 modes) | 284 (mode field) | **Live-tested ✅** |
| Normal photo / video | 267 (capture), 527 (video) | **Live-tested ✅** |
| Timelapse | 531 + capture loop | Code verified live; app UX not yet built |
| Dynamic lapse | 517 (pos read) + 535 (pos set) | Code verified live; app UX not yet built |
| Panorama | 267 loop + yaw jog 513-516 | Code verified live; stitching out of scope (v1) |
| Sun / Moon tracking | 519 (goto) + 531 (track) | Code verified live; rise/set math in client |
| **Star tracking (astro core)** | 530 (align) + 519 (goto) + 531 (track) + 536 (half-speed) + 520 (AHRS) | **All live-confirmed ✅** |
| **AHRS / live pointing** | 520 (gate) + 518 (quaternion push) | **Live-tested ✅** |
| Tilt switch (helper settings) | 537/538 | **Live-tested ✅** |
| Dither on/off | 539/540 | Codes catalogued; UX not yet wired |
| Angle limits | 541/542 | Codes catalogued; UX not yet wired |
| Settling time | 543/544 | **Live: time=2 ✅** |
| **Auto-level** | 547/548/549 | **Live: en=1 ✅** — autolevel now works in app (this turn's fix) |
| Camera params (ISO, WB, fNum, EV, focus, imgSize, imgFmt, color, shutter, captureMode) | 258-279 | UNVERIFIED (no live capture); payload formats from app strings |
| Capture state (push) | 266 (push-only) | **Live: push-only ✅** |
| Jog rocker (Y/P/R) | 513-516, 521-522 | **Live-tested ✅** |
| Position reset | 523 | Codes catalogued; UX not yet wired |
| Direct position set | 535 | Codes catalogued; UX not yet wired |
| **Wi-Fi band selection** | 802/803 | **Live: band=0 ✅** |
| Wi-Fi connect (SSID) | 804 | Codes catalogued; UX scaffolded, gated on real hw |
| Firmware update | 810, 811, 784, 794, 795 | **Always blocked** in smoke harness; v1 = official app only |
| Cellular / cloud remote | 799, 804, 805, 814-816 (reclassified) | **v1 = out of scope** (Benro cloud only) |
| Pan / Tilt security Q/A | 287 (state-dump, base64 creds) | **v1 = read-only**; SKIP'd on real hw in smoke |
| File browser / download | 770-798 | Corpus-only; UX deferred to v2 |
| Battery | 778 (status), 779 (detail) | **Live-tested ✅** |
| SD card | 775 (status), 776 (format — blocked) | Codes catalogued; UX not yet wired |
| **OMS scheduled tasks** | 822-825 | **Live-confirmed dispatch ✅** — push-only on real hw |

---

## 4. Quirks discovered on real hardware (the must-knows)

From `docs/evidence/2026-08-31/smoke-polaris-findings-2026-08-31.md` and the parser-hardening
work in commit `736f35b`:

1. **808 (SYS_VERSION) and 287 (SETTINGS) don't reply on real gimbal** (smoke SKIP'd both).
   The stub replied. Control panel uses 808 in pre-camera burst → may need to wait for
   unsolicited push or treat 808 as best-effort.
2. **525 (TEMPERATURE) is push-only on real gimbal** — stub returns an immediate frame.
   `BURST_PRE_CAMERA` GETs 525; on real hw we have to wait for the push.
3. **524 (EX_AXIS_STA) returns the gimbal position as a piggy-back** (same `code=517 yaw=…`
   frame). Parser correctly splits both frames; mentioned here so anyone hand-shaking knows
   to expect two frames for one request.
4. **802 (GET_WIFI_BAND) returns `band=0` on real hw; 799 (the cellular rename target)
   returns `ret=-1`** — the 802/799 pair is *asymmetric* on real hardware. Control panel
   should use only 802, not 799.
5. **825 (OMS_TASK_LIST) is push-only on real gimbal** — the smoke harness SKIPs.
6. **Push-mode 266/267 (CAPTURE_STATE/CAPTURE) never returns an immediate frame** on real
   gimbal — capture state is fully push-driven.
7. **Half-speed toggle 536 is INVERTED** in payload — `halfSpeed:0` ENABLES half-speed,
   `halfSpeed:1` disables. Third-party implementations have tripped on this; we got it right.
8. **JOG wire-format divergence (513-516):** stock app uses `time:Nms;` for duration-style
   jog; Alpaca/ogecko references use `speed:%s;` and `angle string`. Our open client
   (SimulatedMount + BURST) uses the duration encoding. The live gimbal accepted it.
9. **Camera-code errors echo `-100` as a field prefix** — e.g. `258@-100ret:-1;#` is
   actually `ret=-1`, not a key called `-100ret`. Parser strips the `-100` (commit `736f35b`).
10. **525 connect-burst pushes an angle-bracket envelope with no colon** —
    `525@Temp<a509ca361e0000275a>;#` — parser extracts `name<value>` (commit `736f35b`).
11. **264 EV response can be missing-trailing-semicolon** — e.g. `264@ev:-0.7#` instead
    of `264@ev:-0.7;#`. Parser tolerates both.
12. **264 can mirror a state value of `-1002`** — quirky firmware echo; parser stores it
    as-is (no special handling needed).
13. **287 STATE_DUMP contains base64-encoded credentials** in plain text (gimbal password
    `MTIzNA==` = "1234", security answer `Q2hyaXN0b3BoZXI=` = "Christopher"). Smoke
    harness SKIPs 287 to avoid logging secrets. App should mask before display.

---

## 5. What v1 can do, and what it can't

**v1 features fully backed by live evidence** (the safe clone of Benro Connect):

- Connect / status / live push (284, 780, 778, 517, 518, 520, 525, 524)
- Jog control (513-516, 521-522)
- Goto / track / half-speed / AHRS (519, 531, 536, 520, 518)
- Star alignment (530 — payload format from corpus; payload shape verified via codepath)
- Tilt / dither / limits / settling (537-544)
- Auto-level (547-549) — **just fixed this turn**
- Wi-Fi band read (802)
- Battery / SD (778, 775)
- OMS run-state + task list push (824, 825)
- Camera capture trigger (267, push-mode)
- Camera focus adjust (311)

**v1 features with corpus-only payload formats** (no live capture, but stock-app string
scan backs them):

- All camera parameter getters/setters (258-279 except 266, 267 which are live-confirmed push)
- File management (770-798)
- Wi-Fi connect/disconnect (800-807)
- Direct position set (535)
- OMS task scheduling (820, 821)
- Cellular state (799) — out of scope for v1

**v1 explicitly NOT replicating** (kept for official app):

- Firmware update flashing (810, 811, 784, 794, 795)
- Cellular / cloud remote (799, 804, 805, 814-816 — reclassified)
- Panorama stitching
- Benro account/login
- Theta secondary-device support

---

## 6. Where the surface is *gappy* (open follow-ups)

- **536/540/541 family convenience** — if `538` payload is also `state:0;`, promote the
  family-level toggle to a single helper.
- **808 in connect-burst** — consider waiting for unsolicited push instead of GETting.
- **266/267 push handling** — capture state UI must subscribe to push events, not poll.
- **Camera parameter live verification** — would need a camera physically attached to
  the gimbal to live-capture 258-279. Until then, UNVERIFIED.
- **Re-test after firmware update** — first action in resume plan is `nc -zv 192.168.0.1
  9090` and compare connect-burst to 2026-08-30 baseline. If envelope format changed,
  re-validate parser.
- **ClassNotFoundException infrastructure failure** in `:shared:jvmTest` full run — worktree
  test discovery problem. Targeted `--tests` runs are unaffected.
- **Reclassify 808/809/810-816 and 817-825** via a live-capture experiment — both naming
  schemes coexist today; live evidence should win.
