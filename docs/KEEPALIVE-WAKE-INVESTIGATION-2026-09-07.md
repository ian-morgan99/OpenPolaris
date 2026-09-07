# Polaris wake / Wi-Fi keep-alive investigation — 2026-09-07

## Status

**OPEN, reproducible, and a prerequisite for unattended camera testing.**

Do not describe ordinary control polling as a working keep-alive. On the
tested Polaris (firmware 6.0.0.54), the 5 GHz AP disappears after the Benro
Connect client leaves even while OpenPolaris has a healthy TCP control session.

## Proven environment

- Polaris SSID/BSSID: `polaris_d13e86` / `48:e7:da:d4:b5:73`
- Polaris Bluetooth identity observed live: `48:E7:DA:D4:B5:72`
- Wi-Fi: 5180 MHz, 80 MHz VHT, typically -79 to -81 dBm
- valid host route: `192.168.0.1 dev wlp8s0 src 192.168.0.4`
- invalid fallback route: `192.168.0.1 via 192.168.68.1 dev enp11s0`
  (Hitron LAN, never the Polaris)
- camera during the run: Pentax K-3 Mark III, USB `25fb:0189`

## Reproduction A: status polling does not keep the AP alive

1. Wake/connect the Polaris using Benro Connect on the iPad.
2. Activate the saved `polaris_d13e86` NetworkManager profile.
3. Establish a full OpenPolaris session (`284`, `820`, `823`).
4. Send code 284 every second and verify each response.
5. Power off Benro Connect/iPad.

Observed:

- 25 consecutive 284 responses succeeded, normally in 6–10 ms.
- The next health check timed out at about 26 seconds after the isolated run
  began.
- `iw dev wlp8s0 link` then reported `Not connected`.
- NetworkManager reported that the AP/SSID was no longer available.

Conclusion: code 284 is status/health polling, not the AP keep-alive.

## Reproduction B: opcode 822 does not keep the AP alive

The run was repeated with code 822 sent fire-and-forget every second immediately
before each successful code-284 health check.

Observed:

- Samples 1–88 succeeded. Typical 284 RTT was 7–15 ms; isolated spikes reached
  76 and 122 ms.
- Sample 89 timed out.
- NetworkManager timestamps prove the Wi-Fi association became active at
  `12:16:45` and disconnected at `12:18:14`: approximately 89 seconds.
- After the timeout, `wlp8s0` was disconnected and ports 22/8080/9090 were no
  longer reachable.

Conclusion: sending opcode 822 plus 284 does not reset the firmware's roughly
90-second AP shutdown/watchdog. The earlier apparent improvement was the
remaining lifetime of the Benro-created session, not proof that 822 worked.

## Reproduction C: desktop Bridge can appear successful transiently

The desktop **Bridge to mount Wi-Fi** action can activate a saved profile while
the AP is still visible. A status indication at that moment is not durable
proof. NetworkManager later records `ssid-not-found` when the Polaris removes
the AP.

During this investigation two desktop OpenPolaris processes were also found
running simultaneously. They were terminated before controlled testing because
multiple instances can race profile activation, session ownership, and teardown.
The desktop launcher currently points to this checkout's locally built binary:

`desktopApp/build/compose/binaries/main/app/OpenPolaris/bin/OpenPolaris`

## Wake failure observed on the PC

The current OpenPolaris JVM wake path is:

1. time-bounded `bluetoothctl scan on`;
2. `bluetoothctl pair ADDRESS`;
3. `bluetoothctl trust ADDRESS`;
4. `bluetoothctl connect ADDRESS`;
5. wait, then disconnect.

Live behavior:

- the Polaris appeared only briefly and weakly (about -99 dBm);
- after the scan ended, BlueZ could remove it from the device cache;
- pairing returned `org.bluez.Error.ConnectionAttemptFailed`;
- because pairing is mandatory in the current code, the actual GATT connect was
  never issued;
- when the mount was no longer advertising, direct `bluetoothctl` and
  `gatttool` connections could not restore it.

An experimental concurrent-scan/direct-connect change was **not merged** because
it was not hardware-verified. Do not copy that hypothesis into production.

## Local Benro APK evidence available to the next investigator

The locally supplied APK is:

`/home/ian/Downloads/BenroConnect_1727595281455.apk`

It was decompiled read-only with local jadx 1.5.6 into a temporary directory.
Relevant app-owned sources include:

- `com/snoppa/application/singleton/WifiBroadcast.java`
- `com/snoppa/application/oksocket/SocketHelper.java`
- `com/snoppa/polaris/singleton/PolarisOrderCommunication.java`
- `com/snoppa/application/constant/polaris/PolarisCMD.java`

The decompile confirms `192.168.0.1:9090`, preview on 8080, code 284, and the
822/823 constants. Numeric names alone are not proof of keep-alive behavior.

## Next investigation, in order

1. Trace `WifiBroadcast` GATT connection lifetime from device selection through
   Wi-Fi use. Determine whether Benro Connect intentionally keeps GATT connected
   rather than using a connect/disconnect pulse.
2. Trace every scheduled/repeated call from `SocketHelper` and
   `PolarisOrderCommunication`; record exact frame bytes and intervals.
3. Repeat with a packet capture or instrumented local relay while the physical
   iPad remains connected, then while it disconnects.
4. Test one candidate at a time against the 90-second boundary. Require at
   least five minutes of isolated operation before calling it fixed.
5. Make Wake report each terminal phase (not found, pair failure, connect
   failure, AP visible, profile active). Never let **Bridge** report success
   until `wlp8s0` has `192.168.0.x` and the guarded route targets that interface.
6. Add single-instance protection or shared bridge ownership so two desktop
   processes cannot fight over NetworkManager state.

## Acceptance criteria

- A cold/asleep Polaris can be restored from OpenPolaris without Benro Connect.
- With all Benro clients powered off, the Polaris remains reachable for at
  least five minutes while OpenPolaris is connected.
- The route remains `dev wlp8s0`; Hitron fallback is detected and rejected.
- One forced Wi-Fi drop automatically recovers without a second app instance.
- The exact behavior is covered by deterministic tests and a physical-hardware
  log with timestamps.

