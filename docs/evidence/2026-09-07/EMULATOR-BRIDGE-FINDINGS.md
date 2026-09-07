# Emulator + Gimbal Bridge — Findings 2026-09-07

## Goal

Run Benro Connect (148 MB APK at `/home/ian/Downloads/BenroConnect_1727595281455.apk`)
on a fully-detached Android emulator (AVD `polaris`, Android 34, swiftshader_indirect)
and capture the official-app wire traffic so we can hardware-verify the divergent
codes listed in [PROTOCOL.md §7](../PROTOCOL.md) and the
[Vyskocil Wiki Review](../VYSKOCIL-WIKI-REVIEW-2026-09-07.md).

## Network topology (verified today)

| Hop | Address | Notes |
| --- | --- | --- |
| Gimbal AP | `polaris_d13e86` (5 GHz, ch 36) | BSSID `48:e7:da:d4:b5:73` |
| Gimbal control plane | `192.168.0.1:9090` | TCP, only 1 client at a time |
| iPad | `192.168.0.2` | Benro Connect, port 9090 closed but BT link active |
| Host | `192.168.0.4` | Connected to gimbal AP, 5 GHz wifi |
| Emulator (internal wifi) | `10.0.2.16` (emulated) | Bridge to host via `10.0.2.2` |

## Headline finding — gimbal is in deep-sleep and TCP-only handshake doesn't wake it

- The emulator's NAT'd `10.0.2.16` wifi can't directly reach the 5 GHz AP
  (the QEMU/SLIRP stack doesn't see the host's 5 GHz radio). It does reach
  `10.0.2.2` (the host) — and the host sees the gimbal.
- A TCP relay `/tmp/gimbal-relay.py` (host `0.0.0.0:9091` → gimbal
  `192.168.0.1:9090`) is up and confirmed working: from the emulator
  `nc 10.0.2.2 9091` reaches the gimbal and the gimbal accepts the TCP
  handshake. Verified `nc` returns rc=0.
- **However:** the gimbal accepts the TCP socket but sends nothing back.
  No `284@mode:0;` push. No reply to `808@type:0;`, `820@…`, `821@token:…`,
  or `823@app:…;ver:…;`. This matches the documented
  "gimbal in deep sleep" behaviour flagged in
  [MountSession.kt `handshake failed: no response to 284 … — try Wake`](../../shared/src/commonMain/kotlin/dev/openpolaris/core/domain/MountSession.kt).
- The gimbal only wakes on a **bare Bluetooth GATT connect** to its BT
  MAC (see [BluetoothProbe.kt `wake()`](../../shared/src/jvmMain/kotlin/dev/openpolaris/core/net/BluetoothProbe.kt)).
  After that single connect+disconnect pulse, the TCP push messages appear
  within 2 s.

## The 5 GHz BT problem

- The user's gimbal is on `polaris_d13e86` (5 GHz, ch 36). When the gimbal
  is in 5 GHz mode the BT radio does **not** advertise on 2.4 GHz at all.
- The host's BT adapter (`hci0: 4C:23:38:C8:7B:70`, USB) is 2.4 GHz only.
- 18-second and 25-second `bluetoothctl scan on` runs find only the
  Pentax K3III camera (`18:48:CA:5F:4C:91 PENTAX_5F4C91`) — never the
  gimbal. The earlier short scan (before the 5 GHz switch) also found
  `5E:61:AA:51:80:D0` (random BLE).
- `sudo hcitool lescan --duplicate` returns `Set scan parameters failed:
  Input/output error` and `sudo bluetoothctl scan on` blocks waiting for
  input — both unusable from the sandboxed terminal.

## What worked

| Step | Tool | Result |
| --- | --- | --- |
| Boot emulator | `emulator -avd polaris -no-window …` (setsid) | Booted in 17 s, PID 3805644 |
| Install Benro Connect | `adb install -r BenroConnect_1727595281455.apk` | OK, package `com.snoppa.libra` |
| Grant permissions | `pm grant` (Calendar, Location, Nearby devices, Photos and videos, Notifications) | OK, all four toggles now Allowed |
| Launch app | `monkey -p com.snoppa.libra -c android.intent.category.LAUNCHER 1` | WelcomeActivity launched |
| Reach gimbal over TCP from emulator | `nc 10.0.2.2 9091` (via relay) | rc=0, TCP handshake OK |
| Identify gimbal vs host | gimbal ports 9090, host 80/22/8080 are open | gimbal is unique |
| Confirm the actual gimbal SSID | `iw dev wlp8s0 link` | `SSID: polaris_d13e86` (not `Polaris_00158D`) |

## What didn't work

| Step | Why |
| --- | --- |
| `gimbal:9090` direct from host | TCP connects, gimbal doesn't push — deep sleep |
| `820@` from host | TCP accepts frame, no reply |
| `808@type:0;` from host | TCP accepts frame, no reply |
| BT scan for the gimbal | 5 GHz mode → no 2.4 GHz BT advertisement |
| `sudo hcitool lescan` | `Set scan parameters failed: Input/output error` |
| `sudo bluetoothctl scan on` | Blocks waiting for input — not terminal-friendly |

## Recommended next step

**Switch the gimbal back to 2.4 GHz mode.** Once on 2.4 GHz:
1. The gimbal will start advertising BT on the same radio the host can
   scan.
2. `bluetoothctl pair/trust/connect/disconnect` on the gimbal's BT MAC
   (typically `48:E7:DA:D4:B5:74` — the wifi MAC +1) will perform the
   wake pulse documented in
   [BluetoothProbe.kt](../../shared/src/jvmMain/kotlin/dev/openpolaris/core/net/BluetoothProbe.kt).
3. The relay at `10.0.2.2:9091` is already in place; Benro Connect on
   the emulator should see `Polaris_00158D`/`polaris_d13e86` and connect
   cleanly, and we'll see the full official-app wire burst.

## Files written today

- `/tmp/gimbal-relay.py` — TCP relay `0.0.0.0:9091` → `192.168.0.1:9090`,
  with per-frame `C->G` / `G->C` logging to
  `/tmp/benro-emulator/relay.log`. The relay is fully detached; PID in
  the running process table.
- `/tmp/benro-emulator/relay.log` — running log of all relay traffic.
- `/tmp/benro-emulator/screen-*.png` — emulator UI screenshots.
- `/tmp/benro-emulator/ui-dump.xml` — last UIAutomator dump of the
  Benro Connect device-list screen.
