# Benro Connect on Android Emulator — Setup Recipe (2026-09-07)

A self-contained, reproducible recipe for installing and driving the official
Benro Connect Android app on a fully-detached Android emulator, with all the
gotchas we hit today. The goal is to capture the official-app wire traffic
against the real gimbal so we can hardware-verify the divergent codes in
[PROTOCOL.md §7](../../PROTOCOL.md).

## What this lets us do

- Run the **actual official Benro Connect** Android client (not a stub) on
  an emulator we control, so we can:
  - capture every frame it sends to `192.168.0.1:9090`
  - see the gimbal's full handshake burst including codes our own
    MountSession doesn't speak
  - exercise K3III features (timelapse, panorama, motionlapse) and record
    what Benro Connect actually does
- Run the emulator on a desktop Linux box with bridged wifi to the gimbal
  AP, without needing a physical Android device.

## Prerequisites

- `~/android-sdk/platform-tools/adb` and `~/android-sdk/emulator/emulator`
  on PATH (the script uses absolute paths to avoid PATH races).
- One Benro Connect APK at
  `/home/ian/Downloads/BenroConnect_1727595281455.apk` (148 MB, package
  `com.snoppa.libra`). Verify with:
  `sha256sum /home/ian/Downloads/BenroConnect_1727595281455.apk`.
- An AVD named `polaris` already created via Android Studio's AVD Manager
  (we used Android 34, x86_64, 4 cores, 2 GB RAM, swiftshader_indirect GPU,
  no skin). `emulator -list-avds` should show it.
- A gimbal connected to the host over its wifi AP
  (`SSID: polaris_d13e86` on 5 GHz, default IP `192.168.0.1:9090`).

## 1. Boot the emulator fully detached

The emulator is a long-running daemon; it MUST be launched with `setsid`
and stdin redirected from `/dev/null`, otherwise the launcher session
holds the tty and the emulator dies the moment the launcher returns.

```bash
setsid /home/ian/android-sdk/emulator/emulator \
  -avd polaris \
  -no-window -no-audio -no-boot-anim -no-snapshot \
  -accel auto \
  -gpu swiftshader_indirect \
  < /dev/null > /tmp/emulator.log 2>&1 &
disown
```

- `-no-window` runs headless.
- `-no-audio -no-boot-anim` skips the audio device probe and boot
  animation (faster cold start).
- `-no-snapshot` ensures every boot is a clean cold boot; without it the
  emulator can resume from a saved state with a stale radio.
- `-accel auto` lets Android pick the host's KVM if available.
- `-gpu swiftshader_indirect` falls back to software GL when no GPU
  passthrough is available — necessary on most CI / headless boxes.

Verify the emulator is up:

```bash
ADB="/home/ian/android-sdk/platform-tools/adb"
$ADB devices                 # expect "emulator-5554   device"
$ADB shell getprop sys.boot_completed   # expect "1"
```

Boot takes ~17 s on this hardware. The emulator's log goes to
`/tmp/emulator.log`; first 200 lines have the kernel/dmesg tail.

## 2. Install Benro Connect and grant every permission

```bash
ADB="/home/ian/android-sdk/platform-tools/adb -s emulator-5554"
$ADB install -r /home/ian/Downloads/BenroConnect_1727595281455.apk
# Confirm the package
$ADB shell pm list packages | grep snoppa
# Grant the standard perms in one go. Benro Connect uses the regular
# Android permission names but the "Benro" toggle in the in-app dialog
# is a custom perm and CANNOT be granted via pm grant — you have to
# click through the dialog.
for P in \
  android.permission.ACCESS_FINE_LOCATION \
  android.permission.ACCESS_COARSE_LOCATION \
  android.permission.READ_EXTERNAL_STORAGE \
  android.permission.WRITE_EXTERNAL_STORAGE \
  android.permission.READ_MEDIA_IMAGES \
  android.permission.READ_MEDIA_VIDEO \
  android.permission.POST_NOTIFICATIONS
do
  $ADB shell pm grant com.snoppa.libra $P
done
```

The first `pm grant` is the one most often forgotten. Without
`ACCESS_FINE_LOCATION` Benro Connect will only find devices via wifi
ranging, not BLE, and will fail to find the gimbal even when it's on
the same AP.

## 3. Drive the in-app permission dialog

Even with `pm grant` done, Benro Connect still shows a "This feature is
not enabled. Allow the Theta App to use the required functionality in
your phone's settings." dialog on first launch. The two buttons are
"Cancel" (centre `333,1388`) and "Go to Settings" (centre `746,1388`).

`pm grant` does **not** satisfy the "Benro" / "Theta" custom permission
that the dialog is asking about, so we have to go to Settings:

1. `adb shell uiautomator dump` — dumps UI tree to
   `/sdcard/window_dump.xml`.
2. `adb pull /sdcard/window_dump.xml /tmp/ui-dump.xml` — pull for
   inspection.
3. `adb shell input tap 746 1388` — taps "Go to Settings".
4. On the App Info screen, the "Permissions" row is at
   `[66,1510][381,1584]`. Tap the text directly:
   `adb shell input tap 223 1547`.
5. For each "Not allowed" row, tap it, then tap "Allow" in the
   sub-page. The four rows are: **Location** (allowed), **Photos and
   videos** (allowed), **Calendar**, **Nearby devices**,
   **Notifications**. Each tap is on the *text* bounds, not the row
   container (which often has different clickability).

A small Python helper parses the dump so we know which coordinates are
real text labels:

```python
#!/usr/bin/env python3
import re, sys
data = open(sys.argv[1]).read()
for m in re.finditer(
    r'<node[^>]*\btext="([^"]+)"[^>]*\bbounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
    data,
):
    t = m.group(1)
    if not t: continue
    x1,y1,x2,y2 = (int(g) for g in m.group(2,3,4,5))
    if (x2-x1)<30 or (y2-y1)<30: continue
    print(f"  '{t}' center=({(x1+x2)//2},{(y1+y2)//2})")
```

## 4. Launch Benro Connect and find the gimbal

```bash
$ADB shell monkey -p com.snoppa.libra -c android.intent.category.LAUNCHER 1
# Wait for the device-list screen. A pre-populated entry
# "Polaris_00158D" / "polaris_d13e86" should appear within a few
# seconds (if the gimbal's wifi AP is up and the device is
# advertising mDNS).
```

To confirm the app is at the device-list screen:

```bash
$ADB shell uiautomator dump
$ADB pull /sdcard/window_dump.xml /tmp/ui-dump.xml
grep -E "Polaris|Connect|Discover|Search" /tmp/ui-dump.xml | head -5
```

Screenshots for visual inspection:

```bash
$ADB exec-out screencap -p > /tmp/benro-emulator/screen-N.png
```

## 5. Network bridge: emulator → host → gimbal

The Android emulator's NAT does **not** see the host's 5 GHz wifi radio
(it sees only the host's 10.0.2.x loopback). So `192.168.0.1` from
inside the emulator is the **host**, not the gimbal.

Verified port scan from the emulator (everything that is **not** the
gimbal):

| Port | Service | Identification |
| --- | --- | --- |
| 22 | OpenSSH 7.8 | Linux box, NOT the gimbal |
| 80 | lighttpd 1.4.63 | Linux box |
| 8080 | MJPG-Streamer/0.2 | webcam stream on the Linux box |

So we need a TCP relay on the host: emulator → `10.0.2.2:9091` → host
→ `192.168.0.1:9090`. Script is at `/tmp/gimbal-relay.py`:

```python
#!/usr/bin/env python3
import socket, sys, threading
GIMBAL_HOST, GIMBAL_PORT = "192.168.0.1", 9090
LOG = open("/tmp/benro-emulator/relay.log", "a", buffering=1)
def log(m): LOG.write(m+"\n"); LOG.flush()
def pipe(src, dst, label):
    try:
        while True:
            try: data = src.recv(4096)
            except OSError: break
            if not data: break
            try: dst.sendall(data)
            except OSError: break
            log(f"{label} {len(data):5}B: {data!r}")
    finally:
        for s in (src, dst):
            try: s.shutdown(socket.SHUT_RDWR)
            except: pass
def handle(c, a):
    log(f"client {a} -> gimbal")
    g = socket.create_connection((GIMBAL_HOST, GIMBAL_PORT), timeout=5)
    t1 = threading.Thread(target=pipe, args=(c, g, "C->G"), daemon=True)
    t2 = threading.Thread(target=pipe, args=(g, c, "G->C"), daemon=True)
    t1.start(); t2.start(); t1.join(); t2.join()
port = int(sys.argv[1]) if len(sys.argv)>1 else 9090
s = socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(("0.0.0.0", port)); s.listen(5)
log(f"relay 0.0.0.0:{port} -> {GIMBAL_HOST}:{GIMBAL_PORT}")
while True:
    c, a = s.accept()
    threading.Thread(target=handle, args=(c, a), daemon=True).start()
```

Boot it fully detached:

```bash
pkill -f gimbal-relay.py
setsid python3 /tmp/gimbal-relay.py 9091 \
  < /dev/null > /tmp/benro-emulator/relay-stdout.log 2>&1 &
disown
ss -tln | grep :9091   # expect "LISTEN 0  5  0.0.0.0:9091 ..."
```

## 6. Verify the bridge

```bash
# From the host: should connect through to the gimbal.
nc -w 3 127.0.0.1 9091 </dev/null && echo "host: relay works"
# From the emulator: should also reach the gimbal via 10.0.2.2.
$ADB shell 'nc -w 3 10.0.2.2 9091 </dev/null; echo "rc=$?"'
```

**Important caveat:** the gimbal accepts the TCP socket but only sends
data once it's been woken by a Bluetooth GATT connect. See [§7
below](#7-the-bluetooth-wake-gate).

## 7. The Bluetooth wake gate

The Benro Polaris gimbal's control-plane TCP socket (port 9090) is
silently held by the firmware until a Bluetooth GATT connect to the
gimbal's BT MAC is made. This is the "wake pulse" pattern documented
in
[`shared/src/jvmMain/kotlin/dev/openpolaris/core/net/BluetoothProbe.kt`](../../shared/src/jvmMain/kotlin/dev/openpolaris/core/net/BluetoothProbe.kt):

```kotlin
runner.run(listOf("bluetoothctl", "pair",   device.address))
runner.run(listOf("bluetoothctl", "trust",  device.address))
runner.run(listOf("bluetoothctl", "connect", device.address))
Thread.sleep(wakeSettleMs)            // 2_000 ms
runner.run(listOf("bluetoothctl", "disconnect", device.address))
```

After that single connect+disconnect cycle the gimbal starts pushing
`284@mode:0;`, `790@step:1;password:...;securityQ:3;securityA:...;`,
etc. on the next TCP open.

### The 5 GHz blocker

When the gimbal is in 5 GHz mode (`SSID: polaris_d13e86`, ch 36), the BT
radio does **not** advertise on 2.4 GHz. The host's BT adapter is
2.4 GHz only, so scans find only other 2.4 GHz devices (e.g. a Pentax
K3III camera) and never the gimbal.

| Tool | Result on 5 GHz gimbal |
| --- | --- |
| `bluetoothctl scan on` (18-25 s) | finds Pentax_5F4C91 only |
| `sudo hcitool lescan --duplicate` | `Set scan parameters failed: Input/output error` |
| `sudo bluetoothctl scan on` | blocks waiting for terminal input |

### Workaround

Switch the gimbal back to 2.4 GHz via the Benro Connect app on the
**physical phone**, or via the gimbal's physical button / menu. After
the switch:

```bash
bluetoothctl scan on   # 18-25 s
# Look for the gimbal's BT MAC. For our hardware the wifi BSSID is
# 48:E7:DA:D4:B5:73, so the BT MAC is usually the same or +1.
bluetoothctl devices   # confirm
# Then run the wake pulse using the actual address.
```

## 8. The 4-frame handshake (once the gimbal is awake)

Once the gimbal is awake, the host's `nc` should immediately see
pushed frames on connect. Drive the full handshake to confirm
ours + theirs agree:

```bash
# Open TCP, send 4 frames in order, log all 4 replies.
python3 <<'PY'
import socket, time
s = socket.create_connection(("127.0.0.1", 9091), timeout=5); s.settimeout(3)
print("Connected. Reading pushes for 4s...")
end = time.time() + 4
all_data = b""
while time.time() < end:
    try: data = s.recv(4096)
    except socket.timeout: continue
    if not data: break
    all_data += data
    print(f"  PUSH: {data!r}")
print(f"Total: {len(all_data)}B")
for frame, name in [
    (b"1&820&2&#",                                   "820 probe"),
    (b"1&821&2&token:MTIzNA==;#",                    "821 token (1234)"),
    (b"1&823&2&app:openpolaris;ver:0.1.0;#",         "823 hello"),
]:
    s.sendall(frame); print(f"\nSent {name}")
    end = time.time() + 3
    while time.time() < end:
        try: data = s.recv(4096)
        except socket.timeout: continue
        if not data: break
        print(f"  recv: {data!r}")
s.close()
PY
```

Expected sequence on a live `sw:6.0.0.54` gimbal (per our
[`MountSessionAuthTest`](../../shared/src/commonTest/kotlin/dev/openpolaris/core/domain/MountSessionAuthTest.kt)):

1. `284@mode:0;` push (might also be `state:N;`)
2. `820@needed:0;` (or `needed:1;` if the password is set)
3. `821@ret:0;` (or `ret:1;` on reject)
4. `823` is fire-and-forget on real firmware (no reply)

## 9. Bringing everything back down

```bash
pkill -f gimbal-relay.py
# The emulator is also a long-lived daemon; leave it running for the
# next session if you want to pick up where you left off:
$ADB shell am force-stop com.snoppa.libra
# Otherwise, shut it down cleanly:
$ADB emu kill
```

## Files written today

- `/tmp/gimbal-relay.py` — TCP relay, fully detached, logs every frame
  in both directions to `/tmp/benro-emulator/relay.log`.
- `/tmp/benro-emulator/relay.log` — running frame log (C->G and G->C
  with byte counts and printable payloads).
- `/tmp/benro-emulator/screen-NN-*.png` — Benro Connect UI
  screenshots from this session.
- `/tmp/benro-emulator/ui-dump.xml` — last UIAutomator dump.

## Next-session checklist (handing over to the next agent)

1. Switch the gimbal to 2.4 GHz mode (Benro Connect on a physical phone
   or the gimbal's physical menu).
2. `bluetoothctl scan on` to find the gimbal's BT MAC.
3. Run the wake pulse (`pair → trust → connect → sleep 2s →
   disconnect`) on that MAC.
4. The relay at `/tmp/gimbal-relay.py 9091` is still in place;
   restart with `setsid python3 /tmp/gimbal-relay.py 9091` if it died.
5. `python3` test from §8 above — confirm the gimbal pushes data on
   the relay.
6. Drive Benro Connect through the device list → connect → start a
   timelapse / panorama / motionlapse, and capture every frame in
   `/tmp/benro-emulator/relay.log`.
7. Compare the captured Benro Connect frames against the divergent
   codes table in
   [PROTOCOL.md §7](../../PROTOCOL.md) and update with
   hardware-verified findings.
