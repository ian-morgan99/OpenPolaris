# Firmware Upload / Install -- Byte-Level Audit vs Benro Connect

**Date:** 2026-09-01
**Author:** OpenPolaris automated audit
**Scope:** Every opcode, every payload byte, every state-machine step of the
firmware upload + install flow in the existing OpenPolaris code, compared to
the Benro Connect Android app (decompile side) and the live on-device
`polestar_app` side. Also covers the actual *physical* install path the
production firmware uses.
**Source of truth:**
`docs/evidence/gimbal-ssh-2026-08-31/fw-install-flow.txt`,
`docs/evidence/firmware-update-2026-08-31/README.md`,
`docs/PROTOCOL-CODE-AUDIT-2026-08-31.md`,
`shared/src/commonMain/kotlin/dev/openpolaris/core/domain/FirmwareUpdateController.kt`.

## TL;DR -- three things the user must know

1. **The on-device production firmware does NOT install firmware over the
   network.** The actual install is **SD-card based**: `FwPkt.zip` lands at
   `/app/sd/FwPkt.zip`, the on-board `polestar_app` detects it, runs
   `getFwInfo.sh` to MD5-verify, and either writes to NAND + reboots (PASS) or
   deletes the package + aborts (FAIL). The Benro Connect app's "firmware
   update" feature is literally a `scp -t /app/sd/FwPkt.zip` push
   (see `docs/evidence/firmware-update-2026-08-31/README.md`).
2. **The current OpenPolaris `FirmwareUpdateController` (810 -> 784 -> 794
   -> 795 -> 811 -> 812) is reconstructed from a decompile hypothesis that is
   not confirmed on hardware.** The Benro Connect decompile audit explicitly
   lists 794, 795 as "(gap)" -- they were named by pattern, not observed. The
   same audit lists 810, 811, 812, 813 as **UNVERIFIED**. 811's
   decompile-side name is `SP_GET_CELLULAR_IMSI`, which suggests 811 is
   **not** firmware progress at all -- it's the cellular IMSI read, with a
   coincidentally matching `p:N;` payload shape.
3. **The upload chunk (794) wire format is unknown.** The controller
   currently sends `len:N;` only and never transmits the actual zip bytes.
   The controller's own comment admits this:
   *"the binary-blob framing is the next phase once a captured chunk appears
   in the live trace"*
   (see `FirmwareUpdateController.kt:230-231`).

If the user wants the desktop app to **actually update a real Polaris**
today, the right path is: **use the existing `ScpFirmwareDelivery` to push
`/app/sd/FwPkt.zip` and reboot**, NOT the 810/784/794/795/811/812 sequence.
The controller sequence may work for *Benro Connect's app-private state
machine* on a custom build, but is not what the production firmware speaks.

---

## 1. What the production firmware actually does

`docs/evidence/gimbal-ssh-2026-08-31/fw-install-flow.txt` is a live SSH
capture of the on-device `polestar_app` from a failed install attempt. The
exact install path is:

1. Boot detects `/dev/mmcblk0p1` (vfat) is mounted at `/app/sd`.
2. `polestar_app` runs the state machine. The relevant states
   (`strings /app/bin/polestar_app`):
   - `EXDEV_UPGRADE_STA_SEND_START`
   - `EXDEV_UPGRADE_STA_WAIT_START_RESPONSE`
   - `EXDEV_UPGRADE_STA_WAIT_SEND_DATA`
   - `EXDEV_UPGRADE_STA_SEND_STOP`
   - `EXDEV_UPGRADE_STA_SEND_FAIL`
   - `EXDEV_UPGRADE_STA_WAIT_FAIL_RESPONSE`
   - `SP_EVENT_UPGRADE_FAIL` / `SP_EVENT_UPGRADE_SUCCESS`
   - `OMS_UPGRADE_STA_LOAD_FW`
   - `OMS_UPGRADE_STA_CHECK_FW PASS` / `FAIL`
   - `OMS_UPGRADE_STA_SEND_DATA_FINISH`
   - `OMS_UPGRADE_STA_SEND_CANCLE` (sic -- typo in firmware)
   - `OMS_UPGRADE_STA_FAIL` / `SUCCESS` / `END`
3. The package is **unzipped** from `/app/sd/FwPkt.zip` (or
   `ExDevFwPkt.zip` for external devices) into `/app/sd/FwPkt/`:
   ```
   unzip /app/sd/FwPkt.zip -d /app/sd/
   ```
4. `getFwInfo.sh` MD5-sums each file:
   ```
   /app/sd/FwPkt/camera/config
   /app/sd/FwPkt/camera/uImage
   /app/sd/FwPkt/camera/rootfs.ubifs
   /app/sd/FwPkt/camera/appfs.ubifs
   /app/sd/FwPkt/gimbal/polaris403_*.bin
   /app/sd/FwPkt/gimbal/polaris413_*.bin
   ```
   and writes `/app/sd/FwPkt/crcInfo`, which `polestar_app` reads back.
5. If MD5 matches `crcInfo`: write to NAND + auto-reboot.
6. If MD5 mismatches (`crc[%d] != %d;`): `rm -r /app/sd/FwPkt` and
   `rm -r /app/sd/FwPkt.zip` and abort.

**The on-device state machine reads the SD card. It does not pull bytes
from port 9090.** The 810/784/794/795 sequence in our catalog must
therefore be a *Benro Connect app-private* channel that talks to the
on-board state machine -- i.e. the protocol a "smart" client uses to drive
a remote SD-card install instead of `scp`. But this channel is **not
what the on-board polestar_app listens to for its actual install**; it's a
side-channel.

## 2. Byte-level comparison: each opcode

### 783 -- `FILE_INFO` / decompile: `SP_SET_UPGRADE_START` (no payload)

- **Decompile:** no payload. Probably the "kick the on-board state machine"
  signal: "wake up, scan /app/sd for FwPkt.zip".
- **Live evidence:** none -- this code has never been live-captured (per
  audit, marked with a warning).
- **OpenPolaris:** `FILE_INFO = 783` is named by pattern, not decompile
  (audit table 4.5 row 783). It is *not* invoked by
  `FirmwareUpdateController`. **No code change needed** because the
  controller uses 810 for the precondition, not 783.

### 784 -- `FILE_UPLOAD_FW` / decompile: `SP_LOAD_UPGRADE_FW_STATE` (resp)

- **Decompile name:** "load the upgrade firmware state" -- implies this is
  a **response** opcode, not a request. `SP_LOAD_*` is the same naming
  pattern as `SP_LOAD_EXDEV_FW_STATE` (which is 792's decompile side).
  These are *queries that the app pushes to the client* to advertise the
  on-board state.
- **Our usage:** `FirmwareUpdateController.startUpload` sends 784 with
  payload `"size:$totalSize;name:FwPkt.zip;"` and expects `ret:0;` back.
- **Likely wrong.** If 784 is a *response* opcode, sending 784 from the
  client would be a no-op or a protocol error. The "I want to start
  uploading firmware" request is almost certainly a *different* code that
  the decompile classified as `SP_SET_UPGRADE_START` (which is 783, also a
  "set start" semantic, but a no-payload fire-and-forget).
- **Open question:** does the Benro Connect app ever send 784? Or does it
  only *receive* 784 as a push from the on-board app? Without a Benro
  Connect traffic capture, we cannot tell.

### 794 -- `FILE_UPLOAD_CHUNK` / decompile: **(gap)**

- **Decompile:** this code does not appear in the Benro Connect decompile
  at all (audit: 794 "(gap) -"). It was named by pattern after 784, 795.
- **Our usage:** `FirmwareUpdateController.sendChunk` sends 794 with
  payload `"len:N;"` only -- **no actual bytes**. Comment at lines
  225-231 explicitly documents this is a placeholder pending a real
  capture:
  > "We send an empty payload here and rely on the live-captured
  > simulator response of `ret:0;` -- the JVM side of the test only needs
  > to assert we sent the right code; the binary-blob framing is the
  > next phase once a captured chunk appears in the live trace."
- **Likely the wrong opcode.** If the on-board app does not actually pull
  bytes over port 9090 (see section 1), then there is no 794 to send. The
  Benro Connect app probably uses a wholly different mechanism -- most
  likely a purpose-built binary protocol on a different port, or a TLS
  tunnel, or a raw `scp` over a separate channel that doesn't show up in
  the decompile's `SP_*` table.
- **POLARIS-FUNCTIONS-REPORT.md row 164** asserts the chunk payload is
  `data:base64;` but provides no source -- same pattern-name guess as our
  controller.

### 795 -- `FILE_UPLOAD_END` / decompile: **(gap)**

- **Decompile:** also not present (audit: 795 "(gap) -").
- **Our usage:** `FirmwareUpdateController.endUpload` sends 795 with
  payload `""`. Same guess-construction as the rest of the sequence.
- **Likely the wrong opcode** for the same reason as 794.

### 810 -- `SYS_FW_UPGRADE` / decompile: **(gap)**

- **Decompile:** not present (audit: 810 "(gap) - | UNVERIFIED (H3
  hypothesis)"). The H3 hypothesis was that 810 is "the only H3 trigger
  we know of" -- but H3 itself was a guess about an app-private handshake
  layer.
- **Our usage:** `armFirmwareUpgrade` sends 810 with payload `"type:0;"`.
  The decompile audit flags this as a guess.
- **The on-board truth:** the on-board state machine is driven by **the
  presence of `/app/sd/FwPkt.zip`**, not by an 810 frame. The "arm" step
  does not exist on production firmware -- there is nothing to arm.

### 811 -- `SYS_FW_PROGRESS` / decompile: `SP_GET_CELLULAR_IMSI`

- **Decompile:** `SP_GET_CELLULAR_IMSI` (cellular modem query). The
  `p:N;` shape is **coincidental** -- IMSI is encoded the same way (a
  single number in a key/value pair).
- **Our usage:** `pollProgress` polls 811 with no payload, expects `p:N;`
  back.
- **Highly likely wrong.** The audit explicitly warns:
  > "Our 808/809/811/812/813/814/815/816 are all mis-labelled."
  811's wire shape (`p:N;`) is identical to the cellular IMSI shape
  because **both are just a single key with a number**, not because 811
  is firmware progress.
- **The on-board truth:** firmware progress is logged to `/app/Mlog.txt`
  (see `OMS_UPGRADE_STA_CHECK_FW PASS/FAIL`,
  `SP_EVENT_UPGRADE_SUCCESS/FAIL` strings in `fw-install-flow.txt`).
  There is no network-side progress push.

### 812 -- `SYS_REBOOT` / decompile: `SP_GET_CELLULAR_IMEI`

- **Decompile:** `SP_GET_CELLULAR_IMEI` (read the modem's IMEI). The
  decompile is for a *query*, not a *destructive* request.
- **Our usage:** `reboot()` sends 812 with empty payload, expects
  `ret:0;`.
- **Wrong on two counts.** (a) The decompile classifies 812 as a
  *query*, not a reboot. (b) The on-board truth is that **the install
  auto-reboots itself** if `OMS_UPGRADE_STA_SUCCESS` is reached; the
  client does not trigger the reboot. Reboot is a side-effect of a
  successful NAND write.
- **Destructive risk:** if 812 is actually `SP_GET_CELLULAR_IMEI`,
  sending it with no payload is harmless (a no-op query response). But
  if 812 is actually the reboot on some build variants, sending it
  accidentally reboots the gimbal mid-install. The audit marks this as
  "destructive -- never exercised" for exactly this reason.

### 813 -- `SYS_SHUTDOWN` / decompile: `SP_SET_CELLULAR_COMUSB`

- **Decompile:** `SP_SET_CELLULAR_COMUSB` (configure USB mode of the
  cellular modem), payload `usbmode:..;`. The audit table notes the live
  wire is `usbmode:..;`.
- **Our usage:** none in `FirmwareUpdateController`. Only referenced in
  `FeatureFlags.allowShutdown` which gates the shutdown button.
- **Wrong on two counts:** (a) 813 is a *USB mode setter*, not shutdown.
  (b) There is no network-side shutdown -- the on-board state machine
  handles power via the gimbal's own power button or `poweroff` shell
  command over SSH.

## 3. What OpenPolaris does today (existing code)

| OpenPolaris path           | What it sends                  | What it expects        | Verdict                                                 |
| -------------------------- | ------------------------------ | ---------------------- | ------------------------------------------------------- |
| `armFirmwareUpgrade` (182) | `810@type:0;`                  | `1&810&N&state:N;#`    | Wrong opcode (decompile gap)                            |
| `startUpload` (204)        | `784@size:N;name:FwPkt.zip;`   | `ret:0;`               | Likely wrong (784 is a *response* code per decompile)   |
| `sendChunk` (225)          | `794@len:N;`                   | (none -- fire-and-forget) | **Bytes are never sent.** Wrong opcode (decompile gap). |
| `endUpload` (240)          | `795@`                         | `ret:0;`               | Wrong opcode (decompile gap)                            |
| `pollProgress` (261)       | `811@`                         | `p:N;`                 | Wrong opcode (811 is cellular IMSI per decompile)       |
| `reboot` (300)             | `812@`                         | `ret:0;`               | Wrong opcode (812 is cellular IMEI per decompile); also on-board reboots itself |

**Net:** the entire 6-step sequence in `FirmwareUpdateController` is built
on decompile hypotheses, three of which (810/811/812) the audit marks
**UNVERIFIED**, and two of which (794/795) are *explicitly absent from the
decompile*.

The controller passes its own internal tests (all 9 cases in
`FirmwareUpdateControllerTest`) because the tests use a `FakeConnection`
that round-trips whatever the controller sends and returns the payload
the controller expects to parse -- i.e. the tests validate the *Kotlin
logic* but not the *wire format against the real firmware*.

## 4. What the Benro Connect app does (best inference, not direct evidence)

The exact byte sequence Benro Connect uses is **not known** from any
source we have. Three lines of indirect evidence:

1. **Live evidence:** the Benro Connect app installs firmware by *the
   same SD card write* -- the `scp -t /app/sd/FwPkt.zip` process is
   visible from SSH during a Benro Connect install
   (see `docs/evidence/firmware-update-2026-08-31/README.md`).
   So the Benro Connect app either:
   - (a) runs `scp` internally (most likely, given that `scp` is what's
     running), or
   - (b) pushes the zip via port 9090, and we have no captured trace
     of that push.
2. **Decompile:** the Benro Connect decompile's `SP_SET_UPGRADE_START`
   (783) and `SP_EXDEV_UPGRADE_START` (791) are the closest things to
   "start install" signals. Both have no payload and both are sent *to*
   the on-board app. There is no `SP_UPLOAD_CHUNK` or `SP_UPLOAD_END`
   in the decompile.
3. **Plausibility:** the decompile "fingerprint" suggests the on-board
   `polestar_app` is what actually pulls from the SD card or the network
   side-channel. The client side's only role may be to (a) push the zip
   to the SD, and (b) send `SP_SET_UPGRADE_START` (783) to kick the
   state machine. After that, the on-board handles the rest.

**Most likely Benro Connect sequence** (still speculative, ranking the
hypotheses):

1. **Push zip to `/app/sd/FwPkt.zip`** (via `scp` over SSH to
   root@192.168.0.1, the same path the live evidence shows). This is
   the de-facto install transport.
2. **Send 783 `SP_SET_UPGRADE_START` (no payload)** to tell
   `polestar_app` "scan /app/sd now".
3. **Wait** for the on-board state machine to finish, polling
   `/app/Mlog.txt` over SSH for `SP_EVENT_UPGRADE_SUCCESS` / `FAIL` or
   `OMS_UPGRADE_STA_CHECK_FW PASS/FAIL`.
4. **If SUCCESS, the gimbal reboots itself.** No client-side reboot
   needed.

This sequence matches both the live evidence (the `scp` process) and
the decompile names (783 is `SP_SET_UPGRADE_START`, a no-payload
fire-and-forget kick).

## 5. Recommended fix

### 5a. Phase 1 -- make the existing code honest (no functional change)

- Update `FirmwareUpdateController` to:
  - Drop the `810 -> 784 -> 794 -> 795 -> 811 -> 812` sequence as the
    *primary* path. Keep it as a feature-flagged "experimental Benro
    Connect network-side" path.
  - Add a new primary path: **SSH push + state-machine poll** that
    mirrors the production firmware's actual flow:
    1. `ScpFirmwareDelivery` to `root@<host>:/app/sd/FwPkt.zip`
       (already exists).
    2. (Optional) send `783@` if the build supports it (feature-flag).
    3. Poll `/app/Mlog.txt` for `SP_EVENT_UPGRADE_SUCCESS` or
       `OMS_UPGRADE_STA_CHECK_FW PASS` over SSH.
    4. Wait for the auto-reboot (poll TCP 9090 -- if it goes away and
       comes back, the install completed).
- Mark all the decompile-hypothesis opcodes (`810/784/794/795/811/812`)
  in `Codes.kt` as `// UNVERIFIED -- see
  docs/FIRMWARE-UPLOAD-AUDIT-2026-09-01.md`.

### 5b. Phase 2 -- capture real Benro Connect traffic

- Use
  `tcpdump -i wlan0 -w /tmp/polaris.pcap 'host 192.168.0.1 and port 9090'`
  while running a Benro Connect install. Extract the frame stream with
  Wireshark (already installed at `/usr/bin/wireshark`).
- This is the **only** way to know the actual byte sequence. Without
  it, all current code is reconstruction.

### 5c. Phase 3 -- when the capture exists

- Update `FirmwareUpdateController.sendChunk` to actually transmit the
  zip bytes per the captured `len:N;data:base64;` or whatever the real
  framing is.
- Update the start/end payloads if they differ.
- Add an end-to-end test that uses a recorded pcap as the simulated
  mount's reply stream.

## 6. Code changes that ARE safe to make today (Phase 1a)

These are documentation + UI changes only, no protocol change:

1. **`FirmwarePane` UI:** add a clearly-marked "experimental -- may
   brick the gimbal" warning above the upload button. The current
   button has no warning.
2. **`FirmwarePane` UI:** add a "verify before upload" toggle that
   hashes the local zip and compares to a CRC the user can paste from
   the Benro Connect app. This mirrors the `crcInfo` MD5 step in the
   production firmware.
3. **`FirmwareUpdateController.start`:** detect the case where
   `bytes.isEmpty()` and return `Status.Failed("no firmware bytes")`
   (already does this) and **add** detection for `bytes.size > 128 MB`
   (the free SD space is 121 MB per the evidence; refuse anything
   larger).
4. **`ScpFirmwareDelivery`:** add a pre-flight check that the
   destination's `/app/sd` has at least `bytes.size + 1 MB` free. This
   catches oversize zips before the slow push.
5. **Codes.kt:** add a `@Suppress("unused")` and a deprecation note to
   `SYS_FW_UPGRADE`, `FILE_UPLOAD_FW`, `FILE_UPLOAD_CHUNK`,
   `FILE_UPLOAD_END`, `SYS_FW_PROGRESS`, `SYS_REBOOT`, `SYS_SHUTDOWN`.
   None of these is verified on the production firmware.

## 7. What we cannot determine without more evidence

- The **exact 794 payload format** (binary, base64, hex, plain zip?).
- Whether 810 is a real "arm" opcode, a no-op, or a misclassification.
- Whether 811's `p:N;` is firmware progress or cellular IMSI (the
  audit prefers the latter).
- Whether 812 actually reboots or reads IMEI (the audit prefers the
  latter, but the decompile is for a different firmware version).
- What 794/795 even do on the production firmware (decompile gap, no
  live evidence).
- What Benro Connect sends on the wire during a real install.

## 8. Cross-references

- `docs/PROTOCOL-CODE-AUDIT-2026-08-31.md` -- the source of the
  decompile-side mapping
- `docs/evidence/gimbal-ssh-2026-08-31/fw-install-flow.txt` -- live
  on-device state machine + install path
- `docs/evidence/firmware-update-2026-08-31/README.md` -- live `scp`
  evidence of the Benro Connect install
- `shared/src/commonMain/kotlin/dev/openpolaris/core/domain/FirmwareUpdateController.kt`
  -- the controller being audited
- `shared/src/jvmMain/kotlin/dev/openpolaris/core/domain/ScpFirmwareDelivery.kt`
  -- the working SSH-based delivery (the only verified part of the
  chain)
- `shared/src/commonMain/kotlin/dev/openpolaris/core/config/FeatureFlags.kt`
  -- `firmwareUpload` gate (OFF by default, must be flipped by the
  user via Settings)
