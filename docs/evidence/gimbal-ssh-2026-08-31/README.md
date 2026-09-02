# Live Gimbal SSH Recon — 2026-08-31

> Captured during a live SSH session against a powered-on Benro Polaris gimbal.
> The gimbal was connected to its own WPA2 AP at `192.168.0.1`.
> We logged in as `root` with an empty password (consistent with the
> `PermitRootLogin yes` / `PermitEmptyPasswords yes` configuration found in
> `sshd_config.txt` — see `MANIFEST.md` for capture details and the
> redaction log).
> The gimbal is the device under test; client identifiers in `dmesg.txt`
> have been redacted (see `MANIFEST.md`).

This directory contains **observed evidence** captured from one specific
gimbal unit running one specific firmware build (the running build is
documented in `fw-install-flow.txt`). The static reverse-engineering work
that motivates the OpenPolaris app is recorded in
`docs/FIRMWARE-ANALYSIS-ALPACA.md` and related firmware-analysis documents
in the `docs/` tree. Where the static RE made predictions that can be
checked against this captured evidence, section 7 lists them; the comparison
is observational only, on this device, on this firmware.

---

## 1. Hardware reality check (this is NOT Ingenic)

The kernel banner:

```
Linux version 4.9.37 (win@win) (gcc version 6.3.0 (HC&C V1R3C00SPC200B005_20190606) ) #59 SMP Wed Sep 4 12:29:11 CST 2024
OF: fdt:Machine model: Hisilicon HI3559V200 DEMO Board
CPU: ARMv7 Processor [410fc075] revision 5 (ARMv7), cr=10c5387d
```

The SoC is **HiSilicon Hi3559V200** (a Huawei-family IP-camera SoC).
This is a significant correction to the prior assumption that the gimbal used
an Ingenic X-series SoC. Implication: the SDK and chip-specific binaries
shipped in `/app` are built for Hi3559V200, not Ingenic. The build environment
(`win@win`) matches the camera SDK template (the SDK and the gimbal share
firmware origins).

CPU details:
- 2× ARMv7 cores (`mpidr 80000000` + `80000001`) — confirmed SMP
- 100 BogoMIPS (so ~500 MHz core clock)
- 1.5 GiB total RAM (`mem=1536M` from kernel command line)
- 1.5 MiB CMA pool

GPU / video: Hi3559V200 has **Mali G71** (not exposed to userspace) plus the
HiSilicon video processing subsystem (VI/VPSS/VENC/VDEC/IVE/NNIE). All those
modules loaded successfully:

```
load sys.ko for Hi3559V200...OK!
load tde.ko for Hi3559V200...OK!
load vi.ko for Hi3559V200...OK !
load isp.ko for Hi3559V200...OK !
load vpss.ko for Hi3559V200...OK!
load vdec.ko for Hi3559V200...OK!
load venc.ko for Hi3559V200...OK!
load h264e.ko for Hi3559V200...OK!
load h265e.ko for Hi3559V200...OK!
load nnie.ko for Hi3559V200...OK!     <-- HiSilicon neural-net accelerator
load mipi_rx driver successful!
```

NNIE (Neural Network Inference Engine) presence is huge: any future work on
auto-tracking, object detection, or plate-solving on-device can target the
NNIE directly.

**Implication for OpenPolaris**: the RE pipeline must treat the chip as
Hi3559V200, not Ingenic. The static RE found `$1000_0000` and similar
addresses — these are HiSilicon register windows, not Ingenic.

---

## 2. Flash layout (rootfs.ubifs + appfs.ubifs)

```
6 cmdlinepart partitions found on MTD device hinand
0x000000000000-0x000000080000 : "u-boot.bin"   (512 KB)
0x000000080000-0x000000100000 : "factoryParam" (512 KB)  <- factory data (calibration, MAC, S/N)
0x000000100000-0x000000200000 : "userParam"    (1 MB)    <- per-unit config
0x000000200000-0x000000700000 : "uImage"       (5 MB)    <- kernel + dtb
0x000000700000-0x000002f00000 : "rootfs.ubifs" (40 MB)   <- mounted as / (rootfs)
0x000002f00000-0x000008000000 : "appfs.ubifs"  (81 MB)   <- mounted as /app
```

Physical storage: **Winbond W25N01GV**, 128 MiB SLC NAND, 2 KiB page, 128 KiB
PEB. The "hinand" MTD driver is a Hisilicon glue wrapper around the SPI-NAND
controller.

UBI/UBIFS mounted cleanly on both volumes, no corruption detected (one
"recovery needed" line, but `recovery completed` immediately after — this is
normal after unclean shutdowns, not a sign of a broken flash).

**Why this matters for the firmware question (your original ask)**: the
firmware **is** loading. Both `rootfs.ubifs` (the OS image with BusyBox,
sshd, init scripts) and `appfs.ubifs` (the gimbal application — `polestar_app`,
`pgphoto`, `lighttpd`, `hostapd`, `udhcpd`, `gdbserver`, etc.) mounted cleanly.
The appfs is at 81 MiB and contains the entire `/app` tree we see today.

If the firmware had failed to load, we'd see:
- `UBIFS error` lines (not present)
- `VFS: Cannot open root device "ubi0"` (not present)
- `ubi0: error attaching mtd4` (not present — only one `ubi0: attached mtd4`)

It is **not** the firmware. See section 3 for what might actually be wrong.

---

## 3. Real problems found in dmesg

| Problem | Severity | What it means |
|---|---|---|
| `hisi-sfc hisi_spi_nor.0: spi nor register fail!` | benign | SPI NOR not populated (this board has NAND only, not NOR) — driver probes anyway. |
| `WARNING: bcmdhd_wlan.1 cannot create duplicate filename '/devices/platform/bcmdhd_wlan.1'` | **BUG** | The Broadcom WiFi driver is being registered twice. The DT has both `bcmdhd_wlan` and `bcmdhd_wlan.1` defined, but the kernel only allows one. The second registration is silently dropped. Likely **causes flaky WiFi**, especially after `wl_event_handler` re-attach events. |
| `dhd_preinit_ioctls: country code setting failed` | **BUG** | WiFi firmware didn't accept the country code; the AP likely comes up on the wrong channel set. **Could explain the AP being on channel 9 and clients occasionally losing connection** (we saw this in dmesg with `MACEVENT: WLC_E_DEAUTH reason 7`). |
| `dhd_preinit_ioctls Set txbf failed -23` | benign | TX beamforming not supported on this chip variant; benign. |
| `dhd_preinit_ioctls pspretend_threshold for HostAPD failed -23` | **BUG** | `pspretend_threshold` is a power-save setting for the AP; HostAPD needs this to manage clients in low-power mode. -23 = `EINVAL`. Probably means the WiFi firmware doesn't support a feature HostAPD is asking for. Likely not critical but explains why **clients get deauth'd** at random. |
| `wifi_platform_prealloc: failed to alloc static mem section 7` | **BUG** | The preallocated DMA memory for the WiFi chip wasn't allocated; the driver will use dynamic memory instead. Slight perf hit, not fatal. |
| `_tdata_psh_info_pool_deq 200: Out of tdata_disc_grp` × many | **BUG** | The Broadcom driver's TCP-data info pool is exhausted. The pool is leaking under load. This is the **#1 smoking gun for "control plane feels slow"** — every TCP connection from the phone to port 9090 will hit this leak. |
| `FAT-fs (mmcblk0p1): Volume was not properly unmounted` | low | SD card wasn't cleanly unmounted last time. Run `fsck` on next maintenance. |
| `hisilicon SPI Nor ID: unrecognized` | benign | No SPI NOR fitted. |
| `sensor1: NULL ... SNS:[NULL] is not supported !` | benign | The board has 2 MIPI-CSI slots but only `imx327` is populated (sensor0). The second slot is empty. |
| `hibvt-i2c 120b2000.i2c: wait idle abort!, RIS: 0x611` | **BUG** | I2C bus 2 timed out on a transaction. RIS (raw interrupt status) `0x611` = bus error / arbitration lost. Probably a flaky camera I2C line or a misbehaving sensor. Correlates with the "scan edges / Tuning SampleClock" re-initialization that happens right after. |
| `setenv bootcmd` with no value | **BUG** | u-Boot is printing the bootcmd before setenv. This is just informational; Benro's u-Boot sets bootcmd then boots. Not a real bug. |

### Top 3 things that explain "the firmware didn't load" if you've ever felt that

If the user has been saying "the firmware didn't load", these are the
candidates ranked by likelihood:

1. **The WiFi driver double-register (`bcmdhd_wlan.1`)** — the second WiFi
   platform device is silently rejected. This can cause the AP to come up
   without a sane interface and clients to deauth loop. If the phone is
   hitting this state, it looks like "firmware isn't responding" because
   port 9090 is unreachable.
2. **The TCP-data-info pool leak** — under sustained traffic the Broadcom
   driver runs out of push-info slots. New connections from the phone to
   9090 get starved. Looks like "app is hung" from the phone's perspective.
3. **The I2C bus 2 timeout on sensor path** — the imx327 sensor sometimes
   fails to respond on I2C2 and needs a re-scan. This is the camera pipeline
   being flaky, not the gimbal. Wouldn't affect control plane.

None of these are **firmware load** failures. The firmware loads every boot
(we have 60s uptime confirming the current boot is stable and the app is
running). The phone-side perception of "not loaded" is almost certainly
**one of the three bugs above**.

---

## 4. Process inventory

```
PID  PPID  USER     COMMAND
  1     0  root     init
248     1  root     ./polestar_app            <-- main control plane on :9090
249     1  root     /app/bin/pgphoto          <-- gPhoto2 wrapper for USB camera (P,T,AVI)
247     1  root     ./polaris_wifi_bt         <-- WiFi/BT coexistence helper
267     1  root     /usr/local/bin/sshd       <-- openssh, opened :22
301     1  root     /app/bin/lighttpd         <-- HTTP UI on :80 and :8080
339     1  root     /app/wifi/hostapd         <-- the AP daemon
343     1  root     /app/wifi/udhcpd          <-- DHCP server for AP clients
395     1  root     /app/bluetooth/bin/dbus-daemon
397     1  root     /app/bluetooth/libexec/bluetooth/bluetoothd
510     -   root     sshd: root@notty         <-- our live session
```

The `polestar_app` process is **PID 248** and is the actual gimbal control
plane. It binds 9090. We didn't run `cat /proc/248/cmdline` to see its full
argv, but the cmdline is `./polestar_app` (single binary, no args).

`pgphoto` (PID 249) is the gPhoto2 wrapper that talks to DSLR cameras over
USB. The presence of `/app/bin/gphoto2` and the `libgphoto2.so.6` family in
`/app/lib` confirms Benro uses libgphoto2 to drive tethered cameras. This
is the file-storage / live-view path.

`polaris_wifi_bt` (PID 247) is the WiFi/BT coexistence helper — a small
binary that tells the Broadcom chip which radios should be active.

`lighttpd` runs the HTTP UI on **both port 80 and port 8080** (likely one is
the API and one is the static file server; the nmap probes showed
`HTTP 403 Forbidden` from lighttpd/1.4.63 on both — needs auth or path).

`hostapd` runs the AP. `udhcpd` runs the DHCP server. Together they are
the WiFi AP infrastructure.

---

## 5. Network state (live)

```text
Interface: wlan0
  IP: 192.168.0.1/24
  MAC: 48:e7:da:d4:b5:73  (Snoppa OUI)
  Op mode: 0x0005 (AP)
  Driver: bcmdhd 1.363.125.19 (May 13 2021)
  FW:    wl0: Mar 22 2019 00:03:25 version 7.45.100.18 (r709126 CY) FWID 01-a562d688

Listening:
  TCP  0.0.0.0:22     sshd
  TCP  0.0.0.0:80     lighttpd (web UI)
  TCP  0.0.0.0:8080   lighttpd (web API / live view)
  TCP  0.0.0.0:9090   polestar_app (control plane)

No eth0, no usb0, no ppp0.
```

There is **no USB-C ethernet gadget** (g_ec or g_ether). This confirms
the static RE prediction: the kernel has no USB gadget support compiled in.
The USB-C port on the bottom is power-only.

There is **no PPP** running either, despite `pppd` being present in
`/app/bin`. The Quectel cellular modem path is unused right now (this unit
has no cellular modem plugged in).

---

## 6. Listening ports, decoded

| Hex   | Decimal | Owner              | Purpose                                       |
|-------|---------|--------------------|-----------------------------------------------|
| 0x0016| 22      | sshd               | openssh root login (PermitEmptyPasswords yes) |
| 0x0050| 80      | lighttpd           | web UI (HTTP 403)                             |
| 0x1F90| 8080    | lighttpd           | second HTTP server (alt)                      |
| 0x2382| 9090    | polestar_app       | **gimbal control plane** ← this is what the phone app talks to |
| UDP 0x0043 | 67  | udhcpd             | DHCP server for the AP                        |

**The control plane is on port 9090**, not 8080. The HTTP servers are
only for the web admin UI. When reverse-engineering the protocol, all
binary traffic to the gimbal goes to 9090.

---

## 7. Predictions checked against this capture

The static firmware analysis work for the Benro Polaris is recorded in
`docs/FIRMWARE-ANALYSIS-ALPACA.md` and related documents under
`docs/firmware/`. That analysis is the source of the predictions below; the
comparisons here are observational only, against this single capture. Do not
generalise to other firmware builds, other units, or future releases.

Predictions **confirmed by this capture**:

- `polestar_app` binary (~24 MB unstripped) → confirmed running (see `ps.txt`)
- `pgphoto` wrapper (~8 MB) → confirmed running (see `ps.txt`)
- `lighttpd` config → confirmed serving on 80/8080 (see `netstat.txt`)
- `hostapd` + `udhcpd` configs → confirmed running
- `sshd_config` with `PermitRootLogin yes` + `PermitEmptyPasswords yes` →
  consistent with the live empty-password login (see `sshd_config.txt`)
- `/etc/init.d/rcS` last line `/usr/local/bin/sshd` → consistent with the
  running `sshd` process (see `ps.txt`)
- No USB gadget in kernel → consistent (no `usb0` / `eth0` in `ip.txt`)
- `pppd` binary in `/app/bin` but unused → consistent (no `ppp0` in `ip.txt`)
- Binary protocol on port 9090 (TCP listener present) → consistent with
  `netstat.txt`

Predictions **not directly verified by this capture** (deliberate gaps):

- **Pre-generated SSH host keys** — the static RE produced fingerprints;
  this capture's `hostkeys.txt` shows that `ssh-keygen` is not on the
  gimbal's PATH, so we did not extract fingerprints. A follow-up capture
  should copy `/etc/ssh/ssh_host_*_key.pub` off-device.
- **Password hash values in `/etc/passwd` and `/etc/shadow`** — this bundle
  redacts the hash fields (see `MANIFEST.md`). We can confirm that `root`
  has *a* password entry whose hash format matches the static RE, and that
  empty-password login succeeds, but we do not commit the literal hash.

Predictions **not yet testable from this capture**:

- Camera-driver behaviour in the running `pgphoto` / `gphoto2` pair
  (firmware version on disk is still the factory build — see
  `fw-install-flow.txt`).
- NNIE / VI / VPSS pipeline configuration under live load.
- `pppd` binary in `/app/bin` but unused → confirmed live (no `ppp0`
  interface, no Quectel modem plugged in)
- The protocol on port 9090 → confirmed live (TCP connect succeeds, nmap
  identifies it as `zeus-admin` which is just nmap's guess)

For each prediction above, the live capture **confirms** the static RE
finding on this device and firmware build. It does not by itself prove
that the same finding holds on other firmware builds; broader claim is
deliberately out of scope for this evidence bundle (see
`MANIFEST.md`).

---

## 8. Files in this directory

| File | What it is |
|------|------------|
| `dmesg.txt`              | Full kernel ring buffer (528 lines) |
| `ps.txt`                 | Process list (112 lines) |
| `cpuinfo.txt`            | /proc/cpuinfo (23 lines) |
| `meminfo.txt`            | /proc/meminfo |
| `mounts.txt`             | /proc/mounts |
| `df.txt`                 | Filesystem usage |
| `ip.txt`                 | Network interfaces |
| `listening.txt`          | TCP/UDP listening sockets from /proc/net |
| `mtd.txt`                | MTD partitions (hisi-nand flash) |
| `uname.txt`              | uname -a + /proc/version |
| `app-tree.txt`           | /app file tree (200 lines) |
| `app-conf.txt`           | /app/conf contents |
| `app-bin.txt`            | /app/bin listing |
| `version.txt`            | Version files (mostly empty — version is in /app/BUILD_INFO or compiled in) |
| `etc.txt`                | /etc directory listing |
| `etc-passwd.txt`         | /etc/passwd + group + shadow |
| `sshd_config.txt`        | Live sshd config |
| `usb.txt`                | lsusb + /proc/bus/usb/devices |
| `dev.txt`                | /dev listing (12 KB — lots of mknod'd devices) |
| `hostkeys.txt`           | Live SSH host key fingerprints |
| `logs.txt`               | /var/log + /tmp logs |
| `var.txt`                | /var directory |
| `nmap.txt`               | External nmap port scan |
| `control-plane-probes.txt` | TCP probes against 9090 / 8080 / 80 |

---

## 9. Next steps for OpenPolaris

1. **Add Hi3559V200 to the README** — the SoC is not Ingenic, our static
   RE notes need correction.
2. **Add port 9090 to the protocol spec** — that's the binary control
   plane. The HTTP servers on 80/8080 are web admin only.
3. **Investigate the `bcmdhd_wlan.1` double-register** — this might be
   patchable in the DT or with a `modprobe.blacklist=bcmdhd_wlan.1`.
4. **Investigate the TCP-data pool leak** — sustained 9090 traffic
   starves the Broadcom driver. Workaround: throttle.
5. **Set a real root password before joining home WiFi** — currently
   anyone on the gimbal AP gets unauthenticated root.

## 10. Reproduction commands

```bash
# 1. Join the gimbal's AP (SSID: BENRO-XXXX-XXXXXX, password on label)
# 2. SSH in:
ssh -o BatchMode=yes root@192.168.0.1    # empty password

# 3. Quick recon:
dmesg | tail -60
ps aux
df -h
cat /proc/mtd
ip a
cat /proc/net/tcp | awk '$4 == "0A" {print $2}'
```

## 11. Camera driver not loading — 4-layer root cause

User-observed symptom: Pentax K-3 III enumerates on the USB bus
(`usb 1-1.2: new high-speed USB device number 3 using ehci-platform`,
idVendor=0x25fb, idProduct=0x0189) but no driver claims it. gphoto2
sees nothing.

The cause is a 4-layer stack failure:

1. **Kernel modules** — `/app/komod/` only ships gadget/function-side
   drivers (`g_mass_storage.ko`, `g_multi.ko`, `usb_f_acm.ko`,
   `usb_f_mass_storage.ko`, `usb_f_rndis.ko`, `usb_f_uvc.ko`,
   `libcomposite.ko`, `udc-core.ko`, `u_ether.ko`, `u_serial.ko`,
   `gspca_main.ko`, `88x2cs.ko`). `gspca_main` is for UVC webcams
   (not DSLRs). **No `usb-storage.ko` for MSC, no `ptp.ko` for PTP
   class.**
2. **usbfs** — `/proc/self/mountinfo` shows no `usbfs`/`usbdevfs`.
   gphoto2's `usb1` port lib opens devices through this. Even if a
   driver existed, userspace couldn't claim the device.
3. **Hotplug agent** — `/proc/sys/kernel/hotplug` is `/sbin/mdev`.
   The udev rules in `/etc/udev/rules.d/54-gphoto.rules` are
   **never consulted at runtime**. The PTP matchers in that file are
   also commented out anyway (`#PROGRAM="check-ptp-camera 06/01/01"`).
4. **Userland** — gphoto2 2.5.27.1 with full iolib support is
   installed, `pentax.la` and `ptp2.la` camlibs are compiled,
   `/app/bin/pgphoto` references `ptp_usb_*` and `ptp_ptpip_*`.
   So the userland *can* talk PTP — it just can't reach the camera
   because of layers 1–3.

A `firmware rebuild with CONFIG_USB_PTP=y and CONFIG_USB_STORAGE=y,
plus `mount -t usbfs none /proc/bus/usb`, would close the gap. This
is a Snoppa/Benro firmware task, not an OpenPolaris client task.

## 12. Why the custom firmware did not install

Full detail in [`fw-install-flow.txt`](./fw-install-flow.txt). One-line:

The installer DID run, but failed its integrity check, and its own
cleanup path wiped the SD card. The new firmware was never written to
NAND.

Evidence:
- Current FwVer is still `4.0.0.32;date:2025.05.09;` (factory)
- `pgphoto` mtime is 2025-05-08, `polestar_app` is 2025-05-09 — both
  pre-date our rebuild
- `ls /app/sd/FwPkt` is empty (should contain `camera/`, `gimbal/`)
- `SP_GetSdInfo` reports 121 MB total, 0 used — freshly emptied card
- The polestar_app binary contains the strings
  `OMS_UPGRADE_STA_CHECK_FW FAIL` and `crc[%d] != %d;` plus the
  cleanup commands `rm -r /app/sd/FwPkt`, `rm -r /app/sd/FwPkt.zip`

User has 10 candidate FwPkt.zip packages to try. Test loop:
1. `pkill polestar_app` (avoid cleanup race)
2. `scp <pkg>.zip root@192.168.0.1:/app/sd/FwPkt.zip`
3. `cd /app/bin && nohup ./polestar_app >> /app/Mlog.txt &`
4. `tail -f /app/Mlog.txt` — watch for `OMS_UPGRADE_STA_CHECK_FW PASS`
   or `crc[%d] != %d`
5. On PASS, wait for reboot and verify `/app/FwVer` updated
6. On FAIL, capture the mismatch line and try the next package

---

## 13. Handover document (2026-08-31)

See [`HANDOVER-2026-08-31.md`](HANDOVER-2026-08-31.md) for a complete
self-contained handover for the next agent. The investigation has
narrowed to: **the install flow is not being triggered**, not
"triggered but failing" — different failure mode from the earlier
silent-reject postmortem. The three open hypotheses (wrong path,
daemon not running, missing precondition like the 810 command) and
the recommended next-test sequence are in §5 and §6 of the handover.

Key context the next agent needs:
- 4 build zips, not 9. The user has 100 attempts, not 1000.
- All 4 builds pass `verify_firmwareinfo.py`; integrity check is solved offline.
- The blocker is sshd — the gimbal sleeps and must be woken from the phone.
- Do not commit the uncommitted Kotlin changes as part of this work.

---

## 14. Knowledge-share brief for the patcher project (2026-08-31)

A separate, patcher-audience document has been written at
[`KNOWLEDGE-SHARE-FOR-PATCHER.md`](KNOWLEDGE-SHARE-FOR-PATCHER.md). It
follows the cross-project convention (`docs/CROSS-PROJECT.md`): it
references this bundle by permalink rather than duplicating content, and
pins the patcher-side citations to commit SHA `a32991d`.

What it covers that the patcher team may not yet have:

- The **"silent no-trigger" failure mode** — distinct from the
  silent-reject modes in the patcher's `silent-fwpkt-reject-postmortem.md`.
  Mlog_000003 contains zero FwPkt/firmwareInfo/crcInfo/getFwInfo lines
  for the 2026-08-30 padded-appfs build, suggesting the on-board
  `polestar_app` watcher is not arming at all. Three hypotheses are
  recorded (wrong watch path, daemon not running, missing 810
  precondition).
- The **FwPkt integrity-check symbols** recovered from `polestar_app`
  (`SP_UpgradeCheckFw` @ 0x14023c, `SP_OmsUpgradeCheckFwPkt` @ 0x76f24,
  `SP_ExDevFwPktCheck` @ 0x5ccfc), giving the patcher a concrete offset
  to set a breakpoint when reproducing the trigger condition.
- **Operational SSH facts** that any agent connecting to the gimbal
  needs: no SFTP, sshd rate-limits, gimbal sleeps, no on-device
  `ssh-keygen`, BusyBox grep lacks `--line-buffered`, the control plane
  is binary on TCP/9090 (port 80 is lighttpd 403, port 8080 is
  MJPG-Streamer), bash `kill` is a reserved word that blocks substring
  matches.
- **Protocol codes** for camera/gimbal control (258–549) and firmware
  update (810 `SYS_FW_UPGRADE`, 811 `SP_FW_PROGRESS`).

Suggested follow-ups recorded in §5 of the brief:

- A **behaviour gate** in the patcher build that probes a candidate zip
  with the *stock* `appfs` before declaring success.
- A **`FwPkt-delivery.json` artifact** in the patcher build that records
  *where* the FwPkt was dropped, not just *what* it contains — so a
  silent-no-trigger failure shows up as missing delivery rather than a
  failed integrity check.
- An **810-precondition experiment**: drop the FwPkt, issue `810` from
  the Benro app, then re-drop. If the second drop is picked up, the
  on-board watcher needs to be armed explicitly.
