# Connectivity Test Findings — 2026-08-31 01:12 BST

## Summary

Started the 38 pending connectivity tests against the real Polaris. The
target host `192.168.0.1` (assumed to be the mount) is reachable on the
**home network** but is actually a **Hitron Technologies cable/DSL router**
(GoAhead-Webs server, login page at `/login.html`), not the Polaris mount.

The Polaris WiFi AP (`SSID=polaris_d13e86` per the saved
`/etc/NetworkManager/system-connections/polaris_d13e86.nmconnection`
profile) is **not currently broadcasting**. Visible 2.4/5GHz APs are:
- `AT975793993` (52:A1:5C:CA:66) — Hitron OUI, likely the home router
- `AT975793993` (32:87:BA:CA:0A:DA) — second physical AP, same SSID
- `AL7002222061784` (62:55:F9:C0:60:2C) — unknown OUI
- `tplink` × 4 — TP-Link router(s) on 2.4 + 5 GHz

**The mount is not reachable** — its control AP is off or out of range.
Bluetooth scan returned no devices.

## Network probe results (target 192.168.0.1)

| Port | State   | Note                                          |
|------|---------|-----------------------------------------------|
| 80   | OPEN    | GoAhead-Webs, 302→`/login.html` (Hitron web) |
| 443  | OPEN    | TLS admin page (same Hitron router)           |
| 9090 | closed  | Control protocol NOT listening here           |
| 9091 | closed  |                                               |
| 8080 | closed  | MJPEG preview NOT listening here              |
| 8081 | closed  |                                               |
| 23   | closed  |                                               |
| 554  | closed  | RTSP not exposed                              |
| 8000 | closed  |                                               |
| 5000 | closed  |                                               |

ICMP: 2/2 packets, 5.5ms avg RTT (definitely a live host, just not the mount).

## Diagnostic steps to confirm 192.168.0.1 is not the mount

1. `wget -q -S --timeout=5 -O /dev/null http://192.168.0.1/` →
   - `Server: GoAhead-Webs` (Hitron's embedded web server)
   - `Location: http://192.168.0.1/login.html`
   - Body title: **"Log in to Common Router - Hitron Technologies"**
2. The mount firmware's documented ports (9090 + 8080) are not listening
3. The mount is on its own subnet `192.168.0.0/24` only when the mount
   is the DHCP server (AP-mode). Right now `192.168.0.1` is the Hitron
   home router on the same physical ethernet.

## What we need to run the tests

The mount advertises `polaris_d13e86` as its AP SSID (per the saved
profile). For 192.168.0.1:9090 to be the mount, we need one of:

1. **WiFi handoff complete** — mount broadcasts `polaris_d13e86` AP,
   PC connects via the saved profile, mount's DHCP gives us 192.168.0.x.
   Currently **not** happening (SSID not in scan).

2. **STA mode + static IP** — mount joins the home WiFi as a station
   and we add a static route or DHCP reservation. Requires home router
   access (the Hitron on 192.168.0.1 has the same login page).

3. **BT-driven handoff** — pair the mount over Bluetooth and have it
   raise its WiFi AP. Bluetooth adapter is up but **no devices visible**.

## Suggested next move

- Confirm the mount is powered on (LED on the controller box)
- Wait for `polaris_d13e86` to appear in the wifi scan
- If using BT, bring the mount into pairing mode
- Once connected: `nmcli connection up polaris_d13e86` then re-run
  the port scan

## Code impact

**No code changes yet** — the tests can't run until the mount is
reachable. None of the 38 todos should be closed based on a network
that doesn't carry the mount. When the mount is online, start with
the easy probes (tcp-port-9090, handshake-284, position-517) and
record actual behaviour in `docs/CONNECTIVITY-TEST-RESULTS.md`.

The Hitron-web discovery does inform the plan: the `tcp-port-9090`
test should be re-validated to confirm **the mount's** port 9090
behaves the same as the Hitron web's port 80 (i.e. be ready for a
web login on the mount itself, separate from the control channel).
