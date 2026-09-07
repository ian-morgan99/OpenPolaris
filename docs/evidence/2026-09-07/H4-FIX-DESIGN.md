# H4 fix design — making `.stub.bak` and `.stock.bak` real rollback targets

**Date**: 2026-09-07 14:30
**Author**: this agent (OpenPolaris session)
**Subject**: User asked "how do we fix H4". This is the design doc.
**Status**: ready to execute, awaiting user approval.

## The current state (H4)

After the deploy-fix.sh x86_64-overwrite incident, the two
"backup" files at `/app/lib/stage2/` are byte-identical to the
files they claim to back up. So the rollback path
`cp .stub.bak libgphoto2_port.so.12` is a no-op.

```
/app/lib/stage2/libgphoto2_port.so.12          sha b6f7e58e...  38,620 B  (deployed)
/app/lib/stage2/libgphoto2_port.so.12.stub.bak  sha b6f7e58e...  38,620 B  (== deployed, useless)
/app/lib/stage2/libgphoto2_port/0.12.2/usb1.so sha 4d4bfe48...  30,260 B  (deployed)
/app/lib/stage2/libgphoto2_port/0.12.2/usb1.so.stock.bak sha 4d4bfe48...  30,260 B  (== deployed, useless)
```

## The real pristine targets

Both pristine targets are still on the device and were never
modified:

```
/app/lib/libgphoto2_port.so.12       sha 6fca483d... 105,852 B  (stock 2.5.27-era, Apr 2021)
/app/lib/libgphoto2_port/0.12.0/usb1.so sha 4d4bfe48... 30,260 B  (stock 0.12.0, Apr 2021)
```

**Important finding**: the stock 0.12.0 `usb1.so` and the freshly-built
0.12.2 `usb1.so` are **byte-identical** (both sha `4d4bfe48...`).
This is good news: the stock usb1.so is a real rollback target
without modification.

The stock 2.5.27-era `libgphoto2_port.so.12` (105,852 B, sha
`6fca483d...`) IS a different lib from the freshly-built 2.5.34
(38,620 B, sha `b6f7e58e...`) — as expected. The 2.5.27 stock is
a real, distinct, working fallback. (It lacks LIBGPHOTO2_5_0
symbols, so the freshly-built 2.5.34 core won't load it — but if
the user's flash strategy ever wants to roll ALL THE WAY BACK to
the stock firmware's iolib stack, this is the right file.)

## The fix (one option, ready to run)

```sh
# On the gimbal, with a single command:
ssh root@192.168.0.1 '
  set -e
  # Make .stub.bak the real pristine 2.5.27-era libgphoto2_port
  cp /app/lib/libgphoto2_port.so.12 /app/lib/stage2/libgphoto2_port.so.12.stub.bak
  chmod 644 /app/lib/stage2/libgphoto2_port.so.12.stub.bak
  echo "  stub.bak: $(sha256sum /app/lib/stage2/libgphoto2_port.so.12.stub.bak)"
  # .stock.bak is already a real pristine (== deployed, byte-identical to stock 0.12.0 usb1.so)
  echo "  stock.bak: $(sha256sum /app/lib/stage2/libgphoto2_port/0.12.2/usb1.so.stock.bak)"
  echo "  deployed:  $(sha256sum /app/lib/stage2/libgphoto2_port.so.12)"
  echo "  deployed:  $(sha256sum /app/lib/stage2/libgphoto2_port/0.12.2/usb1.so)"
'
```

**Resulting state** (after the fix):

```
/app/lib/stage2/libgphoto2_port.so.12          sha b6f7e58e...  38,620 B  (deployed, 2.5.34 stripped)
/app/lib/stage2/libgphoto2_port.so.12.stub.bak  sha 6fca483d... 105,852 B  (stock 2.5.27 — REAL ROLLBACK TARGET)
/app/lib/stage2/libgphoto2_port/0.12.2/usb1.so sha 4d4bfe48...  30,260 B  (deployed, 0.12.2)
/app/lib/stage2/libgphoto2_port/0.12.2/usb1.so.stock.bak sha 4d4bfe48... 30,260 B  (stock 0.12.0 — same as deployed, still a valid fallback)
```

Now the rollback path `cp .stub.bak libgphoto2_port.so.12` does
something real: it rolls back the freshly-built 2.5.34 port to
the stock 2.5.27 port that shipped with the gimbal.

## Why this is the right fix

- **No firmware flash** — just a `cp` from a file that's already
  on the device, untouched
- **No risk** — the source file is in `/app/lib/` (the stock
  library directory), which my deploy-fix.sh never touched
  (only `/app/lib/stage2/` was touched)
- **Real rollback target** — the 2.5.27 stock libgphoto2_port is
  known-good on the gimbal, since it's what shipped with the
  original firmware
- **Reversible** — if the user later wants to remove the backups,
  just `rm .stub.bak .stock.bak`
- **Restores the patcher's design intent** — the patcher's
  `install_stage2.sh` script does the equivalent
  (`cp "$BINP" "$BACKUP"` before the swap), and the patched
  image creates `.prestage2.bak` files at the stock camlib/iolib
  paths. This is the "we did it via SSH instead of firmware"
  version of that same design

## What this fix does NOT do

- Does NOT fix patcher#38 (the libgphoto2_port iolibs-lookup
  defect). That's a separate problem and a separate fix.
- Does NOT fix patcher#39 (`/app/bin/` empty). Also separate.
- Does NOT restore the 3 deleted Pentax JPEGs (H1, irrecoverable).
- Does NOT change the deployed libgphoto2_port — the
  freshly-built 2.5.34 stays in place.

## What needs the user's approval

The fix is **5 cp operations on the gimbal**, no firmware change,
no other file touched. It does require one SSH `cp` command, which
is a write operation. The "fixes must go via firmware packet" rule
strictly interpreted says "no SSH file writes ever", but the
rule's purpose was to avoid the kind of damage that the
deploy-fix.sh did (x86_64 overwrites). This fix:
- Writes a file to the **same directory** as the file it's backing
  up (not crossing subsystems)
- Writes content that already exists on the device (stock
  libgphoto2_port.so.12 has been at the same path since
  2021-04-24)
- Is **reversible** by `rm`ing the file
- Does NOT change the running state (no restart needed)
- Does NOT touch the freshly-built libs

I am NOT executing this fix without explicit user approval,
because the user's rule is "no SSH file writes without firmware
packet". The fix is described in detail above; if the user
approves, I run the command and verify with sha256sum.

## Alternative: do nothing, wait for firmware flash

If the user prefers the canonical fix path:
- Future FwPkt.zip install (when patcher#38 and #39 are fixed)
  will run `install_stage2.sh` which creates
  `/app/sd/pgphoto.prestage2.bak` and
  `/app/lib/libgphoto2/*/ptp2.so.prestage2.bak` — the patcher's
  designed backup mechanism.
- That firmware-install path also puts the fresh libs at the
  right places and populates `/app/bin/pgphoto`.

Until that happens, H4 is mitigated by this fix or by a
firmware flash. The user gets to choose.
