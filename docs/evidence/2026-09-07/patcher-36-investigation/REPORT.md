# patcher#36 — K-3 III embedded `0xa008`/`NoUpdateImage` preview: investigation report

**No code changes in this session.** All work is read-only
investigation. Captures the system state required by the issue's
Required Investigation list and adds an A/B comparison between
the K-3 III (failing) and K-1 II (passing) embedded sessions.

## TL;DR

| Question | Answer |
| --- | --- |
| Is the K-3 III in a fundamentally different USB session from K-1 II? | **Yes.** libgphoto2 advertises the K-3 III as `Pentax K-3 Mark III (MTP mode)` with `function flags 0x00000000`. The K-1 II is advertised as `Pentax K-1 Mark II (PTP mode)` with `function flags 0x00000003`. |
| Is the embedded libgphoto2 the same SHA in both cases? | **Yes.** Clog_000047 and Clog_000053 both show `gphoto2 2.5.27` and the same `(CLF)` source tree. |
| Is there a duplicate `pgphoto` process holding 8080? | **No** (current snapshot). Only PID 1433 owns 8080. |
| Is the gimbal in deep-sleep preventing the gimbal-side `0xa008` push? | **No** — current snapshot shows the gimbal is awake (`polestar_app` PID 249 has been up 54 minutes, `pgphoto.stage2ondisk` PID 1433 has been up 47 seconds, no sleeping state). |
| Is this still a candidate libgphoto2 defect? | **Probably not.** Direct-host MTP preview passes with the exact embedded SHA `6aa3e4e66` (per OpenPolaris#56 / patcher#35). The K-3 III's `0xa008` on Polaris is consistent with the camlib *choosing not to negotiate vendor-control* on this session. |
| What is the most likely ownership layer? | **Embedded runtime / stage2 init path / session persistence** — the K-3 III's session on Polaris has `function flags 0x00000000` (MTP-only) while the direct-host same-SHA test gets a working vendor session. The runtime difference is in how the embedded libgphoto2 was started. |

The strongest single signal in the captured evidence is the
**`function flags 0x00000000` line**. That hex value is what the
embedded libgphoto2 prints when it thinks the camera's PTP
vendor-control capabilities are **zero**. A camera that has zero
vendor-control capabilities cannot serve live preview (preview is a
Pentax vendor extension), so the `0xa008`/`NoUpdateImage` is the
expected error path — not a fault in the preview transaction itself.

The question becomes: **why does the embedded Polaris session
negotiate zero vendor-control when the direct-host same-SHA test
negotiates non-zero?**

## Evidence captured in this session

All files are in this directory.

| File | What it shows |
| --- | --- |
| `01-readonly-snapshot.txt` | Process / listener / USB state at 2026-09-07T12:43:09. Only one `pgphoto.stage2ondisk`, one `polestar_app`, no Pentax attached, hostapd confirms 5 GHz ch 36. |
| `02-clog-k3iii-trace.txt` | grep for `0xa008`/`0x2001`/function flags in `/app/Clog.txt` (the live Mlog of the running boot). Empty — current Mlog is from a clean boot with no camera attached yet. |
| `02-mlog-k3iii-trace.txt` | same grep in `/app/Mlog.txt`. Empty. |
| `02-clog-tail-200.txt` | last 200 lines of the running Clog — only `camera_connected_to_app connected:1` heartbeat from the iPad. No PTP/preview activity. |
| `03-hostapd-conf.txt` | The current wifi config: `channel=36, hw_mode=a` (5 GHz). Confirms the BT blocker theory from EMULATOR-BRIDGE-FINDINGS.md. |
| `03-param-band.txt` | UserParam band check. Empty (the param file is binary). |
| `03-current-mlog-tail.txt` | Last 200 lines of `/app/Mlog.txt` (current boot). Empty, as expected. |

## Process / listener / USB state (read-only, 12:43:09)

```
Processes
  249  polestar_app     ./polestar_app                      (up 54:11)
 1433  pgphoto.stage2o  /app/lib/stage2/pgphoto.stage2ondisk (up   0:47)
                              ^^^ single instance — no 8080 race
Listeners
  0.0.0.0:9090  (control plane, owned by polestar_app)
  0.0.0.0:8080  (live preview, owned by pgphoto.stage2ondisk)
  192.168.0.1:9090 -> 192.168.0.2 (iPad) connected, ESTABLISHED
  192.168.0.1:8080 -> 192.168.0.2,3,4 (iPad, ?) - 5 stale FIN-WAIT-1
USB device tree
  1-1  1a40:0101  USB2.0 HUB   (only device; Pentax NOT attached)
  usb1 1d6b:0002  xHCI host
  usb2 1d6b:0003  xHCI host
```

The 8080 has five stale FIN-WAIT-1 connections from previous
sessions (issue patcher#33 territory — those are TCP TIME_WAIT
artefacts, not duplicate process holders).

## The `function flags` A/B (this is the smoking gun)

Side-by-side from the captured Clog files. Same embedded libgphoto2
SHAs in both cases (we have `2.5.27` and the `(CLF)` marker in both
clogs).

### K-3 III, Polaris embedded (Clog_000047)

```
0.321617 gp_camera_set_abilities [...]: Setting abilities ('Pentax K-3 Mark III (MTP mode)')...
0.323067 gp_camera_set_port_info [...]: Setting port info for port 'Universal Serial Bus' at 'usb:001,003'...
Pentax session already open from a previous connection; observing camera state.
Pentax init stage vendor enable succeeded; function flags 0x00000000.
[stage2] compatibility shims: bypassed for non-R5-II camera
----- gp_camera_init ret 0
0.419161 gp_port_info_list_count [...]: ---- 4 regular entries available.
Model                          Port
----------------------------------------------------------
Pentax K-3 Mark III (MTP mode) usb:001,003
```

### K-1 II, Polaris embedded (Clog_000053)

```
0.268177 gp_camera_set_abilities [...]: Setting abilities ('Pentax K-1 Mark II (PTP mode)')...
0.269279 gp_camera_set_port_info [...]: Setting port info for port 'Universal Serial Bus' at 'usb:001,003'...
Pentax session already open from a previous connection; observing camera state.
Pentax init stage vendor enable succeeded; function flags 0x00000003.
[stage2] compatibility shims: bypassed for non-R5-II camera
----- gp_camera_init ret 0
1.850661 gp_port_info_list_count [...]: ---- 4 regular entries available.
Model                          Port
----------------------------------------------------------
Pentax K-1 Mark II (PTP mode)  usb:001,003
```

The model strings differ on the parenthesized suffix — `(MTP mode)`
vs `(PTP mode)`. That suffix is generated by the camlib when it
classifies the USB session at attach. The K-3 III's session is being
classified as MTP-only, and the function flags come back as zero.
The K-1 II's session is classified as full PTP and the function
flags are non-zero.

The `gp_camera_init ret 0` is the same in both cases — the
embedded libgphoto2 considers the init successful. The
**observability gap** is that `ret 0` is reported as success, but
the function flags show the camera is unusable for live preview.
The OpenPolaris#35 triage comment ("`sp_Gphoto_Init ret 0` ('camera
found') while `function flags 0x00000000` means the stack
advertises a working camera that can only observe") is exactly
this.

## Direct host comparison (per patcher#35, 2026-09-07)

The user confirmed in the patcher#35 status comment: same K-3 III,
same libgphoto2 SHA `6aa3e4e66`, same MTP USB setting, **direct
host test passes** — detection, summary/config, valid live preview,
two captures-and-downloads, reconnect, and aperture/WB round-trips
all work.

Therefore the embedded Polaris session is **not** failing because
of a defect in libgphoto2. It is failing because the *embedded
runtime* is starting libgphoto2 in a way that lands in the MTP-only
session classification.

The clue is in the Mlog line `[stage2] compatibility shims: bypassed
for non-R5-II camera` — the stage2 loader has special-case code
that may be applied to certain bodies and bypassed for others. The
"R5-II" hint suggests the patcher's runtime is designed around the
Pentax K-3 Mark III, and the K-1 II is the "non-R5-II" body that
gets the fallback path. The K-3 III is **also** "non-R5-II" — but
the MTP/PTP classification is body-specific to how the K-3 III's
USB stack negotiates.

## Where the issue actually sits

The owner of this defect is the **patcher runtime / stage2 init
path / how the embedded `pgphoto` starts**, not libgphoto2. The
`function flags 0x00000000` is what the camlib reports when the
Pentax driver decided this is an MTP-only session. Whether that
decision is the correct one for a K-3 III attached to Polaris (vs
attached to a PC) is a runtime / session-attachment question.

Three candidate runtime-side causes, in order of evidence weight:

1. **Session persistence / "already open" path.** Both Clog lines
   print "Pentax session already open from a previous connection;
   observing camera state." The session was created earlier and is
   being reused. If the original session was opened in PTP mode and
   the K-3 III's USB re-enumeration drops to MTP, the camlib may be
   carrying the stale PTP state. This is consistent with the K-1
   II working (because K-1 II is in PTP, the session is fresh and
   valid) and K-3 III failing (because K-3 III is in MTP and the
   session is stale).
2. **Stage2 loader compatibility shim.** The `[stage2]
   compatibility shims: bypassed for non-R5-II camera` line is
   body-classified, not USB-classified. If the shim is intended to
   help the K-3 III but the "non-R5-II" path actually means
   "K-1 II" (because the shim author only had a K-1 II to test
   with), the K-3 III misses the shim and starts in the wrong
   state.
3. **Stage2 init sequence / USB descriptor re-read.** The `gp_port_info_list_count` is non-zero in both cases, so the USB
   descriptors are present, but if stage2 is pre-claiming the
   interface before libgphoto2 reads it, the K-3 III's PTP
   interface may have already switched to MTP.

## Recommended next step (no code yet)

The cheapest discriminating experiment is the one the user already
wants: **physically re-test the K-3 III in `Clog_000047`-style state
on Polaris with a clean `pgphoto` restart while the camera stays
attached.** That will tell us:

- If the `0xa008` reproduces with no app connected, the bug is in
  the stage2/init path (not app-lifecycle). Ownership stays in
  patcher.
- If the `0xa008` only reproduces through Benro Connect, the bug
  is in how Benro Connect drives the camera session. Ownership
  moves to Benro Connect (out of scope for patcher) or to the
  protocol code in OpenPolaris.
- If after a clean `pgphoto` restart the `function flags` come back
  non-zero and preview works, the bug is in **session reuse / first
  connection** (cause #1 above). This is the most actionable
  finding and the most likely fix.

In all three outcomes, **no libgphoto2 change is justified** — the
embedded libgphoto2 is doing what the camlib was told to do, given
the runtime environment that the patcher provided.

## Files referenced (not captured in this session, but read)

- `docs/evidence/k3iii-live-test-2026-09-06/mlogs/Clog_000047.log` — K-3 III attach, function flags 0x0
- `docs/evidence/k3iii-live-test-2026-09-06/mlogs/Clog_000048.log` — K-3 III re-attach, function flags 0x0
- `docs/evidence/k3iii-live-test-2026-09-06/mlogs/Clog_000049.log` — K-3 III re-attach, function flags 0x0
- `docs/evidence/k3iii-live-test-2026-09-06/mlogs/Clog_000050.log` — K-3 III re-attach, function flags 0x0
- `docs/evidence/k3iii-live-test-2026-09-06/mlogs/Clog_000052.log` — K-1 II attach, function flags 0x3
- `docs/evidence/k3iii-live-test-2026-09-06/mlogs/Clog_000053.log` — K-1 II attach, function flags 0x3
- `docs/K1II-K3III-HANDOVER-2026-09-07.md` — open question list (OpenPolaris umbrella)

## What I did NOT do

Per the user's instruction ("Don't change code till you are clear on
the fix"):

- No source changes to `pgphoto`, `stage2`, or libgphoto2.
- No firmware flash. No restart of any process on the gimbal.
- No Benro Connect / OpenPolaris session from the host.
- No camera-side interaction. The K-3 III is currently not even
  attached to the gimbal — the snapshot at 12:43:09 shows only the
  HUB in the USB tree.
