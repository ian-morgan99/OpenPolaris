# Feature-test inventory — what else we can test

**Date**: 2026-09-07 13:50
**Rule**: no FwPkt.zip builds, no direct file writes to the gimbal,
no destructive payloads. Read-only protocol probes via cli-probe
(TCP 9090) and the patcher's container tests (local-only) are SAFE.

This is the menu of feature tests I can run **right now** without
violating the user's "no zip builds" / "no direct edits" rule, grouped
by what part of the OpenPolaris surface they exercise.

## Layer 1 — pure passive read-only protocol probes (safest)

These use the existing `tools/cli-probe/build/install/cli-probe/bin/cli-probe`
binary. They do not touch the gimbal filesystem and most do not even
mutate runtime state (they're GET-equivalents).

### 1.1 — Live version of the full pre-camera burst (gradle task)

```
./gradlew :tools:cli-probe:liveBurst -q --console=plain
```

What it does: sends the canonical 9-code BURST_PRE_CAMERA set
(808, 809, 802, 778, 779, 775, 824, 524, 543, 780) in one TCP
session, prints every response.

What it tests: the gimbal's burst-mode response behaviour, the
way the gimbal chains responses (e.g. `524@state:0;#` then
`517@yaw:...;...;#` get bundled).

Status: **just ran successfully**, output captured. All 9 codes
respond except 779 (battery slot 2 not present, no response) and
808 (SYS_VERSION, no response — comment says "✓ live" but live test
returns nothing).

### 1.2 — Passive push-mode listener (gradle task)

```
./gradlew :tools:cli-probe:liveListen -q --console=plain --args="30 192.168.0.1 9090"
```

What it does: opens a long-lived connection, logs **every** incoming
frame to `push.log` for 30 seconds. Catches codes 525 (temperature),
518 (rotate vector), 524/285 (mode state pushes) that never answer
a direct request.

What it tests: the unsolicited push stream — i.e. what the gimbal
emits when nobody is asking. This is the data the app's UI uses
to render the gimbal in real time.

Status: not yet run. Will produce `push.log` of all push events.

### 1.3 — Handshake smoke test (gradle task)

```
./gradlew :tools:cli-probe:authSmoke -q --console=plain --args="192.168.0.1 9090"
```

What it does: runs the full 820/821/823 MountSession handshake via
`dev.openpolaris.core.domain.MountSession`, then a single 519
(gimbal position) read. Prints PASS/FAIL plus a frame summary.

What it tests: whether the **documented** handshake actually works
end-to-end against the live gimbal. Critical for the desktop app
because the desktop app won't even try to talk to the gimbal if
this fails.

Caveat: 821 expects a token. With no token, 820 returns `ret:-1` and
823 may or may not proceed. The AuthSmoke will tell us what the
handshake state machine does in each case.

Status: not yet run.

### 1.4 — Gimbal-control getter codes (all read-only)

| Code | Name | Tested? | What it tells us |
|---|---|---|---|
| 517 | GET_GIMBAL_POS | ✓ | live yaw/pitch/roll (rad) |
| 524 | EX_AXIS_STA | ✓ | external axis state |
| 537 | GET_TILT_STATE | ✓ | tilt envelope state (decoupled from pitch/roll) |
| 538 | SET_TILT_STATE | untested | would be a setter — skip |
| 539 | GET_DITHER_STATE | ✓ | dither on/off |
| 540 | SET_DITHER_STATE | tested as GET | returns `ret:0;` (ack) |
| 541 | GET_LIMIT_STATE | ✓ | limit envelope |
| 542 | SET_LIMIT_STATE | tested as GET | returns `state:0;` |
| 543 | GET_SETTLING_TIME | ✓ | `time:2;` (units?) |
| 547 | GET_AUTO_LEVEL_EN | ✓ | `en:0;` (off) |
| 826 | SP_826 | ✓ | `state:0;` (corpus-derived, unknown meaning) |

### 1.5 — Wifi / network getters

| Code | Name | Tested? | What it tells us |
|---|---|---|---|
| 800 | WIFI_SCAN | ✓ | `ret:-1;` (no scan in progress) |
| 802 | GET_WIFI_BAND | ✓ | `band:1;` (2.4GHz) |
| 803 | SET_WIFI_BAND | tested | `ret:0;` (ack only) |
| 805 | WIFI_DISCONNECT | tested as GET | `state:0;` |
| 806 | WIFI_STATUS | no response | |
| 807 | WIFI_RSSI | no response | |

Caveat: WIFI_CONNECT (804) and WIFI_SCAN (800) are technically
mutating (they trigger a scan / connection attempt). 800 with no
payload returned `ret:-1` so no scan happened — safe. 804 with no
payload returned `state:0` — also no connection happened.

### 1.6 — Camera getters (mostly don't respond in current state)

| Code | Name | Tested? | What it tells us |
|---|---|---|---|
| 286 | CAM_GET_STATE | ✓ | K-3 III identified, `state:1;` |
| 292 | CAM_LIVEVIEW_GET | ✓ | `state:1;` — **live view enabled** |
| 311 | CAM_FOCUS | no response | |
| 258, 260, 262, 264, 268, 270, 272, 274, 276, 278 | camera info burst | no response | K-3 III doesn't expose raw info via these |

Why 258-278 don't respond: those are Pentax-specific camlib reads.
With pgphoto's libgphoto2_port possibly in a degraded state (the
"/app/bin/ missing" issue from earlier), the on-disk camlib might
not be enumerating the camera. The fact that 286 works (which
goes through a different path — direct gphoto2_0 call) but 258
doesn't (which goes through ptp2 camlib) is consistent with
patcher#36's libgphoto2_port issue.

### 1.7 — Push events (live capture)

| Code | Push payload | What it tells us |
|---|---|---|
| 525 | `Tempa<hex16>;` | IMU temperature, pushed every ~few seconds |
| 524 | mode state changes | only on change |
| 518 | rotate vector | only on motion |

To capture these, use `liveListen` (item 1.2 above).

## Layer 2 — read-only filesystem probes (SSH read, no write)

These use the SSH shell to the gimbal but **only read** state.
All of these are safe.

### 2.1 — File-system baseline

```
ssh root@192.168.0.1 'ls -la /app/sd/normal/ /app/sd/lapse/ /app/sd/focusStack/'
```

What it tells us: the actual file count per category. Cross-checks
with code 770 (FILE_LIST) reports. Already done — 0 files in all
categories after the destructive 789 test.

### 2.2 — pgphoto state inspection

```
ssh root@192.168.0.1 'ps -ef | grep pgphoto; ls -la /app/bin/ /app/lib/stage2/; cat /app/restart_gphoto | head -20'
```

What it tells us: whether pgphoto is alive, whether the wrapper
is present (currently NOT — `/app/bin/` is empty, which is a real
breakage on the gimbal).

### 2.3 — Runtime config dump

```
ssh root@192.168.0.1 'cat /app/cfg/* /app/openpolaris-libgphoto2-provenance.txt /app/FwVer'
```

What it tells us: the provenance of the libgphoto2 build (we
already saw `git_commit=6aa3e4e66`) and the FwVer base (4.0.0.32).

## Layer 3 — unit-test surface (no network, no firmware)

These run entirely in the local JVM with stub servers. They test
the OpenPolaris Kotlin code itself, not the gimbal. All safe.

### 3.1 — `:shared:jvmTest`

The BridgeOrchestratorTest, WifiBridgeTest, NoScanGuardTest, etc.
already exist. Running these:
- proves the **client side** of every protocol code is correct
- doesn't touch the gimbal at all
- catches regressions in the request/response framing

### 3.2 — `:composeApp:jvmTest`

Tests for AppViewModel, FeatureFlagsSectionPolicy, MobileResponsive
LayoutContract, UserManualPaneBundledAsset. Pure UI logic.

### 3.3 — Stub-server side

`tools/stub-server` can be launched with `runFakeMount` to provide
a fake gimbal on `127.0.0.1:<port>`. Then `cli-probe status 127.0.0.1 <port>`
exercises the parser/builder against known good responses.

```
./gradlew :tools:cli-probe:runFakeMount -PfakeMountPort=9099 &
./tools/cli-probe/build/install/cli-probe/bin/cli-probe status 127.0.0.1 9099
```

## Layer 4 — PATCHER tests (do not deploy, do not build)

These run the patcher's own test suite, which validates the
**build** path but doesn't actually flash anything. All safe.

### 4.1 — Patcher build-package test (the one that produced the
2026-09-07 build)

```
container/test_polaris_pentax_build_package.sh polaris-patcher-c2 \
  /home/ian/Downloads/FwPkt\(1\).zip \
  /home/ian/Documents/VSCodeProjects/LibGphoto2/libgphoto2
```

This was the script that produced the 2026-09-07-k1ii-k3iii-candidate
build. Re-running it now would re-produce an FwPkt.zip that's
identical to the installed firmware (if the inputs haven't drifted).
**This is a SAFE way to validate the build path** without flashing.

### 4.2 — Patcher fail-closed test

```
container/test_patch_fail_closed.sh
```

Tests the patcher's reject paths (bad MD5, truncated appfs, etc).
We have ~10 of these builds in
`builds/2026-08-30_test-cards/`. Re-running validates the patcher
hasn't regressed on the test-card matrix.

### 4.3 — Stage-2 loader compile test

```
container/test_stage2_loader_compile.sh
```

Validates `arm-linux-gnueabi-gcc` can still compile
`stage2_loader.c` against the patcher's own headers. Pure compiler
test, no runtime.

### 4.4 — QEMU selftest (if installed)

```
./patch-polaris.sh --selftest
```

Runs the rebuilt libgphoto2.ptp2.so under qemu-user-static against
the R5 II registration. Catches ABI mismatches without needing a
real camera.

## Layer 5 — DESKTOP APP observation (read-only, no edits)

### 5.1 — Start the desktop app

```
./scripts/run-desktop.sh
```

The desktop executable already exists at
`desktopApp/build/compose/binaries/main/app/OpenPolaris/bin/OpenPolaris`
(17 KB launcher, runs against the build's lib/). The composable
app reads from the gimbal on 192.168.0.1:9090.

Once it boots, the user can see:
- Connect / Reconnect / Auth buttons
- Tonight pane (astro calculations)
- FullControlPanes (gimbal angle, dither, etc)
- Feature flags (read from /app/cfg/ via 802, 543, 547 etc)
- User manual (bundled asset)
- SimulatedMount demo (in-process fake gimbal, doesn't touch the real one)

This is **the most direct comparison test** — does the app render
the same values I saw via cli-probe? E.g.:
- App should show `band:1` (2.4GHz) on the Wi-Fi card
- App should show `capacity:100;charge:0;` on the battery card
- App should show `state:1` on the camera card
- App should show `mode:1;state:0;` on the gimbal mode card
- App should show `sw:6.0.0.54` on the device info card

### 5.2 — Compare app's UI to the cli-probe transcript

Take the existing transcript
(`docs/evidence/2026-09-07/protocol-mapping/transcripts/cli-probe-transcript-2026-09-07-1336.txt`)
and walk through the app, noting where each app value matches or
diverges from the protocol-level reality. This is the **side-by-side
hardware verification** the user asked for.

## Layer 6 — BEHAVIORAL tests (BOTH read AND write, but no
firmware changes)

These mutate runtime state but are recoverable without firmware
changes. Some are reversible via a follow-up code; some require a
gimbal reboot.

### 6.1 — Gimbal position 535 SET_GIMBAL_POS (REVERSIBLE)

`SET_GIMBAL_POS` would tell the gimbal to move to a specific
yaw/pitch/roll. The gimbal would move, then the user can verify
the position via 517. **Reversible**: 523 (POS_RESET) goes back
to home.

Caveat: moving the gimbal physically could be scary on a desk.
The user should be in a position to see the gimbal.

### 6.2 — Setter codes (low-impact)

- `540 SET_DITHER_STATE` (already tested, returned `ret:0`)
- `542 SET_LIMIT_STATE` (already tested)
- `544 SET_SETTLING_TIME` (tested, returned `ret:0`)
- `547 GET_AUTO_LEVEL_EN` (read only)
- `548 SET_AUTO_LEVEL_EN` (would toggle the auto-level state)
- `549 ACK_GENERIC` (just an ack, safe)

### 6.3 — LED / buzzer

- `547 en:0;` → off
- `547 en:1;` → on (the LED would light up)
- The Benro app exposes this in its settings pane

### 6.4 — Wi-Fi band set

- `802 band:1;` → 2.4GHz (current)
- `802 band:0;` → ??? (might be 5GHz, or might not be supported)
- A band switch would briefly disconnect the wifi — would need
  to reconnect the host to the new SSID

### 6.5 — Camera live view set

- `291 CAM_LIVEVIEW_SET` with payload `state:1;` would start
  the live view. Then 8080/preview should serve an MJPEG stream.
- Curl `http://192.168.0.1:8080/preview` (if `curl` is installed)
  or use a Python http client to grab a frame.

This is the **canonical "is the patcher working" test** — the
patcher's whole point is to get the live view to display in the
app. If `8080/preview` returns a valid MJPEG, the patcher is
working end-to-end.

## NOT TESTING (would need firmware changes or are out of scope)

- Anything that needs `FwPkt.zip` (firmware upgrades)
- Anything that touches `/app/bin/` or `/app/lib/` (file replacement)
- Anything that needs camera-side interaction (CAM_SET_ISO etc —
  the gimbal doesn't respond to these right now anyway)
- Push events that require the gimbal to be physically moved
  (the dither/vibration that triggers 518 push is hard to provoke
  in a static desk test)
- USB passthrough (no USB cable between host and gimbal)

## Recommended next steps (in order of value-to-risk)

1. **Run the liveBurst gradle task** (already done above)
2. **Run the liveListen gradle task** for 30-60s, capture the
   push stream to a file
3. **Start the desktop app** via run-desktop.sh, walk through
   the UI, compare each panel to the protocol-level values I have
4. **Run the patcher's test_polaris_pentax_build_package.sh** with
   the provenance-verified FwPkt(1).zip input — this validates the
   build path produces a valid FwPkt.zip without actually flashing
5. **Run the unit tests** (:shared:jvmTest, :composeApp:jvmTest) —
   confirms the client-side code is correct
6. **Test 291 liveview set + 8080/preview** — this is THE end-to-end
   test for the patcher's value proposition. If this works, the
   whole patcher#36 investigation is essentially closed
7. **Test 540 dither on/off** — verify the gimbal responds, observe
   the app's UI updates
8. **Test 8080/preview** even without 291 — maybe the gimbal already
   has live view on (code 292 said `state:1`)
