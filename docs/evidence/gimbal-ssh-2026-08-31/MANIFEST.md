# Evidence Bundle Manifest — `gimbal-ssh-2026-08-31/`

This document records what is in this evidence bundle, how it was captured,
what was redacted before commit, and the SHA-256 of every file in its
current (post-redaction) state.

> See [`README.md`](README.md) for the substantive findings,
> [`HANDOVER-2026-08-31.md`](HANDOVER-2026-08-31.md) for the open FwPkt
> install-rejection investigation, and
> [`KNOWLEDGE-SHARE-FOR-PATCHER.md`](KNOWLEDGE-SHARE-FOR-PATCHER.md) for
> a brief written for the [`BenroPolarisPatcher`](https://github.com/ian-morgan99/BenroPolarisPatcher)
> team covering the new "silent no-trigger" failure mode, the FwPkt
> integrity-check symbols recovered from `polestar_app`, and the
> operational SSH/control-plane facts observed in this capture.

## Capture

| Field | Value |
|---|---|
| Date | 2026-08-31 (single capture session) |
| Device | One Benro Polaris gimbal unit |
| Gimbal MAC (device under test) | `48:e7:da:d4:b5:73` |
| Gimbal IP (own AP) | `192.168.0.1` |
| Firmware running at capture time | `FwVer:4.0.0.32;date:2025.05.09;` (factory build — see `fw-install-flow.txt`) |
| SoC | HiSilicon Hi3559V200 (ARMv7 dual-core, 1.5 GiB RAM) |
| Access | SSH to `root@192.168.0.1` with empty password, consistent with `PermitRootLogin yes` / `PermitEmptyPasswords yes` in `sshd_config.txt` |
| Network topology note | The gimbal's own AP serves `192.168.0.0/24`; the router the user normally sees at `192.168.0.1` is on a different subnet (Hitron cable modem, see `docs/CONNECTIVITY-FINDINGS-2026-08-31.md`). |

## Source commands

All files in this directory are the output of one SSH session into the
gimbal. The typical capture command for each was:

```bash
ssh root@192.168.0.1 '<capture-command>' > <output-file>
```

Where `<capture-command>` was one of:

| File | Capture command(s) |
|---|---|
| `dmesg.txt` | `dmesg` |
| `ps.txt` | `ps auxf` |
| `ip.txt` | `ip addr; ip route` |
| `listening.txt` / `nmap.txt` | `netstat -tulpn` then external `nmap` from the dev box |
| `mounts.txt` | `mount; cat /proc/mounts` |
| `df.txt` | `df -h` |
| `cpuinfo.txt`, `meminfo.txt`, `uname.txt`, `version.txt` | standard `/proc` and `uname -a` |
| `dev.txt`, `usb.txt`, `mtd.txt` | `cat /proc/...` enumerations |
| `app-tree.txt` | `find /app -maxdepth 3 -type f \| head -200` |
| `app-bin.txt` | `ls -la /app/bin/` |
| `app-conf.txt` | `cat /app/conf/*` |
| `var.txt` | `ls -la /var/; cat /var/log/messages 2>/dev/null` |
| `logs.txt` | `dmesg \| grep -i error; cat /var/log/* 2>/dev/null` |
| `etc.txt` | `ls -la /etc/` |
| `etc-passwd.txt` | `cat /etc/passwd /etc/group /etc/shadow` (see redactions) |
| `sshd_config.txt` | `cat /etc/ssh/sshd_config` |
| `hostkeys.txt` | `ssh-keygen -lf /etc/ssh/ssh_host_*_key.pub` (failed — see file) |
| `control-plane-probes.txt` | external TCP probes to `192.168.0.1:80/8080/9090` from the dev box |
| `fw-install-flow.txt` | `cat /app/FwVer; cat /app/getFwInfo.sh; strings /app/bin/polestar_app \| grep -E 'FwPkt\|FwVer\|UPGRADE\|crc\|md5\|format\|remove' \| head -50` |
| `README.md` | written by the investigator after the capture (see top of file) |
| `HANDOVER-2026-08-31.md` | written by the investigator as a session handover (see top of file) |
| `KNOWLEDGE-SHARE-FOR-PATCHER.md` | written by the investigator as a knowledge-share brief for the `BenroPolarisPatcher` project — references this bundle by permalink, does not duplicate it (see top of file) |

## Redactions applied before commit

The following redactions were applied to the raw capture outputs before
they were committed to this public repository. They are the only
sensitive-content handling this bundle applies.

| File | Redaction | Reason |
|---|---|---|
| `etc-passwd.txt` | All `/etc/passwd` password-hash fields (the second `:`-delimited field) replaced with `[REDACTED]`. | A leaked `$6$` (SHA-512) hash, even one of an empty account, is sufficient for offline dictionary attack if the password is short or guessable. |
| `etc-passwd.txt` | The entire `/etc/shadow` block was **removed** (replaced with an explanatory comment block). | A single root hash is the highest-sensitivity item; we do not commit any of the shadow content. |
| `dmesg.txt` | Two client-device MACs (`a6:cf:f9:ea:ea:49`, `4c:23:38:c8:7b:6f`) replaced with `[REDACTED:client-mac]`. | These are user devices (phones/PCs) that connected to the gimbal's AP — they leak the user's network info and have no analytical value. |
| `dmesg.txt` | The gimbal's own MAC `48:e7:da:d4:b5:73` is **retained** (it appears in many lines). | It is the device-under-test identifier; researchers correlating findings to this specific Polaris need it. |
| `probes-20260901-112625/01-first-look.txt`, `live-probe-2026-09-01-11-26/01-first-look.txt` | Same client-MAC replacement applied to the dmesg-equivalent content these probe bundles re-emit (`4c:23:38:c8:7b:6f` → `[REDACTED:client-mac]`). | The probe subdirectory of this bundle and the sibling `live-probe-2026-09-01-11-26/` bundle were captured from the same gimbal and contain the same dmesg output, so the same redaction had to be applied to keep the lint coverage honest. The original `dmesg.txt` redaction entry above was the seed; the audit (`2026-09-01`) found the two probe files were missed at the original redaction pass. |
| `hostkeys.txt` | Replaced `sh: ssh-keygen: not found` (4×) with an explanatory note. | No actual key material leaked (the gimbal's PATH did not include `ssh-keygen`), but the file is now a clear negative result rather than four cryptic errors. |
| `README.md`, `HANDOVER-2026-08-31.md` | Removed `polaris-re-results.md` link that pointed into a per-machine `.copilot/session-state/...` path. Removed two absolute `/home/ian/...` build-tree references. Narrowed "authoritative ground truth" / "entire firmware-analysis pipeline" over-claims to "observed evidence from this device/firmware" and itemised which static RE predictions were checked. | The bundle should not leak user filesystem layout, link into a per-machine session state, or make over-broad claims that the evidence does not support. |

The redaction list in this table is **exhaustive**: no other content was
modified. If a future reader finds an additional pattern in this bundle
that should have been redacted, please file an issue.

## What this bundle does NOT contain

- The captured firmware binaries (`rootfs.ubifs`, `appfs.ubifs`,
  `uImage`, `u-boot.bin`, `factoryParam`) — too large and not needed for
  the questions investigated.
- The factory `FwPkt.zip` — kept only on the capture host.
- Any SSH private keys (none were ever present; `ssh-keygen` was not on
  the gimbal's PATH so we did not generate them either).
- Any pre-shared Wi-Fi passwords (`/etc/hostapd.conf` was empty in the
  capture — see `app-conf.txt`).
- The literal value of the root password hash (redacted; see above).

## SHA-256 of every file in this bundle (post-redaction)

These hashes describe the bundle as committed. Any change to a file
in this directory (including this manifest) will change its hash.

This block was last refreshed `2026-09-01` after the audit pass for
issue #29 found that two probe files (`probes-20260901-112625/01-first-look.txt`
and the sibling `live-probe-2026-09-01-11-26/01-first-look.txt`) carried
the same un-redacted client MAC as the original `dmesg.txt`, and that
two helper shell scripts (`post-fw-update-probe.sh`, `wake-and-probe.sh`)
plus the probe subdirectory were not previously included in the hash
list at all. The same audit also refreshed the hashes for files whose
content drifted from the original capture (e.g. `HANDOVER-2026-08-31.md`,
`README.md`) — those drifts are non-sensitive (narrative/text edits) and
do not appear in the redactions table.

Re-refresh `2026-09-15`: removed a residual over-claim ("The static RE
pipeline is **production-grade** for this device.") from `README.md`
§7 and replaced it with a scope-narrowing statement ("This capture
confirms the static RE predictions on this specific device/firmware;
broader claims about the entire fleet are deliberately out of scope.").
The CI evidence-bundle lint was extended to catch this phrase in
future (and a negative regression workflow was updated to prove the
extension works). The MANIFEST itself was updated to record the
refresh, so its own hash also changes (see the new value below).

```
017a8aabf200fd9de4a96a46e779784ec9267313e052be471a399efe9dff244d  etc-passwd.txt
11bdc08c7a9820805f2a8a512e7e96e6056a0c8cbb9337d3280161fd834c5089  control-plane-probes.txt
13847dc52f1924659c006edc620d6b1fbab25e4d81faa67997ac8ec748d023ee  dmesg.txt
13b754bca11e028fa8bb129d147079a16625beabe07e7ee31baf6bd85d749db7  HANDOVER-2026-08-31.md
19a552096681ec57a3c83245bee05abe6a515178ecd71e04e047122736afc37d  meminfo.txt
2130fb8bce4d6accc375dcca63852cfeedccb01b23e5c8d7daab41df0cf1f1dd  probes-20260901-112625/02-cmdline.txt
3476473d66aa9f08fe11e32a2b0c39ba1219e6bc13dc73921b1b8f036bac46ad  app-conf.txt
35c01c7f2dfaecc0367a4fa85b70093626e7cdabb69e8326945abcdefb778cab  app-bin.txt
40824e5b5667f889ccf8628ff468362cee87c1fc8bceca4f402435500e5eb20d  wake-and-probe.sh
4d0c7b3ad90fa5dd90ecbb931cf868c6a1ef0a914d1b046e2394ccb73131b587  var.txt
53697cceef9303f875dab3304e82be6fb975511f525b34c5b4ad3989c2de1759  ip.txt
54986fb1255ba44fc2d4dc727ab4889e3e15ee65373e14ea3538ffe0e812f249  sshd_config.txt
57a3de9150c5a393a37373d88c1b08a39884b7986f3b2d6f130395da81b90110  dev.txt
91fbbcdd158089fc22bc65e701a89f2f0d76f459e442014930430cea58fac3ab  MANIFEST.md  (sealed 2026-09-01; re-hash after this point would change the file itself — see "Hash-of-the-manifest" note below)
53ac663c1360168ef2b3eea16f241e375dc43e22e69721c2a47ae1d049712f2d  MANIFEST.md  (re-hashed 2026-09-15 after production-grade over-claim fix in README.md; canonical once the next audit pass seals)
59ce4d00e35643e013859a2d0a0aa8f6f0f81bb2a6eeb3df55d4c3aa5b5d03d7  usb.txt
6ed938505a52b17752af96391a2fd7005cb110d474f92421eeeaec325c35524b  probes-20260901-112625/02-version.txt
74500a25669c4f666be23b36c4d6de1fddde4e9b55d4623c9f6ff7df114fb397  probes-20260901-112625/01-first-look.txt
7e26e0c67f9dd577fc185a18acc928e96697d657db42b2854a7ed497efeb350f  mounts.txt
846d165ac8d6d528a7b0eb42cb0ab6f04dc7ff09d9d377b3be0b8dc40eb4fd90  nmap.txt
8e7aee75d33e4aed01aa395f17c56a652809e12ad355ef17b8df075b400df9a8  app-tree.txt
649d66dda84c2c62d5741ebe617421c28451093ab001d8754a87e1190a0586a7  README.md
9991fb94f5f628bf5055a014067f9c01e77a6da0a200f86e79255506dc26a4e6  etc.txt
a9f6ef83a13030b71cb065705b8e19b0babb58d2bd0cc7730373dc1513f371a4  uname.txt
acc1d56726e47d255905f7a5bc961a115010352002fe7d9e26029d1b515d8765  hostkeys.txt
c1eab0f256b2799d91549bdbf8a542068ae04c0918d695ecc17c386f902b05ce  mtd.txt
c3bb7f2296e8150b7cfe8c43ae895cf925234263254aa0d6a0c80dd49518bd40  fw-install-flow.txt
cb8c4ea2850cf2afc5ba8a46c0b358c6dd91556aa4654f60882c98f4a5f8c93e  cpuinfo.txt
d4d03f340dc48a749530def35f87931727570f91ae4c7d1cef0110a1d1ff104a  df.txt
e19caeb23a94a738d4116a0e0de695da7e6b88cbb32e0c1b6392acc0d1f19499  KNOWLEDGE-SHARE-FOR-PATCHER.md
e310bd6bba8b6f84d3b15ef369294c972b3b16ae895b4a02a23c18929bb1bb16  logs.txt
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  version.txt
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  probes-20260901-112625/02-os-release.txt
f2ff0982f32aa235301d108f79d0b7bc3ebaf245acd4dbd9cbb4007a453d10ee  post-fw-update-probe.sh
f4f39208f14f81a81707c9ac1c75520e98c7694fd0842a91f68bea8418234ad8  listening.txt
f7fa005411c3214e465035c21fe913a485d2f100b14d90a7ecc55c36b0ff61af  ps.txt
```

The empty `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
for `version.txt` and `probes-20260901-112625/02-os-release.txt` is the
SHA-256 of an empty file (the gimbal's `/app/FwVer` and `/etc/os-release`
were unreadable or empty at capture time; see `fw-install-flow.txt` which
shows the version was read by a different path).

### Hash-of-the-manifest

The line above marked `MANIFEST.md` carries the SHA-256 of this file as
it was **sealed** on `2026-09-01`. Any subsequent edit to this file
(e.g. adding a new over-claim fix) will change its hash, so the line
is, by construction, one revision behind. To re-hash: after the bundle
is otherwise complete and no further edits are pending, compute
`sha256sum MANIFEST.md` once and add the result as a new line. The
line marked `(sealed 2026-09-01; …)` is the historical anchor; the
new line is the current anchor; both can coexist without ambiguity.
This is the only file in the bundle whose hash is allowed to be
re-stated; every other file's hash line is canonical for the commit
that contains it.

## Validation

A CI lint (`.github/workflows/ci.yml` `lint` job) fails the build if any
file under `docs/evidence/` matches:

- `/home/` or `C:\Users\` (absolute local paths)
- `.copilot/session-state/` (per-machine session identifiers)
- `/etc/shadow` (the redaction block is the only allowed occurrence)
- `\$[16y]\$[A-Za-z0-9./]{8,}\$[A-Za-z0-9./]+` (crypt(3) hash format)
- `BEGIN .* PRIVATE KEY` (any private key header)
- `[0-9a-f]{2}(:[0-9a-f]{2}){5}` (MAC addresses — except the gimbal's
  own `48:e7:da:d4:b5:73`, which is in the allowlist)

The lint also fails if any `\.md$` file in this directory contains the
phrases `authoritative ground truth`, `entire firmware-analysis
pipeline`, or `**production-grade**` / `is production-grade` (the
over-claim phrases removed from `README.md` §0 and §7). A negative
regression workflow (`.github/workflows/ci-evidence-lint-regression.yml`)
injects each of these phrases plus four other forbidden patterns
(local paths, session-state links, private-key headers, unlisted MACs)
into `_regression-fixture/` and proves the lint catches every one.
