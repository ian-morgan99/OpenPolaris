# Network state change 2026-09-07 15:45

**Author**: this agent (OpenPolaris session)
**Subject**: User asked to retry. Network state has changed since
the K-1 II matrix was recorded at 15:35.

## What changed

| Item | 15:35 (matrix time) | 15:45 (now) |
|---|---|---|
| `ip route get 192.168.0.1` | `dev wlp8s0 src 192.168.0.4` | `via 192.168.68.1 dev enp11s0 src 192.168.68.89` |
| `wlp8s0` state | UP, associated with `polaris_d13e86` | `NO-CARRIER, BROADCAST, MULTICAST, UP state DOWN` |
| `enp11s0` state | (not in use) | UP, IP 192.168.68.89/22 |
| `polaris_d13e86` SSID | visible in scan | **NOT in scan results** |
| SSH to 192.168.0.1 | works | connection refused |
| Port 9090 on 192.168.0.1 | listening | closed |
| Port 8080 on 192.168.0.1 | not bound (patcher#38) | not bound |
| Port 80 on 192.168.0.1 | lighttpd serving | now serves the **Hitron router admin** (different device) |

## What this means

The PC is no longer on the gimbal's AP. The PC is on the
home ethernet network (Hitron cable modem/router combo,
192.168.68.x subnet).

The gimbal itself is **not visible on any network I can
find**:
- `polaris_d13e86` SSID is gone from wifi scans
- 192.168.0.1:80 now serves the Hitron (because the
  Hitron's own admin page is at 192.168.0.1 on the WAN side,
  OR the gimbal and Hitron are on the same subnet and the
  Hitron wins the IP)
- 192.168.68.x has no SSH/80 on the addresses I tried

## Possible causes

1. **Gimbal powered off** — the user turned it off, possibly
   to do a physical intervention (battery swap, lens
   change, etc.)
2. **Gimbal rebooting** — hostapd takes 30-60s to start after
   boot. If I wait 60s and re-scan, `polaris_d13e86` might
   reappear.
3. **AP changed** — the user pressed the WiFi reset on the
   gimbal, which changes the SSID or password
4. **Gimbal moved** — physically moved to a different
   location or plugged into a different network

## What I tried

- Re-scan wifi (no `polaris_d13e86`)
- Wait 30s, re-scan (no `polaris_d13e86`)
- Scan 192.168.68.50/100/150/200/70.10/71.10 for SSH/80
  (none)
- Broadcast ping (Destination Host Unreachable)
- Port 22, 80, 9090, 8080 on 192.168.0.1 (only 80, which is
  the Hitron)

## What I cannot do (and why)

- **Cannot SSH to the gimbal** — connection refused
- **Cannot run protocol probes** — port 9090 closed
- **Cannot verify gimbal state** — no path in
- **Cannot reconnect to the AP** — `polaris_d13e86` is not
  broadcasting; would need the user to (a) confirm the AP
  SSID/password and (b) confirm the gimbal is powered on
- **Cannot start the K-1 II work over** — same as above

## Recommended next user action

1. Check the gimbal is powered on (LED on, screen on if
   applicable)
2. Wait 60s for full boot if it just powered on
3. Confirm `polaris_d13e86` is broadcasting (look at the
   gimbal's screen or use a phone wifi scan)
4. If the SSID changed, tell me the new one
5. If the gimbal is off, power it on

## No code or PC state changes

This is purely a network observation. No files were written
to the gimbal. No files on the PC were modified except this
evidence doc.

## Committed this doc, ready for the user to act

After the user confirms gimbal state, the next agent can
resume from the K-1 II matrix in LIVE-PROGRESS.md and re-run
§6 of K1II-SWAP-PLAN.md. The matrix is already complete
(5 PASS, 2 FAIL, 17 NOT TESTED); the only thing that would
change with a working runtime is the NOT TESTED rows
becoming PASS/FAIL.
