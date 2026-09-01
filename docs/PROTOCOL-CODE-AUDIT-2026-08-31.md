# Protocol Code Audit — `Codes.kt` vs `polaris-re-results.md` §4

**Date:** 2026-08-31
**Author:** OpenPolaris automated audit
**Source of truth:** `/home/ian/.copilot/session-state/57abdabb-…/files/polaris-re-results.md`
**Status:** Major systematic mismatch found. Live-captured codes confirmed correct; inferred catalog needs rewrite.

## TL;DR

The numeric codes in `shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/Codes.kt`
**do not match the decompile-derived code list in `polaris-re-results.md` §4.5–4.7** for the
file/SD, WiFi/system, and OMS blocks. The gimbal-control block (§4.4) and device-info
block (§4.5 row 780) **are** correct — they were live-captured on the real gimbal.

The camera block (§4.1) is partially correct: payload keys (`iso:`, `wb:`, `ev:`, etc.)
match the decompile, but **the numeric codes are shifted** — our `CAM_SET_ISO = 259`
corresponds to the decompile's `SP_SET_WB`, our `CAM_GET_WB = 260` to `SP_SET_EV`, etc.
This is a real "silent data corruption" risk if a future camera UI sends a "set ISO" frame
expecting it to do ISO, when it would actually do white balance.

**Impact today:** Zero. The simulator and CLI-probe only exercise the **live-captured**
codes (808, 809, 802, 778, 779, 775, 824, 524, 543, 780, 525), all of which are correct.
The mis-numbered codes are **only** referenced by `CommandTable.kt` descriptors that have
no simulator dispatch, no live test, and no UI consumer yet.

**Impact tomorrow:** High. The first time we wire up a camera control, file browser, or
OMS panel, we will hit a "this command does something else" surprise. The fix is to
re-align `Codes.kt` and `CommandTable.kt` with the decompile and update all callers.

## Live-captured codes (verified 2026-08-30 on real gimbal at 192.168.0.1)

These match between our `Codes.kt` and the decompile `SP_*` table:

| Code | Our constant | Decompile constant | Notes |
|---|---|---|---|
| 524 | `EX_AXIS_STA` | `SP_GIMBAL_EX_AXIS_STA` | live-captured response: `exAxis:0;ret:0;` |
| 525 | `GET_TEMPERATURE` | (not in decompile) | live-captured: `Tempa<hex16>` — IMU/temp, app-private code |
| 543 | `GET_SETTLING_TIME` | `SP_GET_SETTLING_TIME` | live-captured: `time:N;ret:0;` |
| 775 | `FILE_SD_STATUS` | `SP_GET_SD_INFO` | live-captured response: `totalSize:..;usedSize:..;freeSize:..` |
| 778 | `BATTERY_STATUS` | `SP_GET_BAT_STATE` | live-captured response: `level:N;charging:N;...` |
| 779 | `BATTERY_DETAIL` | `SP_GET_BAT_STATE` (dup) | live-captured, second variant |
| 780 | `DEVICE_INFO` | `SP_GET_DEVICE_VERSION` | live-captured: `hw:1.1.1.2;sw:6.0.0.54;...` |
| 802 | `GET_WIFI_BAND` | `SP_GET_WIFI_BAND` | live-captured: `band:0/1;` |
| 824 | `OMS_RUN_STATE` | `SP_OMS_RUN_STATE` | live-captured OMS state push |

Gimbal-control block (513–549) is entirely verified — every code matches the decompile
`SP_GIMBAL_*` / `SP_SET_*` table.

## Camera block (§4.1) — **mismatched**

| Code | Our constant | Decompile constant | Our payload key | Decompile payload |
|---|---|---|---|---|
| 258 | `CAM_GET_ISO` | `SP_SET_ISO` (both set/query) | `iso:` | `iso:<val>;` |
| 259 | `CAM_SET_ISO` | `SP_SET_WB` (both) | `iso:` | `wb:<k>;` ⚠ |
| 260 | `CAM_GET_WB` | `SP_SET_EV` (both) | `wb:` | `ev:<val>;` ⚠ |
| 261 | `CAM_SET_WB` | `SP_SET_SHUTTER` (both) | `wb:` | `shutter:<val>;` ⚠ |
| 262 | `CAM_GET_FNUM` | `SP_SET_FOCUS` (both) | `fNum:` | `focus:<val>;` ⚠ |
| 263 | `CAM_SET_FNUM` | (gap) | `fNum:` | — |
| 264 | `CAM_GET_EV` | (gap) | `ev:` | — |
| 265 | `CAM_SET_EV` | `SP_GET_ISO_INFO` (resp) | `ev:` | `iso:val1,val2,...;cur:val;` ⚠ |
| 266 | `CAM_GET_STATE` | `SP_GET_WB_INFO` (resp) | (none) | `wb:..;cur:..;` ⚠ |
| 267 | `CAM_CAPTURE` | `SP_GET_EV_INFO` (resp) | (none) | `ev:..;cur:..;` ⚠ |
| 268 | `CAM_GET_FOCUS` | `SP_GET_SHUTTER_INFO` (resp) | `focus:` | `shutter:..;cur:..;` ⚠ |
| 269 | `CAM_SET_FOCUS` | (gap) | `focus:` | — |
| 270–274 | various IMGSIZE/IMG_FMT/COLOR | (gap — reserved) | `imgSize:` etc. | — |
| 275 | `CAM_SET_COLOR` | `SP_GET_FNUM_INFO` (resp) | `color:` | `fnum:..;cur:..;` ⚠ |
| 276 | `CAM_GET_SHUTTER` | `SP_SET_FNUM` (both) | `shutter:` | `fnum:<val>;` ⚠ |
| 277 | `CAM_SET_SHUTTER` | (gap) | `shutter:` | — |
| 278 | `CAM_GET_CAPTURE_MODE` | (gap) | `captureMode:` | — |
| 279 | `CAM_SET_CAPTURE_MODE` | (gap) | `captureMode:` | — |
| 311 | `CAM_FOCUS` | `SP_SET_FOCUS_ADJ` (set) | `dir:+1/-1;step:N;` | `dir:+1/-1;step:<n>;` ✓ |

**Pattern:** the decompile treats codes 258–262 as **set/query combined** (single code
serves both directions: send `iso:0;` to set, get `iso:N;cur:N;` back). Our catalog
treats them as separate GET/SET pairs and shifted everything by 1. Codes 265–268 are
**response-only** "give me the list of available values" probes. Our catalog has them
mis-labelled as setters.

**Note:** the payload KEYS are mostly right (iso, wb, ev, shutter, focus, fNum all match
the decompile), so a code-with-payload table that decoupled the code number from the
key name would be safer than the current `Codes.<GETTER_OR_SETTER>` constants.

## Photo / video capture block (§4.2) — **also mismatched**

| Code | Our constant | Decompile constant |
|---|---|---|
| 267 | `CAM_CAPTURE` | `SP_GET_EV_INFO` (resp) ⚠ |
| 270 | `CAM_GET_IMG_SIZE` | `SP_FOCUS_STACK` (10-step) ⚠ |
| 271 | `CAM_SET_IMG_SIZE` | `SP_PANORAMIC` (12-step) ⚠ |
| 272 | `CAM_GET_IMG_FMT` | `SP_DELAY_SHOT` (11-step) ⚠ |
| 273 | `CAM_SET_IMG_FMT` | (gap) |
| 277 | `CAM_SET_SHUTTER` | `SP_SUN_SHOT` (5-step) ⚠ |
| 280 | (none) | `SP_HDR` (4-step) |
| 283 | (none) | `SP_PLC` (6-step) |
| 284 | `PUSH_MODE_STATE` | `SP_PUSH_MODE_STATE` ✓ |
| 285 | (none) | `SP_SET_MODE_STATE` (`mode:N;`) |
| 289 | (none) | `SP_REMOVE_PEOPLE_SHOT` (5-step) |
| 291 | `CAM_LIVEVIEW_SET` | `SP_SET_CAMERA_PREVIEW` ✓ |
| 292 | `CAM_LIVEVIEW_GET` | `SP_GET_CAMERA_PREVIEW` ✓ |
| 296–306 | (none) | SP_GET/SET_CONTROL_MODE, SP_GET/SET_EX_TIME, SP_HDMI_*, SP_HOLY_GRAIL (13-step), SP_GET_TIME_INTERVAL_TYPE |

The multi-step composites (FOCUS_STACK, PANORAMIC, DELAY_SHOT, SUN_SHOT, HDR, PLC,
REMOVE_PEOPLE_SHOT, HOLY_GRAIL) are a major **feature-class** we don't model at all.
Each is a "wizard" the app drives step-by-step (`step:1;`, `step:2;`, …) until the
gimbal acks completion. This is the entire timelapse/panorama/HDR feature surface.

## File / SD block (§4.5) — **mostly wrong**

| Code | Our constant | Decompile constant | Live? |
|---|---|---|---|
| 770 | `FILE_LIST` | `SP_GET_FILE_COUNT` (`type:FILE_TYPE_*;`) | — |
| 771 | `FILE_DELETE` | `SP_GET_FILE_LIST` (`type:..;page:..;size:..;`) | — |
| 772 | `FILE_DOWNLOAD_START` | `SP_DEL_FILE` (`path:..;`) | — |
| 773 | `FILE_DOWNLOAD_DATA` | `SP_ADD_FILE` (`path:..;`) | — |
| 774 | `FILE_DOWNLOAD_END` | `SP_SD_FORMAT` (no payload) | — |
| 775 | `FILE_SD_STATUS` | `SP_GET_SD_INFO` | ✓ matches semantically |
| 776 | `FILE_SD_FORMAT` | `SP_PUSH_SD_INFO` (push) | — |
| 777 | `FILE_SET_TYPE` | `SP_PUSH_SD_HINT_ID` (push) | — |
| 778/779 | `BATTERY_STATUS` / `BATTERY_DETAIL` | `SP_GET_BAT_STATE` (×2 variants) | ✓ |
| 780 | `DEVICE_INFO` | `SP_GET_DEVICE_VERSION` | ✓ |
| 781 | `FILE_THUMB` | (gap) | — |
| 782 | `FILE_THUMB_LIST` | (gap) | — |
| 783 | `FILE_INFO` | `SP_SET_UPGRADE_START` (no payload) ⚠ | — |
| 784 | `FILE_UPLOAD_FW` | `SP_LOAD_UPGRADE_FW_STATE` (resp) ⚠ | — |
| 785 | `FILE_BACKUP` | `SP_PUSH_UPGRADE_STATUS` (push) ⚠ | — |
| 786 | `FILE_RESTORE` | `SP_GET_CLASS_FILE_COUNT` (no payload) ⚠ | — |
| 787 | `FILE_CAM_LIST` | `SP_DEL_CLASS` (no payload) ⚠ | — |
| 788 | `FILE_LIST_BY_DATE` | `SP_APP_ADD_FILE` (no payload) ⚠ | — |
| 789 | `FILE_DELETE_ALL` | (gap) | — |
| 790 | `FILE_PROTECT` | `SP_APP_PASSWORD_INFO` (3-step encrypted pwd) ⚠ | — |
| 791 | `FILE_UNPROTECT` | `SP_EXDEV_UPGRADE_START` (no payload) ⚠ | — |
| 792 | `FILE_QUOTA` | `SP_LOAD_EXDEV_FW_STATE` (resp) ⚠ | — |
| 793 | `FILE_PAGINATE` | `SP_PUSH_EXDEV_STATUS` (push) ⚠ | — |
| 794 | `FILE_UPLOAD_CHUNK` | (gap) | — |
| 795 | `FILE_UPLOAD_END` | (gap) | — |
| 796 | `FILE_CAM_RAW` | `SP_GET_ISP_CFG_FILE` (no payload) ⚠ | — |
| 797 | `FILE_SCAN_COMPLETE` | `SP_ERROR_CODE` (resp) ⚠ | — |
| 798 | `FILE_RENAME` | `SP_GET_LOG_LIST` (no payload) ⚠ | — |

We have **no actual file-list/delete/download** commands — the decompile's 770–774
(map to our 770–774 with wrong labels) are the real file APIs. The file-upload
commands 794/795 don't appear in the decompile. We named them based on pattern, not
on decompile.

Our `FILE_RENAME = 798` is also a guess — decompile says 798 is `SP_GET_LOG_LIST`,
not rename. The 780 collision fix (commit 466ae56) renumbered our placeholder; it
should be **deleted entirely** or replaced with a `GET_LOG_LIST` descriptor.

## WiFi / system block (§4.6) — **partially wrong**

The "Live wire" column records the *response shape actually captured* (e.g. `ver:..;hw:1;`,
`ret:0;`, `sw:0/1;`); the "Semantic?" column records whether the *code name* (what the constant
says the code does) is independently verified on hardware. A `✓` on the wire column only
means we observed a payload; it does **not** mean the constant name is correct. The destructive
codes (812 reboot, 813 shutdown) are listed as UNVERIFIED in the "Semantic?" column because
the read-only hardware smoke never exercised them; treat the names as *carries destructive
behaviour, but not yet proven on this firmware*.

| Code | Our constant | Decompile constant | Live wire | Semantic? |
|---|---|---|---|---|
| 799 | `WIFI_BAND` | `SP_GET_CELLULAR_STATE` ⚠ | — | UNVERIFIED |
| 800 | `WIFI_SCAN` | (gap) | — | UNVERIFIED |
| 801 | `WIFI_LIST` | (gap) | — | UNVERIFIED |
| 802 | `GET_WIFI_BAND` | `SP_GET_WIFI_BAND` | `band:0;` | ✓ |
| 803 | `SET_WIFI_BAND` | `SP_SET_WIFI_BAND` | `ret:0;` | ✓ |
| 804 | `WIFI_CONNECT` | `SP_GET_WARNING_TONE_STATE` ⚠ | — | UNVERIFIED |
| 805 | `WIFI_DISCONNECT` | `SP_SET_WARNING_TONE_STATE` (`sw:0/1;`) ⚠ | — | UNVERIFIED |
| 806 | `WIFI_STATUS` | (gap) | — | UNVERIFIED |
| 807 | `WIFI_RSSI` | (gap) | — | UNVERIFIED |
| 808 | `SYS_VERSION` | `SP_SOCKET_CLIENT_TYPE` (resp) ⚠ | `ver:$fwVersion;hw:1;` | UNVERIFIED (wire ✓, name ⚠) |
| 809 | `SYS_SERIAL` | `SP_SET_CELLULAR_APN` (full APN config) ⚠ | `sn:$serial;` | UNVERIFIED (wire ✓, name ⚠) |
| 810 | `SYS_FW_UPGRADE` | (gap) | — | UNVERIFIED (H3 hypothesis) |
| 811 | `SYS_FW_PROGRESS` | `SP_GET_CELLULAR_IMSI` ⚠ | `p:N;` | UNVERIFIED (wire ✓, name ⚠) |
| 812 | `SYS_REBOOT` | `SP_GET_CELLULAR_IMEI` ⚠ | `ret:0;` | UNVERIFIED (destructive — never exercised) |
| 813 | `SYS_SHUTDOWN` | `SP_SET_CELLULAR_COMUSB` (`usbmode:..;`) ⚠ | `usbmode:..;` | UNVERIFIED (destructive — never exercised) |
| 814 | `SYS_TIME` | `SP_GET_CELLULAR_HV` ⚠ | `cellular=0;cellhwver=0;usbmode=1` (cellular payload — name ✗) | UNRESOLVED (wire contradicts name) |
| 815 | `SYS_TIMEZONE` | `SP_GET_AUTO_OFF_SW` ⚠ | `sw=0` (auto-off payload — name ✗) | UNRESOLVED (wire contradicts name) |
| 816 | `SYS_LANGUAGE` | `SP_SET_AUTO_OFF_SW` (`sw:0/1;`) ⚠ | — | UNVERIFIED |

**Big finding:** the decompile says 799+ is **cellular-state block**, not WiFi.
The Polaris has a cellular modem. We missed this entirely — there's a whole
"configure APN, read IMSI, read IMEI, switch USB mode" surface we don't model.

Our 808/809/811/812/813/814/815/816 are all mis-labelled. Only 802/803 (WiFi band
get/set) are correct.

## OMS block (§4.7) — **shifted by 7**

| Code | Our constant | Decompile constant | Live? |
|---|---|---|---|
| 817 | `SYS_BUZZER` | `SP_OMS_ADD` ⚠ | — |
| 818 | `SYS_LED` | `SP_OMS_VERSION` ⚠ | — |
| 819 | `SYS_LOG` | `SP_OMS_UPGRADE_START` ⚠ | — |
| 820 | `APP_PASSWORD_INFO` | `SP_OMS_LOAD_UPGRADE_FW_STATE` ⚠ | — |
| 821 | `APP_TOKEN` | `SP_OMS_PUSH_UPGRADE_STATUS` ⚠ | — |
| 822 | `APP_PING` | `SP_OMS_PUSH_UPGRADE_PROGRESS` ⚠ | — |
| 823 | `APP_HELLO` | `SP_OMS_BAT_STATE` ⚠ | — |
| 824 | `OMS_RUN_STATE` | `SP_OMS_RUN_STATE` | ✓ |
| 825 | `OMS_TASK_LIST` | `SP_UPGRADE_RESULT_EXIT` ⚠ | — |

Our handshake codes (APP_PASSWORD_INFO/TOKEN/PING/HELLO) don't have a home in
the decompile table at all. They may be:
- app-private codes the decompile didn't classify as `SP_*` (most likely)
- codes in a different section of the decompile we haven't read yet
- legacy/unused

The OMS codes 817–823 are **all** mis-numbered in our catalog.

## Recommended fix

This audit is the source of a multi-PR effort:

### PR status

| PR | Scope | Status | Commit |
|---|---|---|---|
| PR 1 | camera block 258–311 | **pending** (large-surface; needs sign-off before renumbering `CAM_*` constants) | — |
| PR 2 | file/SD block 770–798 | **pending** (depends on PR 1) | — |
| PR 3 | WiFi/system 799–816 (cellular reclassification) | **shipped 2026-08-31** | `7df0d8e` |
| PR 4a | OMS block 817–823 (annotate variant — keep our names, add audit docstrings) | **shipped 2026-08-31** | `693d5d8` |
| PR 4b | OMS block 817–823 (full renumber to `SP_OMS_*`) | **pending** (needs sign-off) | — |
| PR 5a | harden the catalog (CI assertion: every `CommandTable` parse lambda has matching `SimulatedProtocol` dispatch) | **shipped 2026-08-31** | `855e1f9` |
| PR 5b | harden the catalog (canonical-list allowlist gate on `BurstTest`) | **shipped 2026-08-31** | `39491f1` |

> **Cross-project note.** When any of these PRs is shipped, check whether the
> behavior change has firmware implications. If yes, open a patcher issue,
> apply the `affects-patcher` label on this side, and reference
> `ian-morgan99/benro-polaris-firmware-patcher#NN` in the body. See
> [`docs/CROSS-PROJECT.md`](./CROSS-PROJECT.md). PR 3 did **not** trigger this
> convention — it is a constant rename plus audit annotations only, with no
> wire-level behavior change.

### PR 1 (urgent, blocks camera UI): camera block
- Replace the 22 `CAM_GET_*/SET_*` constants with a smaller set of 12 `SP_SET_*` /
  `SP_GET_*_INFO` constants matching the decompile.
- Decouple the code number from the getter/setter label: one `SP_SET_ISO = 258`
  used for both directions, parse the response by key.
- Update `SimulatedProtocol.kt:209-…` dispatch (currently broken-labels) to the
  correct codes.
- Update `CommandTable.kt:212-…` to use the new `SP_*` names.
- Update `CommandTableTest.kt:58-77` and `SimulatedProtocolTest.kt:54-56`.

### PR 2 (when implementing file browser): file/SD block
- Realign 770–798 with the decompile.
- Decide what to do with codes that exist in our catalog but not in the decompile
  (FILE_THUMB, FILE_THUMB_LIST, FILE_PROTECT, FILE_UNPROTECT, FILE_DELETE_ALL,
  FILE_UPLOAD_CHUNK, FILE_UPLOAD_END, FILE_RENAME) — drop them or prove they
  exist in a newer firmware.
- Drop `FILE_RENAME = 798` (commit 466ae56 already moved it; should be removed).

### PR 3 (when implementing cellular config): WiFi/system block
- Add the cellular block (799, 808–816) and the warning-tone (804/805).
- Keep WiFi band (802/803) as-is.
- Reclassify 808/809 to cellular if live-capture confirms.

### PR 4a (done) / PR 4b (when implementing OMS panel): OMS block
- **PR 4a (shipped `693d5d8`):** add audit docstrings to 817–825. Keep our
  names. Captures the decompile reading as evidence without taking the
  renumber risk.
- **PR 4b (pending):** realign 817–823 to the correct `SP_OMS_*` constants.
  Either find a home for our `APP_*` handshake codes (820–823) or document
  them as app-private (likely they are; see audit docstring on `APP_TOKEN = 821`).

### PR 5a (done) / PR 5b (done) / future PR 5: harden the catalog
- **PR 5a (shipped `855e1f9`):** CI assertion that every `CommandTable`
  descriptor with a non-`null` `parse` lambda has a matching `SimulatedProtocol`
  dispatch entry.
- **PR 5b (shipped `39491f1`):** canonical-list allowlist gate. The `BurstTest`
  `--full` codeset is pinned to `CommandTable.BURST_PRE_CAMERA_CODES`, and
  that constant is in turn pinned to the audit doc's live-captured set
  (808, 809, 802, 778, 779, 775, 824, 524, 543, 780, 525). Either side of
  the gate must move in lockstep with the other.
- **Future PR 5:** 808 and 809 are still suspect — the decompile reads them
  as `SP_SOCKET_CLIENT_TYPE` (response) and `SP_SET_CELLULAR_APN`. Resolve
  via live-capture of the actual `ver:` and `sn:` payload source. Until then,
  treat PR 5b's allowlist as the de-facto gate.

## How to verify each PR on hardware

The protocol's "ground truth" is the live gimbal at `192.168.0.1:9090`. The
catalog is a *model* of that ground truth; this section is the test plan that
turns each PR from "code matches decompile" into "code matches the gimbal".

### Pre-flight (every PR)

1. **Wake the head.** Walk the gimbal off sleep (pick it up, jog the joystick,
   or send a UDP probe). The polestar_app does *not* respond on `9090` when
   the head is asleep, even though its wifi AP is up. The bundled
   `wake-and-probe.sh` script automates a BT-wake pulse + SSH/UDP first-look:
   ```bash
   ./docs/evidence/gimbal-ssh-2026-08-31/wake-and-probe.sh
   ```
   See the script's header comment for the BT-MAC + flow.
2. **Confirm the API is alive.**
   ```bash
   nc -w 2 -z 192.168.0.1 9090 && echo ok || echo dead
   ```
3. **Firmware on the head should be the factory build (4.0.0.32).** The
   `FwPkt.zip` at `/app/sd/FwPkt.zip` is the factory image; the in-flight
   firmware update is **not** the place to verify these PRs — the update may
   change the OMS surface, which would invalidate the §4.7 reclassification.
4. **Capture a baseline.** Run
   ```bash
   ./gradlew :tools:cli-probe:run --args="burst --full"
   ```
   before *and* after the PR. The two traces should be byte-identical for
   PRs 3, 4a, 5a, 5b (no behaviour change). PRs 1, 2, 4b will *intentionally*
   diverge — that is the verification.

### PR 3 (shipped — `7df0d8e`)

No new behaviour. The constant rename of `WIFI_BAND = 799` → `GET_CELLULAR_STATE`
is only safe because no `CommandTable` descriptor references 799. To confirm:
```bash
grep -RIn "Codes\.WIFI_BAND\|Codes\.GET_CELLULAR_STATE" shared tools composeApp
# Should show only Codes.kt (definition) and SimulatedProtocol.kt:329
# (dispatch). No consumer should reference the old name.
```

### PR 4a (shipped — `693d5d8`)

No new behaviour. The audit docstrings in `Codes.kt:222-275` are the change;
they are visible on every `:shared:jvmTest` and `:shared:lintKotlinMain` run.
Diff to look for:
```bash
git show 693d5d8 -- shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/Codes.kt
```

### PR 4b / PR 1 / PR 2 (pending — wholesale renumber)

The verification is: **the live gimbal must respond to the new code with the
expected payload shape.** For each renamed constant, send a one-shot frame
and check the response.

Worked example (planned for PR 4b):
```kotlin
// expected: app-ping ack on the NEW code for "upgrade progress"
sendFrame(SP_OMS_PUSH_UPGRADE_PROGRESS)  // 822 in either naming
expect response.startsWith("ret:0;")
```
If the response comes back on a *different* code, the decompile is wrong, and
the PR is not safe to merge.

### PR 5a / PR 5b (shipped — `855e1f9` / `39491f1`)

CI-only. Run
```bash
./gradlew :shared:jvmTest :tools:cli-probe:test --rerun-tasks
```
PR 5a's assertion (every `CommandTable` parse lambda has a matching
`SimulatedProtocol` dispatch) fires if you add a new descriptor without
dispatch. PR 5b's assertion (the `BurstTest --full` codeset equals
`CommandTable.BURST_PRE_CAMERA_CODES`, and that constant equals the audit
doc's live-captured set) fires if you reorder `BURST_PRE_CAMERA` without
updating the audit doc.

### Rollback (any PR)

The audit doc is the rollback plan:
1. `git revert <commit>` — no behaviour lost because none was added.
2. Re-run `./docs/evidence/gimbal-ssh-2026-08-31/wake-and-probe.sh` — the
   live trace should be identical to the pre-PR trace (modulo the PR's
   intentional changes). For a post-firmware-update device, also run
   `./docs/evidence/gimbal-ssh-2026-08-31/post-fw-update-probe.sh` (or
   `POST_UPDATE=1 ./docs/evidence/gimbal-ssh-2026-08-31/wake-and-probe.sh`
   to chain).
3. If the live trace diverges, the PR is wrong, not the gimbal. Reopen the
   audit doc and either amend the decompile reading or capture more data.

## What this audit did NOT find

- **Code 780 collision** — already fixed in commit 466ae56.
- **Parser infinite loop** — already fixed in commit c7a4b91.
- **Burst test regression** — already covered in commit c7a4b91.

## References

- Decompile source: `polaris-re-results.md` §4 (lines 100–252)
- Live capture provenance: `docs/CONNECTIVITY-FINDINGS-2026-08-31.md`
- Burst verification: `tools/cli-probe/src/test/kotlin/dev/openpolaris/probe/BurstTest.kt`
- 780 collision fix: git commit `466ae56`
- Burst hardening: git commit `c7a4b91`
