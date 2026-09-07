# K-1 II swap plan — executable by a junior agent

**Date**: 2026-09-07 15:20
**Author**: this agent (OpenPolaris session) — primary
**Backup agent**: any agent that has access to the bench with
the gimbal, the K-1 II, the PC, and the OpenPolaris repo.
**Goal**: swap the K-3 III for the K-1 II on the gimbal and
record the matrix state. No K-3 III rows are changed by this
plan. No firmware is flashed.

---

## 0. Pre-conditions (verify before doing anything else)

| # | Check | Expected result | If wrong |
|---|---|---|---|
| 0.1 | `pwd` | `/home/ian/Documents/VSCodeProjects/OpenPolaris` | `cd` to it |
| 0.2 | `git status` | clean working tree | commit/stash, then proceed |
| 0.3 | `git log --oneline -1` | `bc8f4b5 docs(livelog): K-3 III handoff docs complete — 4 design docs published` | `git pull` |
| 0.4 | `ssh root@192.168.0.1 cat /app/FwVer` | `FwVer:4.0.0.32;date:2025.05.09;` | STOP — gimbal not in expected state |
| 0.5 | `ssh root@192.168.0.1 cat /app/openpolaris-libgphoto2-provenance.txt` shows `git_commit=6aa3e4e66240d4b4d68a65b75631e0f6aadf308a` | yes | STOP — different libgphoto2 SHA |
| 0.6 | `ip route get 192.168.0.1` | `... dev wlp8s0 ...` | STOP — wrong interface, fix WiFi first |
| 0.7 | K-1 II body + battery | present on bench, battery charged | STOP — find a charged battery |
| 0.8 | K-1 II USB cable | present (the one that came with the camera, or a known-good data cable) | STOP — wrong cable, no data |

**If any pre-condition fails, STOP and write a follow-up
comment on OpenPolaris#56 explaining which check failed.**

## 1. Claim hardware ownership

Open the file `LIVE-PROGRESS.md` at the OpenPolaris repo root.
Find the line:

```
- **Current hardware-test owner:** **this agent (2026-09-07 14:10) — K-1 II swap and qualification, after K-3 III baseline capture recorded ...
```

Change it to:

```
- **Current hardware-test owner:** **<your agent name> (2026-09-07 <HH:MM>) — K-1 II swap, step N of K1II-SWAP-PLAN.md**
```

Replace `<your agent name>` and `<HH:MM>`. Replace `step N`
with the step you're about to do (start at "step 2 of 7").

Commit + push this change BEFORE touching the gimbal:

```sh
cd /home/ian/Documents/VSCodeProjects/OpenPolaris
git add LIVE-PROGRESS.md
git -c user.name="<your git user.name>" \
    -c user.email="<your git user.email>" \
    commit -m "handoff: claim K-1 II hardware ownership, step N of 7"
git push origin main
```

**Do not start work on the gimbal until the push succeeds.**

## 2. Confirm the K-3 III is the currently-attached camera

The K-3 III has USB id `25fb:0189`. Verify it is the connected
camera (not a different Pentax body):

```sh
ssh root@192.168.0.1 'lsusb 2>&1 | grep 25fb'
# Expected output:
# Bus 001 Device 004: ID 25fb:0189
```

If `lsusb` shows a different USB id, STOP — the wrong camera
is connected.

Then confirm `sp_Gphoto_Init` state is the known-broken one
(so we know we're not starting from a passing state):

```sh
ssh root@192.168.0.1 'echo "1&286&2&#" | nc -w 2 127.0.0.1 9090'
# Expected: timeout, connection refused, or "manufacturer:none;model:none;state:-2"
# ANY of these is OK — confirms runtime is in the broken state we expect
```

**Do not try to "fix" anything here. The broken state is the
starting point for the K-1 II test.**

## 3. Power off the gimbal and physically swap the camera

This step requires hands on the hardware.

1. Unplug the gimbal power (the wall adapter, not just a
   remote-off). Wait 10 seconds.
2. Disconnect the K-3 III USB cable from the gimbal.
3. Remove the K-3 III body from the gimbal mount.
4. Mount the K-1 II body on the gimbal mount. The K-1 II is
   physically larger than the K-3 III — use the L-bracket or
   the longer plate if needed. The hot shoe goes on top.
5. Connect the K-1 II USB cable:
   - One end to the K-1 II's USB port (left side, under the
     cover labelled "USB / HDMI")
   - Other end to the gimbal's USB port (the same one the
     K-3 III was using — usually the side or back, marked
     with a USB icon)
6. **DO NOT** power the gimbal back on yet. The USB
   hotplug-after-power-on path is the one we want to test.

If the K-1 II is connected to the gimbal before power-on, the
gimbal's cold-boot USB enumeration may not see it. This is a
**known K-1 II behavior** vs the K-3 III — the K-3 III was
hot-plugged in the K-3 III testing.

## 4. Power on the gimbal and wait for boot

1. Plug the gimbal power back in.
2. Wait **at least 60 seconds** for the gimbal to boot. The
   boot involves: NAND read, UBIFS mount, polestar init,
   polestar watchdog launch, WiFi AP bring-up.
3. The gimbal's WiFi AP (`polaris_d13e86`) should be
   visible. The host PC's `wlp8s0` should associate.
4. Verify the route:

```sh
ip route get 192.168.0.1
# Expected: 192.168.0.1 dev wlp8s0 src 192.168.0.X ...
```

5. Verify SSH:

```sh
ssh -o ConnectTimeout=5 root@192.168.0.1 'cat /app/FwVer'
# Expected: FwVer:4.0.0.32;date:2025.05.09;
```

## 5. Confirm the K-1 II is enumerated (cold boot, vendor mode)

The K-1 II needs to be in **vendor mode** for the Polaris to
recognize it as a Pentax camera. Vendor mode is enabled by:

1. K-1 II menu → "USB" → "PTP" (NOT "Mass Storage", NOT "Auto")
2. K-1 II menu → "Connection mode" → "PC" (not "Printer", not
   "MTP/PTP auto")
3. The K-1 II's USB vendor ID in vendor mode is `0x25fb`. The
   product ID is `0x0188` (NOT `0x0189` like the K-3 III).

Verify:

```sh
ssh root@192.168.0.1 'lsusb 2>&1'
# Expected output:
# Bus 001 Device 001: ID 1d6b:0002
# Bus 001 Device 002: ID 1a40:0101
# Bus 002 Device 001: ID 1d6b:0003
# Bus 001 Device 004: ID 25fb:0188          <-- K-1 II
# OR (if cold-boot didn't enumerate)
# (no 25fb entry at all)
```

If `25fb:0188` is present, the gimbal sees the K-1 II. Note
the bus/device number for the matrix.

If no `25fb` entry, the cold-boot USB enumeration missed the
K-1 II. Try the **warm plug** workaround (the patcher's
recovery path):

```sh
# Unplug the K-1 II USB cable from the gimbal side (camera side stays plugged)
ssh root@192.168.0.1 'lsusb 2>&1'   # confirm 25fb disappears
sleep 2
# Replug into the same gimbal USB port
sleep 5
ssh root@192.168.0.1 'lsusb 2>&1'   # confirm 25fb:0188 reappears
```

If the warm plug also fails, try the **runtime USB
re-enumeration** workaround:

```sh
ssh root@192.168.0.1 'echo 0 > /sys/bus/usb/devices/1-1/authorized 2>/dev/null; sleep 2; echo 1 > /sys/bus/usb/devices/1-1/authorized 2>/dev/null; sleep 5; lsusb 2>&1'
```

If still no `25fb`, STOP and file a new OpenPolaris issue
titled "K-1 II not enumerating on Polaris cold boot" with
the full `lsusb` output and a description of any LED state
on the K-1 II.

## 6. Run the K-1 II matrix (protocol-level probes only)

For each row in the K-1 II matrix, send the protocol probe
and record the result. Use the cli-probe tool at
`tools/cli-probe/build/install/cli-probe/bin/cli-probe` (or
build it if missing — see §8).

### 6.1 Detect (code 286)

```sh
ssh root@192.168.0.1 'echo "1&286&2&#" | nc -w 2 127.0.0.1 9090'
# Expected: 286@manufacturer:ricoh imaging company, ltd.;model:pentax k-1 mark ii;state:1;storage:N;photoFormat:N;#
# ACCEPTABLE: state:1, state:0 (powered but not ready)
# FAILURE: state:-2 (runtime broken — try restart_gphoto)
# FAILURE: timeout (no response at all)
```

If `state:1` or `state:0`: record the result in the matrix.
If `state:-2`: try `/app/restart_gphoto` (it will fail with
the `/app/bin/` empty bug, that's expected — but it logs to
`/app/Clog.txt` which is useful evidence). If timeout:
check `lsusb` again, the camera may have disconnected.

### 6.2 Push events (code 525)

Already pushed by the gimbal every ~30s. Capture 2 minutes
of pushes to confirm cadence:

```sh
ssh root@192.168.0.1 'tail -F /app/Clog.txt 2>&1' &
TAIL_PID=$!
sleep 120
kill $TAIL_PID
# Look for 525@Tempa<hex16>;# in the output, 4 of them
```

If 4+ pushes seen: PASS. If 0-1: FAIL.

### 6.3 Direct libgphoto2 (PC-attached) — separately

This is done from the **PC**, not the gimbal. Unplug the
K-1 II from the gimbal, plug into the PC's USB, run
gphoto2 on the PC with the libgphoto2 fork:

```sh
sudo apt list --installed 2>/dev/null | grep libgphoto2
# If libgphoto2 2.5.34 is installed (or built locally), use it
gphoto2 --auto-detect
gphoto2 --capture-preview
# Should produce a JPEG
```

Then plug the K-1 II back into the gimbal (re-run §5).

## 7. Record results in the K-1 II matrix

Open `LIVE-PROGRESS.md`. Find the "## K-3 III matrix"
section. AFTER it, add a new "## K-1 II matrix" section:

```markdown
## K-1 II matrix

| Area | Feature | Status | Evidence |
|---|---|---|---|
| Detection | USB enumerate (cold boot) | PASS/FAIL | <lsusb output, bus:dev> |
| Detection | USB enumerate (warm plug) | PASS/FAIL | <lsusb output, bus:dev> |
| Detection | 286 = K-1 II identify | PASS/FAIL | <raw response> |
| Push events | 525 cadence | PASS/FAIL | <N pushes in 2 min> |
| Direct PC | libgphoto2 detect | PASS/FAIL/NOT TESTED | <command + output> |
| Direct PC | libgphoto2 preview | PASS/FAIL/NOT TESTED | <JPEG file size> |
| Direct PC | libgphoto2 capture | PASS/FAIL/NOT TESTED | <JPEG file size> |
| Polaris runtime | Detect before restart | PASS/FAIL/NOT TESTED | <code 286 response> |
| Polaris runtime | Detect after pgphoto restart | NOT TESTED | blocked on patcher#39 (no /app/bin/pgphoto) |
| Live view | SET ON | NOT TESTED | blocked on patcher#36, #38 |
| Live view | GET state | NOT TESTED | blocked on patcher#36, #38 |
| Live view | First JPEG | NOT TESTED | blocked on patcher#36, #38 |
| Still capture | Normal capture (264) | NOT TESTED | blocked on patcher#37, #38 |
| Camera config | ISO/WB/EV/SHUTTER/FNUM SET | NOT TESTED | blocked on OpenPolaris#62 (Codes.kt is 11% correct) |
| Focus | focus adj (311) | NOT TESTED | blocked on OpenPolaris#62 |
| Format | image format/size | NOT TESTED | blocked on OpenPolaris#62 |
| Capture modes | bulb/video/delay/focus stack/HDR/astro/timelapse | NOT TESTED | blocked on OpenPolaris#62 |
| Storage/media | count/list/thumb/download | NOT TESTED | blocked on OpenPolaris#62, #63 |
| Recovery | USB disconnect/reconnect | NOT TESTED | requires warm plug (covered in §5 above) |
| Recovery | camera power-cycle | NOT TESTED | requires power-off + on (covered in §3-§4) |
| Recovery | pgphoto restart | FAIL | patcher#34, #38, #39 |
| Client | Benro Connect full qualification | NOT TESTED | needs Android emulator / device |
| Client | OpenPolaris full qualification | NOT TESTED | needs OpenPolaris app |
```

Fill in PASS/FAIL/NOT TESTED based on the §6 results.

Commit + push:

```sh
cd /home/ian/Documents/VSCodeProjects/OpenPolaris
git add LIVE-PROGRESS.md docs/evidence/2026-09-07/ 2>/dev/null
git -c user.name="<your git user.name>" \
    -c user.email="<your git user.email>" \
    commit -m "evidence(k1ii): K-1 II swap completed, matrix recorded"
git push origin main
```

## 8. Build the cli-probe (if it doesn't exist)

```sh
cd /home/ian/Documents/VSCodeProjects/OpenPolaris
ls tools/cli-probe/build/install/cli-probe/bin/cli-probe
# If present, skip
# If not present:
cd tools/cli-probe
./gradlew installDist
# The binary will be at build/install/cli-probe/bin/cli-probe
```

The cli-probe binary takes a code and optional payload:

```sh
tools/cli-probe/build/install/cli-probe/bin/cli-probe 286 "" 2 127.0.0.1 9090
# Sends: 1&286&2&#
# Prints the raw response
```

## 9. File a follow-up issue if anything unexpected

If the K-1 II behaves differently from the K-3 III:

- Different USB id: file OpenPolaris issue with `25fb:018X`
  in the title
- Different manufacturer/model string: file issue with the
  raw response
- Cold-boot enumeration fails repeatedly: file issue with
  the warm-plug workaround details

Cross-link to OpenPolaris#56 (the umbrella) and #48 (libgphoto2
K-1 II capture -6 reproduction).

## 10. Release hardware ownership

When done (matrix recorded, follow-up issues filed, or
STOPped for an unexpected behavior):

1. Open `LIVE-PROGRESS.md`.
2. Change the "Current hardware-test owner" line to:
   ```
   - **Current hardware-test owner:** **UNCLAIMED** — K-1 II swap and matrix recording complete; see [docs/evidence/2026-09-07/K1II-SWAP-PLAN.md](K1II-SWAP-PLAN.md) for full results
   ```
3. Add an activity log entry:
   ```
   - 2026-09-07 <HH:MM>: K-1 II SWAP COMPLETE. <agent name>
     ran K1II-SWAP-PLAN.md. <N> rows PASS, <M> rows FAIL,
     <K> rows NOT TESTED. See K-1 II matrix in LIVE-PROGRESS.md.
     <follow-up issues filed: list>
   ```
4. Commit + push:

```sh
cd /home/ian/Documents/VSCodeProjects/OpenPolaris
git add LIVE-PROGRESS.md docs/evidence/2026-09-07/
git -c user.name="<your git user.name>" \
    -c user.email="<your git user.email>" \
    commit -m "handoff: release K-1 II hardware ownership, <N> rows PASS, <M> FAIL, <K> NOT TESTED"
git push origin main
```

## 11. What to do if you get stuck

1. **Re-read this plan from §0.** Most "stuck" states are
   missed pre-conditions.
2. **Check `/app/Clog.txt` and `/app/Mlog.txt` on the gimbal:**
   ```sh
   ssh root@192.168.0.1 'tail -50 /app/Clog.txt'
   ssh root@192.168.0.1 'tail -50 /app/Mlog.txt'
   ```
3. **If the gimbal is unresponsive** (no SSH, no WiFi AP):
   power-cycle it (unplug 10s, replug, wait 60s, retry).
4. **If you find a NEW defect** (not in the matrix, not in
   the existing issues): file a new OpenPolaris issue with
   `bug` label, link from OpenPolaris#56, do not try to fix
   it during the swap.
5. **If you accidentally destroy files on the gimbal** (the
   user has explicitly said the SD card is expendable, the
   PC is NOT): STOP. Do not attempt recovery. Document what
   was destroyed in OpenPolaris#56.

## 12. Reference docs to read before starting

These are all in the OpenPolaris repo, paths relative to
`/home/ian/Documents/VSCodeProjects/OpenPolaris/`:

- [LIVE-PROGRESS.md](LIVE-PROGRESS.md) — the coordination ledger (670+ lines)
- [docs/PROTOCOL.md](docs/PROTOCOL.md) — protocol spec
- [docs/evidence/2026-09-07/protocol-mapping/PROTOCOL-MAP-2026-09-07.md](docs/evidence/2026-09-07/protocol-mapping/PROTOCOL-MAP-2026-09-07.md) — 65 verified codes
- [docs/evidence/2026-09-07/SESSION-AUDIT-2026-09-07.md](docs/evidence/2026-09-07/SESSION-AUDIT-2026-09-07.md) — full audit (H1-H4, M1-M2, L1)
- [docs/evidence/2026-09-07/issue-62-audit/](docs/evidence/2026-09-07/issue-62-audit/) — Codes.kt audit
- [docs/evidence/2026-09-07/patcher-38-fix-design/](docs/evidence/2026-09-07/patcher-38-fix-design/) — two-path fix
- [docs/evidence/2026-09-07/patcher-39-fix-design/](docs/evidence/2026-09-07/patcher-39-fix-design/) — /app/bin/ fix
- [docs/evidence/2026-09-07/H4-FIX-VERIFICATION-2026-09-07-1425.md](docs/evidence/2026-09-07/H4-FIX-VERIFICATION-2026-09-07-1425.md) — the H4 mitigation
- [docs/evidence/2026-09-07/agent-handoff/](docs/evidence/2026-09-07/agent-handoff/) — 3 hand-off docs (OpenPolaris, libgphoto2, patcher)

## 13. Issues to cross-link from any new issue

When filing new issues during this swap, link to:

- OpenPolaris#56 (umbrella) — always
- OpenPolaris#62 (camera command map) — if matrix row is
  blocked on the mapping being wrong
- OpenPolaris#63 (camera parity matrix) — for any camera
  feature that should work but doesn't
- patcher#36, #37, #38, #39 — for any runtime defect
- libgphoto2#48 — if K-1 II capture -6 reproduces

## 14. Definition of done

You are done when:

1. K-1 II matrix has a row for every category in the
   template above
2. Every row is PASS, FAIL (with evidence), NOT TESTED (with
   reason), or N/A (with evidence)
3. `LIVE-PROGRESS.md` activity log has the swap completion
   entry
4. `LIVE-PROGRESS.md` hardware-test owner is `UNCLAIMED`
5. All work is committed and pushed to `origin/main`
6. The 4 handoff docs from the previous session (issue-62-audit,
   patcher-38-fix-design, patcher-39-fix-design, K-3 III progress
   summary on #56) are still present and unchanged
7. (Optional but encouraged) A new OpenPolaris comment on
   #56 linking to any new issues filed

You are NOT done if:

- Any matrix row is `?` or `TBD`
- The K-1 II is left attached but the gimbal is unpowered
  (don't leave the gimbal in a partial state)
- The hardware-test owner field still shows your name
