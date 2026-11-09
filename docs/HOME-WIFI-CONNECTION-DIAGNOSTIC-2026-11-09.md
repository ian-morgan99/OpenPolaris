# Home-Wi-Fi / station-LAN connection diagnostic

Issue: [OpenPolaris #102](https://github.com/ian-morgan99/OpenPolaris/issues/102).

The report is currently unverified. This document starts the OpenPolaris-only
investigation without changing NetworkManager, firmware, router settings, or
physical hardware.

## Current finding

OpenPolaris already accepts an editable mount host and control port in
`ConnectionPane`, constructs `MountSession` from those live values, persists a
successful endpoint in the connection `SessionMarker`, and keeps preview on a
separate `previewPort` (default 8080). The current connection flow does not,
however, distinguish Direct AP from Home Wi-Fi/LAN, does not discover the
station lease, and does not prove the endpoint before the generic connect
failure is shown.

The new Astro WebUI reference is also split:

- `polaris-httpd.c` defaults its HTTP/WebUI listener to port 8080 and binds it
  to `INADDR_ANY`.
- The Astro AP+station scripts keep the AP on `wlan0` and add a station
  interface (`wlan1`), then report the DHCP address for access from the home
  network.
- The Astro server's own mount commands default to `127.0.0.1:9090`, because
  they are local to the mount. That is not proof that `polestar_app`'s remote
  9090 listener is available on the station interface.

Consequently, an HTTP 8080 success would not prove native OpenPolaris control
on 9090, and a 9090 refusal would not be fixed safely by changing the preview
port or by forcing a Wi-Fi bridge.

## Failure hypotheses, ordered by evidence value

| Hypothesis | What would support it | Safe check |
|---|---|---|
| Wrong or stale station address | Router lease differs from the host entered in OpenPolaris | Read the router lease and the mount's `/api/wifi` status; do not change the lease. |
| Home subnet collides with the mount AP subnet | Station lease or route is also `192.168.0.x` while AP is `192.168.0.1` | Record both interface addresses and route selection; do not readdress the mount during diagnosis. |
| Client isolation/firewall | 8080/9090 fail only from the chosen client or VLAN | Test from a client on the same LAN and inspect router isolation policy read-only. |
| `polestar_app` is not bound to station LAN | 8080 answers but 9090 is refused; mount-side `ss -lntp` shows 9090 bound to AP/loopback only | Operator-run mount `ss`/process/bind inspection. |
| Service is down or port differs | Both endpoint probes fail, or process/log evidence has no 9090 listener | Mount-side process/log inspection; no restart as part of this diagnostic. |
| OpenPolaris endpoint propagation bug | TCP probe works independently but the app still connects to a different host/port | Add a fake-connection assertion and inspect session marker/reconnect values. |

“Connection refused” is stronger than a timeout: it usually means the route
reached an address and the TCP stack rejected the port. It still does not
identify whether the wrong host, a bind scope, a firewall, or a service state
caused it.

## Required operator evidence

Run only on the local LAN and only against the known Polaris address. Replace
`<station-ip>`; do not paste a password or public address into logs.

```text
# Client-side, read-only reachability
ip route get <station-ip>
nc -vz -w 3 <station-ip> 9090
nc -vz -w 3 <station-ip> 8080
curl --max-time 3 -sS -D - -o /dev/null http://<station-ip>:8080/

# Mount-side, read-only identity and listener evidence
ip addr
ip route
ss -lntp
ps
```

Record timestamps, client subnet/interface, mount firmware version, exact
station IP, whether the AP is still visible, and whether each result was
`connected`, `refused`, `timed out`, or `no route`. A successful HTTP 8080
response should be recorded separately from the native 9090 result.

Do not run `nmcli connect`, change routes, reconfigure the AP, restart
`polestar_app`, or issue a camera/motor command as part of this first evidence
pass. Those are configuration or hardware-affecting actions and need a separate
operator-approved plan.

## OpenPolaris implementation seam

The safe client-side design for #102 is:

| Concern | Direct AP | Home Wi-Fi/LAN |
|---|---|---|
| Connection mode | Explicit and visible | Explicit and visible |
| Control host | Default `192.168.0.1`, editable | Persisted station IP/hostname, editable |
| Control port | 9090 | 9090 unless verified otherwise |
| Preview endpoint | Same host, separate 8080 | Same station host, separate 8080 |
| Mount Wi-Fi setup | Not performed by OpenPolaris | External prerequisite; no hidden NetworkManager/firmware mutation |
| Fallback | N/A | Offer Direct AP without overwriting the saved LAN endpoint |
| Diagnostics | Route/control handshake | Route, 9090 probe, 8080 probe, endpoint identity, last failure phase |

The existing host/port fields and `SessionMarker` are the migration seam. The
next code slice should add a typed connection mode and endpoint-health result,
then wire diagnostics into the Connection pane. It should not claim Home-LAN
support until a station-IP 9090 handshake and reconnect cycle are observed.

## Acceptance gates

1. A supported client can use a verified station IP to read status and perform
   a non-destructive handshake on 9090.
2. Preview health on 8080 is reported independently from control health.
3. A DHCP change or mount restart produces an actionable endpoint diagnostic,
   not an indefinite Connecting state.
4. Direct AP still connects with the existing default and remains available as
   a fallback.
5. No WAN exposure, public discovery, forced BSSID, route takeover, firmware
   mutation, motor movement, shutter, or unattended Bulb operation is added.

## Related ownership

- #70 covers bridge/AP identity and recovery.
- #74 covers the separate preview port.
- #57 covers structured diagnostics.
- #98 P1.5–P1.7 covers network bridge, TCP ownership, and recovery.
- #103 records the Astro WebUI station-LAN and capture comparison.
