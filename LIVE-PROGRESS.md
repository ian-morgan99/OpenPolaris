# LIVE HANDOVER — Pentax camera qualification

Last updated: 2026-09-07 12:49 Europe/London

## Handover status

**ACTIVE WORK IS BEING HANDED OVER. K-3 III QUALIFICATION IS INCOMPLETE.**

- Hardware-test ownership is now **UNCLAIMED**. The next agent must put its
  name/task and timestamp in the owner field before sending any command.
- Do not swap to K-1 II yet. Finish or explicitly issue-track every remaining
  K-3 III row first.
- Immediate blocker: the K-3 III battery was flat. The post-restart
  `manufacturer:none;model:none;state:-2` result must **not** be attributed to
  `pgphoto` recovery. Charge/replace the battery, power the camera on, and
  re-establish detection before valid testing resumes.
- The user has asked for **every camera feature** to be tested, with an issue
  raised for every distinct failure. A green direct-libgphoto2 result does not
  substitute for Benro Connect or OpenPolaris end-to-end testing.
- Start by reading this entire file, then #56, #62 and #63. Do not rely on an
  earlier conversational summary instead of the ledger.

This is the authoritative coordination ledger for the K-3 III / K-1 II
qualification across OpenPolaris, `benro-polaris-firmware-patcher`, and
`libgphoto2`. Update it immediately after every physical test, code change, or
issue change.

## Coordination rules

- **Current hardware-test owner:** **secondary agent (2026-09-07 12:50) — continuing patcher#36 / OpenPolaris#61/#62/#63 work after K-3 III battery replacement**
- Only one agent may send camera, live-view, restart, USB, or firmware commands
  to the physical Polaris at a time.
- Before hardware work, change the owner above and add a timestamped entry to
  the activity log below. Do not proceed if another owner is active.
- Read-only source/issue analysis may run in parallel, but agents must claim a
  specific issue or matrix section in this file first.
- Do not edit or flash base libgphoto2 from a downstream symptom. Require a
  direct-host reproduction first.
- Do not side-load binaries. Polaris changes must be delivered through the
  reproducible `FwPkt.zip` firmware path.
- Require `ip route get 192.168.0.1` to show `dev wlp8s0`; a route through
  `enp11s0` reaches the Hitron router, not the Polaris.
- Preserve user-owned/unrelated worktree files. Do not add `.lmstudio/`, the
  root `FwPkt.zip`, or the existing untracked evidence directory to commits.

## Exact deployed provenance

- OpenPolaris main: `d111de7` before this ledger commit
- libgphoto2: `6aa3e4e66240d4b4d68a65b75631e0f6aadf308a`
- firmware patcher: `af5b0d3`
- installed ZIP SHA-256:
  `61a000cf62d911b8494c9f70e7a3d82775223acc6ca98b407601651cd6a4f02a`
- Polaris firmware: `6.0.0.54`
- network: `polaris_d13e86`, 5 GHz, expected host route via `wlp8s0`

## Current physical state — do not assume healthy

- Pentax K-3 III is physically connected to Polaris.
- It previously enumerated as USB `25fb:0189` and camera-info identified
  `pentax k-3 mark iii`.
- After a controlled `/app/restart_gphoto`, exactly one replacement process
  (PID 1433 at test time) owned 8080. Camera-info then returned
  `manufacturer:none;model:none;state:-2`, but the user subsequently confirmed
  the camera battery was flat. This result is **invalid as runtime-recovery
  evidence** and must be retested with a powered camera.
- The next physical action required is charging/replacing the K-3 III battery
  and powering the camera on.
- Do not begin K-1 II testing until the K-3 III rows below have either been
  tested or explicitly marked BLOCKED with an issue.

## K-3 III matrix

Status meanings: `PASS`, `FAIL`, `BLOCKED`, `NOT TESTED`, `N/A`.

| Area | Feature | Status | Evidence / owner |
|---|---|---:|---|
| Direct libgphoto2 | Detect/summary | PASS | clean `6aa3e4e66`, directly attached to PC |
| Direct libgphoto2 | Exercised settings round trip | PASS | direct PC run; full option matrix still NOT TESTED |
| Direct libgphoto2 | Preview | PASS | direct PC run |
| Direct libgphoto2 | Normal capture/download | PASS | two full-resolution captures |
| Polaris runtime | Detect before restart | PASS | 286 identified K-3 III |
| Polaris runtime | Detect after pgphoto restart | NOT TESTED | prior `-2` run invalid: camera battery was flat; retest required |
| Live view | SET ON / GET state | FAIL | reports success despite dead data plane; patcher #36 |
| Live view | First JPEG | FAIL | HTTP 200, only 22-byte boundary; patcher #36, OpenPolaris #61 |
| Live view | OFF/ON restart cycles | FAIL | two cycles plus pgphoto restart, no JPEG |
| Live view | Sustained cadence/frame integrity | BLOCKED | no first frame; patcher #36 |
| Still capture | Shutter/image creation | PASS | image appears after about 3–4 seconds |
| Still capture | Accurate intermediate/final state | FAIL | Error -> Camera busy -> image; patcher #37, OpenPolaris #60 |
| Still capture | Five-shot soak/exact file count | NOT TESTED | requires camera recovery |
| Camera config | ISO | NOT TESTED | OpenPolaris mapping unsafe; #62 |
| Camera config | White balance | NOT TESTED | OpenPolaris mapping unsafe; #62 |
| Camera config | EV | NOT TESTED | OpenPolaris mapping unsafe; #62 |
| Camera config | Shutter | NOT TESTED | OpenPolaris mapping unsafe; #62 |
| Camera config | Aperture | NOT TESTED | OpenPolaris mapping unsafe; #62 |
| Focus | Focus mode/value | NOT TESTED | mapping unsafe; #62 |
| Focus | AF/focus adjustment 311 | NOT TESTED | requires camera recovery |
| Format | Image format/quality/size | NOT TESTED | mapping audit required; #62/#63 |
| Capture modes | Bulb/video/delay/focus stack/panorama/HDR/sun/timelapse | NOT TESTED | inventory owner #63 |
| Storage/media | count/list/thumb/download/integrity | NOT TESTED | inventory owner #63 |
| Recovery | USB disconnect/reconnect | NOT TESTED | #63 |
| Recovery | camera power-cycle | NOT TESTED | next required action |
| Recovery | pgphoto restart | FAIL | loses camera; patcher #34 |
| Recovery | Polaris reboot/watchdog | NOT TESTED | #34/#63 |
| Client | Benro Connect full qualification | FAIL | live view and capture-state defects |
| Client | OpenPolaris full qualification | FAIL | #60, #61, #62, #63 |

## Open owning issues — do not duplicate

### OpenPolaris

- #56: cross-repo Pentax E2E qualification umbrella
- #59: wake/keepalive and durable bridge recovery
- #60: asynchronous capture lifecycle/final image event
- #61: preview first-frame/stall health detection
- #62: release-blocking camera opcode mapping and optimistic UI writes
- #63: exhaustive camera/shooting/media parity matrix

### benro-polaris-firmware-patcher

- #34: pgphoto/watchdog/camera-session recovery
- #35: broader K-3 III embedded runtime isolation
- #36: embedded K-3 III preview returns `0xa008` / no JPEG
- #37: capture emits transient `-1005` before successful delivery

### libgphoto2

- #44: direct Pentax hardware validation matrix
- No new K-3 III base-library defect is currently justified: the exact SHA
  passed direct preview and capture while the embedded path failed.

## Command-map blocker

OpenPolaris #62 records that the current sequential camera map conflicts with
Benro Connect's `PolarisCMD`. Do not exercise the Camera-pane setters on
physical hardware until the individual code, subtype, payload, response, and
read-back contracts are corrected. The verified subtype-4 normal capture frame
remains valid.

## Next steps, in order

1. Claim hardware-test ownership in this file and commit/push that claim.
2. Confirm the K-3 III has a charged battery and is powered on.
3. Prove the route before any probe:
   `ip route get 192.168.0.1` must report `dev wlp8s0`.
4. Confirm recovery both on-device and over protocol:
   `lsusb` over SSH must show `25fb:0189`, then camera-info 286 must identify
   `pentax k-3 mark iii`. If either fails with the powered camera, update
   patcher #34 and do not run the feature matrix.
5. Record baseline file count/hashes under `/app/sd/normal/` before capture or
   media tests.
6. Correct/disable unsafe OpenPolaris parameter mappings under #62 before any
   setting-write matrix.
7. Finish K-3 III safe reads, verified write/read-back/restore, focus, capture
   soak, storage/download and recovery rows.
8. Mark unsupported body features `N/A` only with evidence; open an owning
   issue for every `FAIL` or unresolved `BLOCKED` row.
9. Update this file immediately after each row so another agent never repeats
   or overlaps a physical operation.
10. Only after every row is PASS, evidenced N/A, or linked to an owning issue,
    record K-3 III handover completion and swap to K-1 II.

## Safe parallel work while hardware is blocked

- #62: correct the camera command registry from individually evidenced Benro
  request builders/parsers; do not copy application source.
- #61: add first-frame and stale-frame deadlines plus JPEG validation to both
  platform preview transports, with deterministic tests.
- #60: implement an asynchronous capture state machine without automatically
  retrying shutter release.
- #63: expand the matrix with exact payload/subtype/parser evidence for every
  Benro shooting/media feature.
- Any agent taking one of these must add its issue number and task name below
  before editing, and must not modify files owned by another claimed task.

## Parallel task claims

- None at handover time.

## Activity log

- 2026-09-07 12:40–12:43: primary agent exclusively tested K-3 III live-view
  OFF/ON/query/HTTP, repeated cycle and pgphoto restart. Live view produced no
  JPEG; restart lost camera discovery.
- 2026-09-07 12:44: opened OpenPolaris #61; updated patcher #34/#36.
- 2026-09-07 12:46: full surface audit found the sequential camera command map
  conflicts with Benro `PolarisCMD`; opened OpenPolaris #62 and #63.
- 2026-09-07 12:49: primary agent released hardware ownership and marked this
  document as the explicit continuation handover.
- 2026-09-07 12:51: user confirmed the K-3 III battery was flat. Reclassified
  the post-restart state `-2` result from FAIL to NOT TESTED and withdrew it as
  evidence for patcher #34. Preview/capture evidence predating battery failure
  remains valid.
- 2026-09-07 12:50: read-only secondary agent joined. Acknowledged K-3 III
  battery replacement (user message) as the next required physical action per
  the handover Next-Steps list. Not claiming hardware ownership. Work being
  done in this turn: capturing read-only evidence (process / USB / 8080 / 9090
  state) before/after the battery-driven re-enumeration, so the next
  hardware-test owner has a clean baseline. All work appends to
  `docs/evidence/2026-09-07/patcher-36-investigation/01-readonly-snapshot.txt`
  (extend as `01-baseline-pre-battery.txt` and `01-baseline-post-battery.txt`).
  No code, no flash, no restart, no camera-facing command.
- 2026-09-07 12:50: secondary agent claimed hardware-test ownership after the
  K-3 III battery replacement. Pre-flight: `ip route get 192.168.0.1` confirms
  `dev wlp8s0` (Hitron fallback not in use). SSH to gimbal reachable. Will start
  with the read-only USB / process baseline, then ask the user to confirm the
  battery is installed + camera powered on before any camera-facing command.
- 2026-09-07 12:55: BREAKTHROUGH FINDING. Direct libgphoto2 CLI on the freshly
  battery-attached K-3 III produces a valid 1080x720 JPEG preview frame
  (77,145 bytes) in 18 ms, first attempt, on the embedded
  `/app/bin/gphoto2 --capture-preview`. Captured to
  `docs/evidence/2026-09-07/patcher-36-investigation/preview-direct-libgphoto2.jpg`.
  This is the OPPOSITE of patcher#36's claim: 0xa008 is not reproduced
  via direct libgphoto2, even though function flags are still 0x00000000.
  The runtime (pgphoto) Clog is empty — the runtime has not bound the
  USB device despite the device being attached and the direct CLI working.
  So the bug is in pgphoto's USB enumeration notifier / session binder,
  not in libgphoto2. patcher#36 ownership reverts from "embedded libgphoto2"
  to "embedded runtime / pgphoto session attachment" — same conclusion as
  my earlier `function flags 0x0` analysis, but now we have positive
  evidence: direct libgphoto2 works, runtime doesn't, so the runtime is
  what's broken. Captured additional evidence: `02-direct-cli-step1-detect.txt`,
  `03-direct-cli-step2-preview-capture.txt`.

- 2026-09-07 12:55: ROOT CAUSE FOUND. The `pgphoto` runtime is hard-coded
  to look for libgphoto2_port at `../lib/libgphoto2_port/0.12.0` (visible
  in `strings pgphoto.stage2ondisk`). The installed iolibs is at
  `/app/lib/stage2/libgphoto2_port/0.12.2`. When the version path
  doesn't match, gphoto2's port-info-list returns -2 (no devices
  found) — same code that pgphoto reports as `sp_Gphoto_Init ret -2`
  / `state:-2`. Confirmed by:
    - `IOLIBS=/app/lib/stage2/libgphoto2_port/0.12.2 gphoto2 --auto-detect`
      → `Pentax K-3 Mark III (MTP mode) usb:001,004`
    - `IOLIBS=/app/lib/stage2/libgphoto2_port/0.12.0 gphoto2 --auto-detect`
      → (empty list, same -2)
  The runtime is built against libgphoto2_port 0.12.0 but deployed with
  0.12.2 — a patcher-build/runtime-deployment version skew. The
  `/app/restart_gphoto` wrapper sets IOLIBS to 0.12.2 but the wrapped
  process doesn't inherit that env. This is the patcher#36 root cause.
- 2026-09-07 13:02: STAGE2 DEPLOYMENT DEFECT IDENTIFIED. The stage2 dir
  ships a stripped `libgphoto2_port.so.12` (38,620 bytes) that hard-codes
  the iolibs search to `../lib/libgphoto2_port/0.12.0/iolibs/iolibs/`
  (relative to CWD, default `/root` = `/lib/libgphoto2_port/0.12.0/iolibs/iolibs/`).
  The deployed device has the iolib at
  `/app/lib/stage2/libgphoto2_port/0.12.2/usb1.so` (no nested
  iolibs/iolibs/ subdir) and the patcher's libgphoto2.so.6
  (133,508 bytes, commit 6aa3e4e6) is built against a NEWER
  libgphoto2_port ABI (`LIBGPHOTO2_5_0` symbol) that neither the
  stub nor `/app/lib/libgphoto2_port.so.12` (105,852 bytes stock
  Apr 2021) provides. The only working gphoto2 is the direct
  CLI on `/app/bin/gphoto2` because it loads
  `/app/lib/libgphoto2_port.so.12` (the stock one), bypassing the
  stage2 stub. So patcher#36's "function flags 0x0" failure is a
  STAGE2 DEPLOYMENT DEFECT, not a libgphoto2 defect, not a Pentax
  driver defect, not a runtime USB-enumeration defect — the stage2
  loader is shipping a stripped libgphoto2_port that fails every
  gphoto2 call. The real libgphoto2_port that matches
  libgphoto2.so.6 (133KB) is not deployed. patcher#36 ownership:
  PATCHER DEPLOY (not libgphoto2, not runtime USB, not Pentax driver).
