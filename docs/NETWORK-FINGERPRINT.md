# Polaris gimbal network fingerprint

Quick rule of thumb for identifying the Benro Polaris gimbal on the LAN.
Confirmed by 2026-09-01 live probes and 2026-08-31 SSH probe evidence.

## TL;DR

| Port  | Open in normal mode? | What it is                                 |
|-------|----------------------|--------------------------------------------|
| 9090  | YES                  | `polestar_app` — the only authoritative listener. Custom binary protocol, banner `# polestar v…`. |
| 22    | optional             | Dropbear / OpenSSH. Only present if the patcher's `SSH_PUBKEY` debug hook has been installed (commit `004c057` of `blaineam/benro-polaris-firmware-patcher`, PR #1). Stock firmware does NOT have sshd enabled. |
| 80    | **NO — never**       | The Polaris userland does NOT bind TCP/80. If you see a webserver on :80 at the gimbal's IP, you are hitting a different device. |
| 443   | **NO — never**       | Same rule as :80. The userland does not do TLS. |

## If TCP/80 is open at 192.168.0.1, it is NOT the gimbal

The 2026-09-01 probe observed `Server: GoAhead-Webs` with a self-signed
`CN=server.example` certificate (validity 2016-01-14 → 2116-01-15 — the default
cert that ships with the GoAhead embedded web library). That is a totally
different device squatting the address. Most likely the LAN router, or a
nearby IP-camera or NAS. Do not interpret it as the gimbal.

## Recovery / firmware-update mode

When the gimbal is in firmware recovery (the user has triggered a `patch.sh`
run, or the userland failed to boot), `polestar_app` and `sshd` are killed and
the wlan0 AP may be down. ICMP from the LAN still works, but TCP/9090 and
TCP/22 are both connection-refused. Do not interpret that as "the gimbal is
off" — it is in recovery. Wait for it to come back.

## Physical network paths

The gimbal serves its own AP `polaris_d13e86` (BSSID `48:E7:DA:D4:B5:73`)
at `192.168.0.1/24`. The dev box reaches the gimbal over `wlp8s0` (MAC
`4c:23:38:c8:7b:6f` — a station of the polaris AP) when associated.
When `wlp8s0` is DOWN, the only path to the gimbal is via the LAN router
forwarding to the AP, which is unreliable and the gimbal often shows up
as the LAN router's own webserver on :80/443 (see above).

To reach the gimbal directly: `sudo ip link set wlp8s0 up && nmcli dev wifi
connect polaris_d13e86` (network key is the gimbal's WiFi PSK, printed on
the QR-code sticker under the battery or in the Benro app's network-setup
screen).
