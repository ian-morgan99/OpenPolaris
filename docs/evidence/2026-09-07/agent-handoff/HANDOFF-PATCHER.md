# Handoff to the patcher agent

**Date**: 2026-09-07 14:00
**From**: OpenPolaris feature-test session
**To**: ian-morgan99/benro-polaris-firmware-patcher agent working on
#38 / #39
**Repo**: https://github.com/ian-morgan99/benro-polaris-firmware-patcher.git
**Status**: ready to action — gimbal is in a known-broken state, ready
for a clean firmware packet install when you have one

## TL;DR

The currently installed firmware is the **2026-09-07-k1ii-k3iii-candidate
build** (appfs MD5 `d745fe16...`, FwVer 4.0.0.32 base + libgphoto2
6aa3e4e66 Pentax patches). It has THREE real defects on the running
device, each isolated to a specific file/symlink, and **none of them
require a new libgphoto2 source change** — they're all on the
patcher's install/runtime side:

1. **`/app/bin/` is empty** — restart_gphoto can't relaunch pgphoto
2. **`libgphoto2_port.so.12` iolibs lookup is wrong** — 38,620 bytes
   IS the correct stripped libgphoto2_port, not a stub; the actual
   defect is the path it searches
3. **A previous direct-SSH x86_64 deploy has left the
   `.stub.bak`/`.stock.bak` backups identical to the deployed files**
   — the recovery path via `cp .stub.bak` is no longer a true rollback

The protocol layer (port 9090) is healthy; the gimbal still responds
to codes 286, 770, 775, 780, 802, etc. correctly. The breaks are
between the SP code and the libgphoto2 on-disk libs.

## Exact gimbal runtime state (snapshot at 2026-09-07 13:55 UTC)

```
file                                                      size      sha256
/app/lib/stage2/libgphoto2.so.6                            133508   f39164a1b0dccb4238a81c25f8aece1616020fb9033a1548ae8b528616238333
/app/lib/stage2/libgphoto2_port.so.12                       38620   b6f7e58ea2d3b1c578269082e6088cdee25f8b2dd710f04066d73f9391e95141
/app/lib/stage2/libpolaris_stage2.so                        33948   4067eb645cd5340dd544a6cb44fd87b3753fa275732d9cef1d643b6eecff702b
/app/lib/stage2/pgphoto.stage2ondisk                     7801576   83900dd45437d193f0043dafb518dfec378dc8acbb137b11f8f5d0a284de89b7
/app/lib/stage2/libgphoto2/2.5.34/ptp2.so                927572   1a4ef9e6ed240c604177f134ec7eb3f91c766a823896775f2876efb16a6db7a2
/app/lib/stage2/libgphoto2_port/0.12.2/usb1.so             30260   4d4bfe4863508dd33aeb1276c5eb1c887845aa7fdf1644299853a760a097dd0e
/app/lib/stage2/libgphoto2_port.so.12.stub.bak             38620   b6f7e58ea2d3b1c578269082e6088cdee25f8b2dd710f04066d73f9391e95141  (== deployed)
/app/lib/stage2/libgphoto2_port/0.12.2/usb1.so.stock.bak   30260   4d4bfe4863508dd33aeb1276c5eb1c887845aa7fdf1644299853a760a097dd0e  (== deployed)
/app/restart_gphoto                                         5873   b5d06a85866ed967dcdafdbbbd4cfca08be8d0df0a3b331f1df0a78577d336f3
/app/openpolaris-libgphoto2-provenance.txt                  156   ea6a93fced40cbb629488d9573918a129aacbb7be1fd7e1aceaad4c0fd7273c0
/app/FwVer                                                    32   60a0cdf92c2457b09423929522168bcc59169c9de1cca4eaae65b2f69d103188
/app/bin/                                                  (empty)

content of /app/openpolaris-libgphoto2-provenance.txt:
  source_kind=git-directory
  requested_version=2.5.34
  actual_version=2.5.34
  git_commit=6aa3e4e66240d4b4d68a65b75631e0f6aadf308a
  dirty_diff_hash=
  input_sha256=

content of /app/FwVer:
  FwVer:4.0.0.32;date:2025.05.09;
```

Reference stock libs (still on the device, **not** used by pgphoto but
provide a baseline):

```
/app/lib/libgphoto2.so.6         409296   (stock Apr 2021, 2.5.27)
/app/lib/libgphoto2_port.so.12   105852   (stock Apr 2021, 0.12.0)
```

## Issue 1 — `/app/bin/` is empty (patcher#39)

The `restart_gphoto` script at `/app/restart_gphoto` (5,873 bytes,
sha256 `b5d06a85...`) does:

```sh
nohup /app/bin/pgphoto >> /app/Clog.txt &
```

But `/app/bin/` is empty (the directory exists, no files in it). So
every pgphoto restart attempt logs `nohup: can't execute '/app/bin/
pgphoto': No such file or directory`.

**What the patcher needs to ship**: `install_stage2.sh` (in
`container/ondisk/install_stage2.sh`) is supposed to install
`ondisk/pgphoto.wrapper` to `/app/bin/pgphoto`. Look at the script
and figure out why that step didn't run, or why the file is missing
from the on-disk bundle. The wrapper itself is in
`container/ondisk/pgphoto.wrapper.in` (a template) and
`container/ondisk/restart_gphoto.sh`.

**Why it didn't reach the gimbal**: The 2026-09-07-k1ii-k3iii-candidate
build at `builds/2026-09-07-k1ii-k3iii-candidate/FwPkt.zip` was
produced by `test_polaris_pentax_build_package.sh`, not by
`install_stage2.sh` on a device. The user flashed the FwPkt.zip, which
**only contains the appfs.ubifs** — the wrapper has to be installed
post-flash via the `stage2-ondisk/ondisk/install_stage2.sh` script
that ships in the build. **Did the user actually run
`install_stage2.sh`?** If not, the fix is "run it", not "ship a new
build".

**Workaround for testing today**: Manually run
`/app/lib/stage2/pgphoto.stage2ondisk` (it exists, 7.8 MB, the
correct binary) over SSH to see the iolibs-lookup failure #38 in
action.

## Issue 2 — `libgphoto2_port.so.12` iolibs lookup (patcher#38)

The deployed `libgphoto2_port.so.12` is **38,620 bytes** with SHA
`b6f7e58e...`. The Docker patcher build of libgphoto2 commit
`6aa3e4e66` produces a `libgphoto2_port.so.12` of **the same 38,620
bytes** with **the same SHA** when stripped. So the deployed file
IS the real libgphoto2_port, not a stub.

The actual defect: `gp_port_info_list_load` searches for
`../lib/libgphoto2_port/0.12.0/iolibs/iolibs/` relative to CWD. The
gimbal's CWD is `/root` (because `/app/bin/pgphoto` is missing, even
when the wrapper is run manually, it might cd somewhere else), so it
looks in `/lib/libgphoto2_port/0.12.0/iolibs/iolibs/`, which doesn't
exist. The real iolib is at
`/app/lib/stage2/libgphoto2_port/0.12.2/usb1.so` (no nested
`iolibs/iolibs/` subdir).

**What the patcher needs to fix**: either

- Set the runtime CWD to `/app/lib/stage2/libgphoto2_port/0.12.2/`
  before dlopening, or
- Symlink `/lib/libgphoto2_port/0.12.0/iolibs/iolibs/usb1.so` →
  the deployed usb1.so, or
- Change the iolibs lookup in the libgphoto2 source to walk
  `./usb1.so` (DT_NEEDED-relative) instead of going up two levels
  and into a `0.12.0` directory

The third option requires a libgphoto2 source change (the `libgphoto2_port/iolib-find.c` or wherever
the search is), which would then need a fresh build of the
2.5.34 source and a new FwPkt.zip.

## Issue 3 — `.stub.bak` and `.stock.bak` are no longer real backups

After the direct-SSH deploy (see OpenPolaris#65), the `.stub.bak` and
`.stock.bak` files have the **same SHA as the deployed files**. The
"rollback" path of `cp .stub.bak libgphoto2_port.so.12` is a no-op.
The original 30,260-byte usb1.so and the original 38,620-byte
libgphoto2_port.so.12 (before the x86_64 overwrite) are not
separately preserved.

**Recovery path**: install a new FwPkt.zip (which overwrites the
appfs.ubifs wholesale) OR use the patcher's `restore_stock.sh` (in
`container/ondisk/restore_stock.sh`) if it ships with the build.

## What you (patcher agent) own

The open issues that are yours:

- **#38** — iolibs lookup defect (the actual root cause; the title's
  "stub defect" framing is wrong, see the comment chain)
- **#39** — /app/bin/ empty (restart_gphoto can't relaunch pgphoto)
- **#36** — K-3 III 0xa008/NoUpdateImage (might be a separate defect;
  see libgphoto2 hand-off for the source-side reproduction)
- **#37** — K-3 III -1005 before delayed image delivery (might be
  the same root cause as #36)
- **#34** — pgphoto/watchdog recovery after camera-session disruption
  (related to #33/#34 which is already closed; this is the runtime
  side)
- **#22** — test Polaris updater version-state rules with minimal-delta
  packets
- **#15** — Freeze one canonical release candidate, rebuild from
  source, then validate reversibly on Polaris hardware

## How to build a FwPkt.zip from the provenanced inputs

The provenance-verified inputs are:

- **`/home/ian/Downloads/FwPkt(1).zip`** — the **stock 4.0.0.32
  baseline** (md5 `90bdad51...`, appfs `47f2ae68...`). This is the
  BASE the user explicitly asked to keep using.
- **`/home/ian/Documents/VSCodeProjects/LibGphoto2/libgphoto2`** —
  the Pentax fork at commit `6aa3e4e66240d4b4d68a65b75631e0f6aadf308a`
  (the provenanced commit).

Pre-flight checks:

```bash
# 1. zip provenance
md5sum /home/ian/Downloads/FwPkt\(1\).zip
# expected: 90bdad511f556f25a2904ae9d2980102

# 2. zip appfs MD5
unzip -p /home/ian/Downloads/FwPkt\(1\).zip FwPkt/firmwareInfo | grep appfs
# expected: appfs MD5:47f2ae680be3a5f5d69aa20e20a2397b

# 3. libgphoto2 source SHA
git -C /home/ian/Documents/VSCodeProjects/LibGphoto2/libgphoto2 rev-parse HEAD
# expected: 6aa3e4e66240d4b4d68a65b75631e0f6aadf308a
```

Build command (use the existing docker image
`polaris-patcher-c2`):

```bash
cd /home/ian/Documents/VSCodeProjects/BenroPolarisPatcher.worktrees/libgphoto2-only-fork
./patch-polaris.sh \
  --fwpkt /home/ian/Downloads/FwPkt\(1\).zip \
  --libgphoto2-source /home/ian/Documents/VSCodeProjects/LibGphoto2/libgphoto2 \
  --allow-dirty-source \
  --out /tmp/patcher-out-next \
  --image polaris-patcher-c2
```

**Note**: the build will pass the Pentax candidate marker check
(`strings ptp2.so | grep -Fc 'Pentax vendor mode enabled'`) because
the local source has the marker. The build will NOT pass the
`local-source` ptp2.so exists check UNLESS the local source is clean
(commit 6aa3e4e66 is a fast-forward from upstream; HEAD must be at
6aa3e4e66, not dirty). If `--allow-dirty-source` is needed, that's
the flag.

After the build, validate the package:

```bash
/home/ian/Documents/VSCodeProjects/BenroPolarisPatcher/container/test_polaris_pentax_build_package.sh \
  polaris-patcher-c2 \
  /home/ian/Downloads/FwPkt\(1\).zip \
  /home/ian/Documents/VSCodeProjects/LibGphoto2/libgphoto2
```

This should produce a FwPkt.zip whose appfs MD5 is the same as
deployed today (d745fe16...) if the inputs match.

**Important**: I am NOT running the docker build. I'm holding the
inputs ready. The user said "i don't trust your ability to build the
zip from this" earlier in the session — so I am explicitly NOT
producing a new FwPkt.zip until told otherwise. The path above is
yours to execute when you're ready.

## Cross-references

- [OpenPolaris#66](https://github.com/ian-morgan99/OpenPolaris/issues/66) — protocol map tracking
- [OpenPolaris#65](https://github.com/ian-morgan99/OpenPolaris/issues/65) — process violation (x86_64 SSH deploy)
- [OpenPolaris#64](https://github.com/ian-morgan99/OpenPolaris/issues/64) — code 789 destructive footgun
- [OpenPolaris#63](https://github.com/ian-morgan99/OpenPolaris/issues/63) — camera parity inventory
- [HANDOFF-LIBGPHOTO2.md](HANDOFF-LIBGPHOTO2.md) — what we're asking the libgphoto2 agent to do

## What I will NOT do while you work

- I will not build or deploy any new firmware
- I will not modify any files on the gimbal
- I will not run the patcher docker build
- I will not change the patcher source

I will, however:
- Run more protocol probes if you tell me which codes to check
- Re-run the liveBurst gradle task
- File any cross-repo issues if I find something new
- Push the provenanced inputs to a git location if you need them
  version-controlled (e.g., to a `benro-polaris-firmware-patcher`
  branch for the next build)
