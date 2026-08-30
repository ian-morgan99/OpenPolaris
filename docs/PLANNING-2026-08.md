# OpenPolaris — Current Plans & Findings Snapshot

**Date:** 2026-08-30
**Status:** Gateway open, burst implemented in code, hardware smoke still pending.
**Audience:** pick this up tomorrow and know exactly what to do next.

> **2026-08 updates:**
> 1. The `AppViewModel.kt:127.0.0.1` reference is **dead code** — that file
>    was at `shared/.../core/AppViewModel.kt`; it has since been moved
>    to `composeApp/.../ui/AppViewModel.kt`, where it now defaults to
>    `192.168.0.1` (line 38). The TCP connection itself was migrated
>    to
>    [`MountSession.kt:21`](shared/src/commonMain/kotlin/dev/openpolaris/core/domain/MountSession.kt)
>    (`host = "192.168.0.1"`, port `9090`), and
>    [`Burst.kt:13`](tools/cli-probe/src/main/kotlin/dev/openpolaris/probe/Burst.kt)
>    was also flipped to `192.168.0.1`.
> 2. The post-connect sequence naming `515` as `SET_SYSTEM_TIME` (line 173,
>    line 241) contradicts the RE table in
>    [PROTOCOL.md §Control plane](PROTOCOL.md) and `shared/.../Codes.kt:43`,
>    both of which say `515 = SP_GIMBAL_HADJ_ANGLE`. **The post-connect
>    "time-set" code is currently unidentified.** When the live burst
>    probe (Step 3) runs, capture the exact code the firmware expects;
>    until then, treat 515 in any burst command as a placeholder.
> 3. **2026-08-30 (today):** Step 5 implemented in code. The post-connect
>    burst is now wired into [`AppViewModel.kt`](composeApp/src/commonMain/kotlin/dev/openpolaris/ui/AppViewModel.kt)
>    and runs in this order after `MountSession.connect()` returns true:
>    `808` (firmware version) → `809` (serial number) → `802` (WiFi band) →
>    `778` (battery base) → `779` (battery detail) → `775` (SD status) →
>    `824` (OMS run state) → `524` (ex-axis state) → `543` (settling-time get).
>    Each call is wrapped in `runCatching` so a non-matching parser
>    doesn't abort the burst. `544` (settling-time SET) is *not* in the
>    burst — it's only fired by `setSettlingTimeMs(ms)`. See
>    "What changed today" below.

This is the **actionable** view. The deep RE reference is
[polaris-re-results.md](</home/ian/.copilot/session-state/57abdabb-a2e1-4a3e-a7c1-77b48d31c65a/files/polaris-re-results.md>);
the high-level product roadmap is [PLAN.md](PLAN.md). Read those for
context. This document is what we have on the board **right now**, what's
in the way, and what each next step costs.

---

## TL;DR — three things to know

1. **We are blocked on a Linux desktop problem, not the gimbal.**
   `gnome-control-center wifi` polls every few seconds and triggers a
   polkit password prompt each time. The user's SSID `polaris_d13e86`
   is saved and the gimbal is reachable, but every panel re-open
   restarts the auth-flood. **Fix is one file in `/etc/polkit-1/rules.d/`
   + `systemctl restart polkit`.** Rule is ready in the session files
   directory; the user has to `sudo cp` it.

2. **The TCP wire format is now fully known and already implemented in
   the codebase.** `CommandBuilder` produces `1&<spCode>&<cmdType>&<payload>#`
   exactly like the Benro app, and `ResponseParser` consumes
   `<spCode>@<value>#`. The first 7 burst-set codes (524, 544, 802,
   824, 775, 778, 779) round-trip against the local stub server;
   75 `shared:jvmTest` tests pass.

3. **The Bluetooth→WiFi handoff is now fully understood from RE.**
   The gimbal's BLE adapter is a simple wake switch: connecting to
   the GATT service for ~100 ms is enough to make it bring up its
   WiFi AP. The original app does **no message writes** during wake —
   just connect, observe `state=2`, disconnect. After that, TCP to
   `192.168.0.1:9090` is all that is needed.

### What changed today (2026-08-30)

Picking up tomorrow? Read this first.

- **Polkit rule ready, one sudo away from killing the auth-flood.**
  Run `/home/ian/Documents/VSCodeProjects/OpenPolaris/scripts/install-wifi-polkit-rule.sh`
  (asks for your password once, ~30 s). Until this is installed, every
  `nmcli radio wifi on` from a GUI panel will trigger a polkit auth
  request and slow the laptop.
- **Post-connect burst now wired into the desktop app.** Open the app,
  connect, and the moment the TCP socket is up, the app fires
  `808 → 809 → 802 → 778 → 779 → 775 → 824 → 524 → 543` (in that order).
  The parsed results populate 8 new observable state fields in
  [`AppViewModel`](composeApp/src/commonMain/kotlin/dev/openpolaris/ui/AppViewModel.kt):
  `firmwareVersion`, `serialNumber`, `wifiBand`, `batteryDetail`,
  `sdStatus`, `omsState`, `settlingTime`, `exAxisState`. Each call is
  individually `runCatching`'d so a parser miss on one code doesn't
  kill the rest of the burst.
- **Time-set code is *not* in the burst.** The earlier draft of this
  doc named `515` as the time-set code, but RE says `515 = HADJ_ANGLE`.
  We skipped the time-set entirely — `543` (settling-time GET) is the
  only "set/get" pair in the burst, and `544` is the matching setter
  fired by `setSettlingTimeMs(ms)`. If the live smoke (next bullet)
  shows a separate time-sync is required, we can add it without
  touching the burst structure.
- **Next concrete thing to do is the live smoke.** With the polkit
  rule installed, gimbal powered, and the desktop app pointed at
  `192.168.0.1`, the burst will fire automatically on connect. We
  need to capture one successful round-trip and paste the responses
  back here so we can tune the four `*Detail.fromFrame` parsers to
  the actual wire format. (40 unit tests pass against the stub
  server; live firmware is the only thing left to validate against.)
- **Build is green.** `JAVA_HOME=/home/ian/jdks/jdk-21.0.2 ./gradlew
  :shared:jvmTest :composeApp:compileKotlinJvm` → `BUILD SUCCESSFUL`,
  40 tests pass. (Note: `/usr/lib/jvm/java-21-openjdk-amd64` is a JRE
  without `javac` — always set `JAVA_HOME` to a real JDK on this box.)

---

## Where we are

### Code (in the OpenPolaris repo)

| Area | State | Evidence |
|---|---|---|
| Wire format | ✅ implemented | [CommandBuilder.kt](shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/CommandBuilder.kt), 75 jvmTest pass |
| Code constants | ✅ implemented | [Codes.kt](shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/Codes.kt), 190 lines, camera 258-311 marked UNVERIFIED |
| Response parser | ✅ implemented | [ResponseParser.kt](shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/ResponseParser.kt) splits `<spCode>@<value>#` |
| Burst codes 524/544/802/824/775/778/779 | ✅ round-trip green | 75 jvmTest pass; 7 burst probes against stub server |
| Stub server (mobile-app → PC) | ✅ built | `tools/stub-server` runs the simulator as a TCP service |
| `MountSession` host (was `AppViewModel.connect()`) | ✅ defaults to `192.168.0.1:9090` | [MountSession.kt:21](shared/src/commonMain/kotlin/dev/openpolaris/core/domain/MountSession.kt); `Burst.kt:13` also defaults to `192.168.0.1` |
| Post-connect burst (524/TBD-time-set/778/775/284/802/824) | ✅ implemented | [`AppViewModel.postConnectBurst()`](composeApp/src/commonMain/kotlin/dev/openpolaris/ui/AppViewModel.kt) runs 808/809/802/778/779/775/824/524/543 after `MountSession.connect()`. Time-set was **dropped** in favor of `543` (settling-time get); `544` is the setter. |
| Camera info burst (CAM_GET_* = 258/260/262/264/266/268/270/272/274/276/278) | ❌ not implemented | fired by `getCanmeraInfo()` in original app; code list already pinned from RE §5. Next step: add to `AppViewModel.postConnectBurst()` after live smoke proves the basic 9-code burst. |
| BT-side codes (1-5, 257-263, 513-524) | ❌ not in repo | likely a new `BtCodes.kt` + frame helpers |
| Linux BlueZ BLE wake pulse | ❌ not implemented | optional, see "Bluetooth" below |
| `ResponseParser` handling of literal `h` pulse ack | ⚠️ not visible | needs review |

### RE findings (already captured, just listed here for orientation)

- **Gimbal AP is OPEN WiFi** (no PSK). Saved NM profile `polaris_d13e86`
  has no `key-mgmt`/`psk` keys. Confirmed by `PhoneConnectUtils.createWifiConfig`.
- **TCP target** is `192.168.0.1:9090`. HTTP `http://192.168.0.1/`. Live
  stream `http://192.168.0.1:8080/?action=stream`.
- **SEND wire format:** `1&<spCode>&<cmdType>&<value>#` where `cmdType`
  semantics are roughly: `1`=write-only, `2`=set/query (default for the
  app), `3`=control-plane (gimbal motion 513-549), `4`=special (282/286).
- **RECV wire format:** `<spCode>@<value>#` plus literal `"h"` for
  heartbeat acks (used by OkSocket PulseManager).
- **BT frame format:** `code:<N>;#<key:val;…>#` (note: `#<…>#` envelope).
- **GATT UUIDs:** service `0000fff0-…`, TX `0000fff1-…`, RX `0000fff2-…`.
  MTU requested: 512.
- **BT scan name filter:** `polaris_`, `theta_`.
- **BT→WiFi handoff is just a connect/disconnect pulse.** No message
  writes. Original app: `connectGatt` → wait for `state=2` → `close()`.
- **SP_TEST=526** is used for 8 diagnostic sub-steps
  (cable/HDMI/4G/AT/URAT/sleep/reset/battery-warn) by the app's test
  flows. It is **not** part of the connect handshake.
- **Post-connect burst** (from `setWifiConnectState(true)`):
  524 → (time-set, code **TBD** — not confirmed in RE) → 778 poll → 775 poll → 284 → 802 → 824.
- **Camera info burst** (from `getCanmeraInfo()`): fires the
  `CAM_GET_*` set — 258, 260, 262, 264, 266, 268, 270, 272, 274, 276,
  278 (ISO / WB / FNum / EV / State / Focus / ImgSize / ImgFmt /
  Color / Shutter / CaptureMode). **Earlier draft listed 265/266/
  267/268/275 — incorrect.**

Full table of every code the app dispatches is in
[polaris-re-results.md §5](</home/ian/.copilot/session-state/57abdabb-a2e1-4a3e-a7c1-77b48d31c65a/files/polaris-re-results.md>)
(sections 5-8 of that file).

### Environment

- `polaris_d13e86` SSID is **saved** (DHCP, autoconnect, never-default).
- NetworkManager profile has **no PSK** (OPEN). Correct.
- WiFi radio is currently **off** because `gnome-control-center wifi`
  panel triggered an auth flood.
- `/etc/polkit-1/rules.d/` is mode `rwxr-x---` `root:polkitd`. Per-user
  polkit rules in `~/.config/polkit-1/rules.d/` are **not read by
  polkitd on Debian** — only `/etc/polkit-1/rules.d/` matters.
- `polkit-1` package is installed. `polkitd` is running.
- A polkit rule is ready in the session files dir; the user just
  needs to `sudo cp` it (one sudo prompt with their password).

---

## Blockers and the order to clear them

### Blocker 1 — auth-flood (gate to everything else)

Without this fixed, every attempt to bring up WiFi from the GUI
generates a stream of polkit prompts. CLI commands work because they
go through `polkit.Result.YES` paths (no admin), but opening the
gnome-control-center wifi panel for any reason restarts the loop.

**The fix (one-time, takes ~30 s):**

A one-shot installer script is checked in at
[`scripts/install-wifi-polkit-rule.sh`](../scripts/install-wifi-polkit-rule.sh):

```bash
/home/ian/Documents/VSCodeProjects/OpenPolaris/scripts/install-wifi-polkit-rule.sh
```

The script copies the rule, sets ownership/perms, restarts polkit, and
verifies the new `pkaction` implicit-active values. It will prompt
**once** for the sudo password.

The same result can be achieved by hand:

```bash
sudo cp /home/ian/.copilot/session-state/57abdabb-a2e1-4a3e-a7c1-77b48d31c65a/files/50-openpolaris-wifi-scan.rules \
        /etc/polkit-1/rules.d/50-openpolaris-wifi-scan.rules
sudo chown root:root /etc/polkit-1/rules.d/50-openpolaris-wifi-scan.rules
sudo chmod 0644 /etc/polkit-1/rules.d/50-openpolaris-wifi-scan.rules
sudo systemctl restart polkit
```

After that, `gnome-control-center wifi` may be opened freely; no
prompts. The rule is JS-format, must be owned by root, and must be
in `/etc/polkit-1/rules.d/` (not `~/.config/polkit-1/`).

**Verify:**

```bash
nmcli radio wifi on
nmcli connection up polaris_d13e86
ping 192.168.0.1
```

**Owner:** user (one sudo).

### Blocker 2 — gimbal not powered (assumed)

The user reported earlier that the gimbal was charging / on. We
have not yet confirmed it's currently powered and broadcasting
`polaris_d13e86`. If the SSID is missing after Blocker 1 is fixed,
the gimbal needs power before the test can continue.

**Verify:**

```bash
nmcli device wifi list
```

Look for an entry with SSID starting `polaris_`.

**Owner:** user.

### Once both blockers are clear

Three things become unblocked:

1. We can issue a **live TCP burst probe** from the CLI:
   ```bash
   # connect to gimbal AP (no PSK), then
   for c in 524 515 778 775 284 802 824; do
     printf '1&%s&2&-100#\n' "$c" | nc -w1 192.168.0.1 9090
   done
   ```
   **Note:** `515` is the stand-in the planning doc uses for the
   "time-set" step. The RE table in [PROTOCOL.md](PROTOCOL.md) says
   `515 = SP_GIMBAL_HADJ_ANGLE`. Treat the time-set output as
   *unspecified code* and capture whatever the firmware echoes — that
   becomes the canonical time-set code. This will tell us whether the
   firmware's wire format is exactly what we inferred from RE (very
   likely yes), and gives us real responses to validate
   `ResponseParser` against.

2. ~~We can flip `AppViewModel.connect()` to `host=192.168.0.1`.~~
   **Done** — `MountSession.connect()` and `Burst.kt` already default
   to `192.168.0.1:9090`. Only the *live verification* remains.

3. We can run the full Android jvmTest burst set against the live
   gimbal instead of the stub.

---

## Next steps in priority order

Each step lists **what**, **how long** (rough), **how to verify**, and
**what it unblocks**.

### Step 1 — Install the polkit rule

- **What:** run the four `sudo` lines in Blocker 1.
- **Who:** user.
- **Cost:** ~30 s, one sudo prompt.
- **Verifies:** `nmcli radio wifi on` no longer prompts; can open
  `gnome-control-center wifi` freely.
- **Unblocks:** live testing.

### Step 2 — Confirm gimbal is online and reachable

- **What:** `nmcli device wifi list` (after Step 1). Confirm
  `polaris_…` SSID is visible and connect. Then `ping 192.168.0.1`.
- **Who:** user.
- **Cost:** ~1 min.
- **Unblocks:** Step 3 (live burst probe).

### Step 3 — Live TCP burst probe

- **What:** run the 7-code burst against `192.168.0.1:9090` via `nc`
  and capture the responses to `/tmp/live-burst-001.txt`.
- **Why:** the single most valuable experiment we can do. Confirms
  our wire format is right against real firmware and gives us real
  field formats to test the parser against.
- **Cost:** ~10 min including analysis.
- **Expected output:** seven frames of `<spCode>@<value>#`, possibly
  with key/value pairs, possibly with keyframes for the polled ones
  (778, 775). If any frame is empty or error-shaped, document the
  exact text.
- **Unblocks:** Step 5/6 implementation, G1 sign-off. May surface
  firmware quirks (extra fields, unexpected ordering, push frames
  from the gimbal side, the actual time-set code, the actual
  `getCanmeraInfo()` dispatch list).

### ~~Step 4 — Flip `AppViewModel.connect()` to `192.168.0.1`~~ (done in code)

- **What was needed:** change `host = "127.0.0.1"` to `host = "192.168.0.1"`,
  port stays 9090.
- **What happened:** `AppViewModel.kt` no longer exists in the repo. The
  connection was migrated to
  [MountSession.kt](shared/src/commonMain/kotlin/dev/openpolaris/core/domain/MountSession.kt:21),
  which **already defaults to `host = "192.168.0.1"`, port `9090`**.
  `tools/cli-probe/.../Burst.kt:13` was the only remaining `127.0.0.1`
  default; also flipped to `192.168.0.1`. Re-ran `:tools:cli-probe:installDist`
  and re-verified both `cli-probe` and `BurstKt` round-trip clean against
  the local stub.
- **Remaining:** live verification against a real gimbal — covered by
  Step 3 (live TCP burst probe) and the G1 sign-off.
- **Unblocks:** running the app against a real gimbal from the IDE.

### Step 5 — Implement and wire the post-connect burst (DONE 2026-08-30)

- **What was done:** in [`AppViewModel.postConnectBurst()`](composeApp/src/commonMain/kotlin/dev/openpolaris/ui/AppViewModel.kt),
  after `MountSession.connect()` returns true, fire this 9-code burst:
  1. `808` (firmware version, `cmdType=2`)
  2. `809` (serial number, `cmdType=2`)
  3. `802` (WiFi band, `cmdType=2`)
  4. `778` (battery base, `cmdType=2`)
  5. `779` (battery detail, `cmdType=2`)
  6. `775` (SD status, `cmdType=2`)
  7. `824` (OMS run state, `cmdType=2`)
  8. `524` (ex-axis state, `cmdType=3`)
  9. `543` (settling-time get, `cmdType=2`)
- **Time-set was *dropped*.** The earlier draft named `515`, but RE
  says `515 = HADJ_ANGLE`. If the live smoke shows the gimbal
  really does need a time-sync, we'll add it as a separate
  `timeSync()` method, not as a slot in this burst.
- **`544` (settling-time setter) is NOT in the burst.** It's only
  fired by `setSettlingTimeMs(ms)`. Pairing is `543` get / `544` set.
- **Tolerance:** each `request<T>()` is wrapped in `runCatching`.
  A parser that returns `null` → `matched!!` throws NPE → `request`
  catches it → `runCatching` swallows it. The burst never aborts
  on a single bad parse; subsequent codes still fire.
- **New types added** to [`MountState.kt`](shared/src/commonMain/kotlin/dev/openpolaris/core/domain/MountState.kt):
  `BatteryDetail`, `SdStatus`, `OmsState`, `ExAxisState`. Each has a
  `fromFrame` companion parser. `BatteryDetail.fromFrame` is the
  only merge parser — it accumulates 778 + 779 fields across the
  two responses.
- **Build evidence:** `JAVA_HOME=/home/ian/jdks/jdk-21.0.2
  ./gradlew :shared:jvmTest :composeApp:compileKotlinJvm` →
  `BUILD SUCCESSFUL`, 40 jvmTest pass.
- **Live verification (Step 8 below) still pending.**

### Step 6 — Implement and wire the camera info burst

- **What:** after Step 5's burst, fire all the `CAM_GET_*` codes to
  populate the camera state. Per [Codes.kt](shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/Codes.kt),
  the GETs are at:
  - 258 ISO
  - 260 WB
  - 262 FNum
  - 264 EV
  - 266 State
  - 268 Focus
  - 270 Img size
  - 272 Img fmt
  - 274 Color
  - 276 Shutter
  - 278 Capture mode

  **Note:** the earlier draft of this step listed `265, 266, 267, 268,
  275` and called them "ISO/WB/EV/Shutter/FNum" — that is wrong on
  every count (`265` is `CAM_SET_EV`, not ISO; `267` is `CAM_CAPTURE`;
  etc.). The original app's `getCanmeraInfo()` does fire this full
  GET set; capture the exact list from the live probe (Step 3) and
  pin it down in a `CAMERA_INFO_BURST` constant.
- **Cost:** ~hour. Mostly a switch in the parser.
- **Unblocks:** camera parameter display.

### Step 7 — Decide on Bluetooth scope

This is the one big open question. Two paths:

**Path A — TCP-only (recommended for v1).** The BLE wake pulse is
nice-to-have, not required. The user's earlier report is that
"wifi is currently turned off due to the numer of auth requests"
which is a **Linux** issue, not a gimbal issue. We can:
- Use `nmcli connection up polaris_d13e86` directly to associate.
- Skip the BLE pulse entirely. (User can press a physical button
  on the gimbal to power it up, or it may wake automatically when
  power is applied.)
- Drive the gimbal from the desktop app via TCP only.

This is the simplest path and is what the v1 PLAN.md already
assumes. BLE is only needed if the gimbal is not already
broadcasting its AP when we want to use it.

**Path B — Full BLE wake.** Add a Linux BlueZ path to
`tryBleAwakenWifi`:
- `bluetoothctl scan on` → filter for `polaris_` / `theta_`
- `bluetoothctl connect <MAC>` (just connect, no writes)
- after 1 s, `bluetoothctl disconnect <MAC>`
- then continue with `nmcli connection up polaris_d13e86`

This adds ~half a day of plumbing plus a new tool dependency on
`bluetoothctl` / `dbus-send`. It would let the desktop app wake
the gimbal from cold.

**Recommendation:** start on Path A. Revisit Path B only if Path A
hits a real "gimbal is asleep" problem in testing.

### Step 8 — Live integration test of the burst

- **What:** run the existing 7-burst probe but now through the
  full app stack (`MountSession` → `Connection` → real socket),
  capture outgoing bytes and incoming responses, compare with
  golden stub-server outputs.
- **Cost:** ~hour once Steps 4-6 are done.
- **Verifies:** end-to-end path is correct.

### Step 9 — Begin the PLAN.md milestones

At this point Phase 1 of [PLAN.md](PLAN.md) is unblocked. Status
screen, jog rocker, MJPEG preview, etc.

---

## Open questions to resolve during live test

These are the things we don't know yet that we have to learn from a
real gimbal. Document the answer in
[polaris-re-results.md](</home/ian/.copilot/session-state/57abdabb-a2e1-4a3e-a7c1-77b48d31c65a/files/polaris-re-results.md)
§10 when we get answers.

1. **Does the BLE pulse alone wake the WiFi AP, or does the gimbal
   need to be already powered?** (Inferred from RE: pulse alone
   suffices. Verify experimentally.)
2. **Does the gimbal reject TCP connections from un-paired phones?**
   (Inferred: no — original app has separate BT pairing but TCP
   comes up regardless. Verify.)
3. **Does the gimbal require the full post-connect burst, or can
   it serve single codes ad hoc?** (Inferred: it expects the burst;
   push/poll streams depend on it. Verify by NOT sending it and
   seeing if push frames still flow.)
4. **What is the actual `cmdType` value per code?** Codebase pins
   to `2` for all codes. The RE parser switch shows codes 513-549
   typically use `3`, and camera setters use `1`. Need to capture
   one of each kind from the live gimbal to be sure.
5. **What does the `value` payload look like for each code in the
   real response?** (e.g. 524 reply, 284 reply, 802 reply.)
6. **What interval does the firmware want for the 778/775 polls?**
   (RE shows a timer but exact value not captured.)
7. **Does the gimbal send unsolicited push frames, or only
   responses to queries?** (RE shows `284` is `PUSH_MODE_STATE`,
   strongly suggesting push. Need to confirm by capturing the
   wire without first asking.)
8. **Does the gimbal send its own heartbeat, or only respond to
   ours?** (OkSocket PulseManager sends and reads `h`. Does the
   firmware also send `h`?)

---

## Risk register (current)

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Polkit rule doesn't work after install (e.g. rule syntax error) | Low | Reverts to auth-flood | `journalctl -u polkit -n 50` to check; rule is simple JS |
| Gimbal firmware differs from RE (different vendor, different model) | Low–Med | Wire format mismatches | Burst probe will reveal in seconds; re-derive from response |
| Gimbal requires BT pairing before TCP (i.e. whitelist by BT MAC) | Low | TCP rejected | Implement BLE pair-and-bind (Path B above) |
| `1&` magic prefix is wrong (firmware wants different prefix) | Very low | First send fails | Send a `1&284&2&-100#` and inspect reply; if no reply, try no-prefix |
| `cmdType=2` is wrong for some code class | Med | Silently misinterpreted | Burst probe captures both requests AND replies; mismatch obvious |
| Auth-flood returns if user opens gnome-control-center wifi | Mitigated by Step 1 | Re-blocking | polkit rule covers wifi scan + enable-disable + network-control |
| NM profile is wrong (PSK when it should be OPEN, or vice versa) | Low | Won't associate | `nmcli connection show polaris_d13e86` to inspect; remove and re-add as needed |
| Decompile cache lost (`/tmp/benro-decompile/` wiped) | Already happened | RE findings are now only in distilled form | All decisions traceable through `polaris-re-results.md`; if raw `.java` is needed again, re-decompile from the BenroConnect APK in the `benropolarispatcher` repo |
| Live burst response format diverges from `fromFrame` parsers | Med | Burst parses, but fields stay null | `BatteryDetail`/`SdStatus`/`OmsState`/`ExAxisState` use `runCatching` so the burst stays non-fatal; mismatches surface in the log, then we tune the parsers |

---

## What is NOT in scope for the next session

Don't go chasing these until Steps 1-6 are verified:

- Implementing the camera params UI (Phase 3 in PLAN.md).
- Astro core (sidereal time, alt/az, catalog loader) — Phase 2 in
  PLAN.md. Has its own protocol work but is gated on Phase 1.
- Cellular / 4G / SIM / cloud remote — out of scope for v1.
- Implementing the full BT control plane (codes 1-5 over BLE GATT)
  when the TCP path is sufficient for v1.
- A native BlueZ BLE wake pulse — only if Path A in Step 7 fails.
- iOS target — out of scope for v1 (architecture permits later).

---

## Files to know about (and where the truth lives)

| Path | What it is |
|---|---|
| `docs/PLAN.md` | High-level product roadmap (Phase 0-4). Stable. |
| `docs/PROTOCOL.md` | Protocol reference (somewhat out of date). |
| `docs/OVERVIEW.md` | Product overview. |
| `docs/ARCHITECTURE.md` | System architecture. |
| `docs/SPEC.md` | v1 acceptance criteria. |
| `docs/PLANNING-2026-08.md` | **This document.** Actionable plan snapshot. |
| `shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/CommandBuilder.kt` | Wire format encoder. ✅ |
| `shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/ResponseParser.kt` | Wire format decoder. ✅ |
| `shared/src/commonMain/kotlin/dev/openpolaris/core/protocol/Codes.kt` | Code constants + `subtypeFor()`. ✅ |
| `shared/src/commonMain/kotlin/dev/openpolaris/core/domain/MountSession.kt` | Connect/handshake state machine. Defaults to `host=192.168.0.1, port=9090` ✅ (was Step 4). |
| `shared/src/commonMain/kotlin/dev/openpolaris/core/sim/SimulatedProtocol.kt` | Stub server protocol logic. |
| `tools/stub-server` | Standalone TCP server (the stub). |
| `/home/ian/.copilot/session-state/57abdabb-a2e1-4a3e-a7c1-77b48d31c65a/files/polaris-re-results.md` | Authoritative RE reference (894 lines). |
| `/home/ian/.copilot/session-state/57abdabb-a2e1-4a3e-a7c1-77b48d31c65a/files/50-openpolaris-wifi-scan.rules` | Polkit rule, ready to install. |
| `/tmp/benro-decompile/sources/...` *(cache wiped, lost since prior session)* | The original decompiled BenroConnect Java sources. The distilled findings are in `polaris-re-results.md`; the raw `.java` files must be re-decompiled from the APK in `benropolarispatcher` repo if any of them are needed again. |
| `polaris-re-results.md` §8 (`PolarisOrderCommunication`) | Central TCP protocol class — wire format at `sendOrder` line 1439. |
| `polaris-re-results.md` §8 (`WifiBroadcast`) | BLE + connect control — `tryBleAwakenWifi` at line 1150. |
| `polaris-re-results.md` §8 (`WifiSocketHelper`) | TCP socket + `socketReadResponse` line 167. |
| `polaris-re-results.md` §8 (`BLEMessageModel`) | BT message codes + frame format. |
| `polaris-re-results.md` §8 (`PhoneConnectUtils`) | WiFi config (OPEN network confirmed). |
| `polaris-re-results.md` §5 + §9 (`PolarisCMD`) | All code constants + per-code one-liner map. |

---

## TL;DR — next steps in priority order

These are the highest-leverage things to do next, in order. Each
is blocked on the one above it; if the user is short on time,
work top-down. (The detailed Step 1–9 breakdown with full context
is above in "Next steps in priority order".)

1. **Install the polkit rule** (one sudo, 30 s).
   `scripts/install-wifi-polkit-rule.sh`. This is the single
   blocker on all wireless work. Without it, every WiFi scan
   triggers an auth dialog and the user's productivity dies.
2. **Capture a live burst from the gimbal** (10 min).
   Power the gimbal, open the desktop app, click Connect,
   capture stdout + one log of the 9-code burst responses.
   Paste it into RE §10. This validates that the 808/809/802/...
   payloads we expect match what the gimbal actually sends, and
   gives us the data to tune `BatteryDetail`, `SdStatus`,
   `OmsState`, `ExAxisState` parsers.
3. **Implement Step 6 — camera info burst** (~half a day).
   Once the basic burst is proven live, add the
   `CAM_GET_DEVICE_INFO`, `CAM_GET_STATE`, `CAM_GET_VIDEO_RES`
   etc. queries to `AppViewModel.postConnectBurst()`. Camera
   info is the second highest-value data to show in the UI
   (after battery/SD state).
4. **Wire up the first real control command end-to-end** (~1 day).
   Pick one command — `START_RECORD` or `TOGGLE_MODE` — and
   prove that our request gets echoed and the gimbal reacts.
   Use it as the smoke test for the whole stack.
5. **Polish the UI** (depends on the above).
   The Compose UI is a placeholder; once real state flows, the
   actual controls can be wired to the real `request<T>()` calls.

## Re-starting tomorrow (cheat sheet)

1. **First:** check the polkit rule status
   (`ls -la /etc/polkit-1/rules.d/`). If not there, install it
   (Step 1, 30 s). If it's already there, you're done with auth.
2. **Second:** confirm the gimbal is online
   (`nmcli device wifi list | grep polaris_`). If not, power the
   gimbal.
3. **Third:** if both green, do the live burst probe
   (Step 3, 10 min). This is the single most informative thing
   we can do.
4. **Then:** run the desktop app (`./gradlew :composeApp:run`).
   On connect, the app fires the Step-5 burst automatically
   (808/809/802/778/779/775/824/524/543). Capture one full
   round-trip in the log and paste it into
   [polaris-re-results.md §10](</home/ian/.copilot/session-state/57abdabb-a2e1-4a3e-a7c1-77b48d31c65a/files/polaris-re-results.md)
   so we can tune the four `*Detail.fromFrame` parsers to the
   real wire format.
5. **Then:** start on Step 6 (camera info burst).

If you only have 10 minutes, do step 1. If you have an hour, do
1+2+3. If you have half a day, do all of the above plus start
Step 6.

The protocol side is no longer the bottleneck. The auth-flood
fix is. Get that installed and the rest flows.
