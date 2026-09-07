# Patcher #39 fix design — /app/bin/ empty

**Date**: 2026-09-07 15:05
**Author**: this agent (OpenPolaris session)
**Subject**: Patcher #39 — `/app/bin/pgphoto` is missing from the
2026-09-07-k1ii-k3iii-candidate build, so `restart_gphoto` cannot
launch pgphoto.

## The problem (recap)

`/app/bin/` on the running gimbal is **empty**:
```
drwxr-x---    2 1000     1000           160 Sep  7 13:44 .
drwxr-xr-x   12 root     root          1688 Sep  7 07:00 ..
```

`/app/restart_gphoto` runs:
```
nohup /app/bin/pgphoto >> /app/Clog.txt &
```

Result:
```
nohup: can't execute '/app/bin/pgphoto': No such file or directory
```

The polestar watchdog `checkGphotoTask` loops calling
`restart_gphoto` and every call fails. Port 8080 is never
bound, so `/preview` is never served.

## The build pipeline's design

The patcher `patch.sh` is supposed to install the wrapper at
`$PG = $APP/bin/pgphoto` (line 410 of `container/patch.sh`):

```bash
install -m "$P_MODE" -o "$P_UID" -g "$P_GID" "$W/pgphoto.wrapper" "$PG"
log "  installed self-driving wrapper -> /app/bin/pgphoto (execs /app/lib/stage2/pgphoto.stage2ondisk)"
```

This is the **last** `install` before the `repack_appfs.sh`
call. The build IS designed to put the wrapper there.

So the bug is **upstream of `install`** — the wrapper file
either isn't being generated correctly, isn't being
`install`-ed, or is being **dropped during repack**.

## Three likely root causes

### Root cause 1: wrapper template substitution failed

`pgphoto.wrapper.in` has these placeholders:
- `@CAMLIBS_VERSION@`
- `@IOLIBS_VERSION@`
- `@PENTAX_MAX_CAPTURE_SIZE@`

The build does:
```bash
sed -e "s|@CAMLIBS_VERSION@|$LIBGPHOTO2_VERSION|g" \
    -e "s|@IOLIBS_VERSION@|$LIBGPHOTO2_PORT_VERSION|g" \
    -e "s|@PENTAX_MAX_CAPTURE_SIZE@|$PENTAX_MAX_CAPTURE_SIZE|g" \
    /opt/patcher/ondisk/pgphoto.wrapper.in > "$W/pgphoto.wrapper"
```

If the sed fails (e.g. an unset env var results in a
substitution error), the wrapper file is empty or partial.
`install` of an empty file is silently OK. Then `repack_appfs`
puts the empty file in appfs.ubifs.

**Symptom to check**: `unzip -p FwPkt.zip FwPkt/camera/appfs.ubifs | strings | grep "stage2" | head`

### Root cause 2: install mode 0 stripped perms

`$P_MODE` is the perms from the **stock** pgphoto. If the
stock pgphoto was `0750` (Benro convention), the wrapper
becomes 0750 — owner-executable only. After install_stage2.sh
later does `chmod +x`, the file becomes executable.

But: in the **repack**, the build does `install -m $P_MODE`
WITHOUT `chmod +x`. So if `$P_MODE = 0750` and the build
host's user is not the stock uid, the file is **not
executable by root on the device** after install.

**Symptom to check**: `ssh root@192.168.0.1 'stat /app/bin/pgphoto'`

### Root cause 3: repack_appfs dropped the file

`repack_appfs.sh` does `mkfs.ubifs -r $TREE`. If `$TREE` is
not `$APP` (the build's modified extracted tree), or if the
build extracted to a different dir than the install wrote to,
the wrapper ends up in the wrong tree and isn't repacked.

**Symptom to check**: the FwPkt's `appfs.ubifs` (extracted
locally with `ubireader_extract_files`) should have
`appfs/ubifs/bin/pgphoto` and the wrapper should be there.

## The fix

The patcher needs a **post-build verification step** that
**fails closed** if the wrapper is missing from the repacked
appfs:

```bash
# Add to container/patch.sh AFTER repack_appfs.sh
log "verifying /app/bin/pgphoto in repacked appfs..."
TMPDIR=$(mktemp -d)
ubireader_extract_files -k -o "$TMPDIR" "$W/out/FwPkt/camera/appfs.ubifs" >/dev/null 2>&1
if [ ! -f "$TMPDIR/ubifs/bin/pgphoto" ]; then
    die "REPACK DROPPED /app/bin/pgphoto — wrapper missing from appfs.ubifs"
fi
if [ ! -x "$TMPDIR/ubifs/bin/pgphoto" ]; then
    chmod +x "$TMPDIR/ubifs/bin/pgphoto"
    log "  fixed: chmod +x on wrapper"
    # Re-repack with corrected perms
    repack_appfs.sh "$STOCK_APPFS" "$TMPDIR/ubifs" "$W/out/FwPkt/camera/appfs.ubifs"
fi
# Also verify the wrapper actually execs the stage2 binary
WRAPPER_LINE=$(grep -E "^exec" "$TMPDIR/ubifs/bin/pgphoto" | head -1)
if [ -z "$WRAPPER_LINE" ]; then
    die "wrapper has no 'exec' line — substitution may have failed"
fi
log "  /app/bin/pgphoto verified: $(stat -c %s "$TMPDIR/ubifs/bin/pgphoto") bytes"
rm -rf "$TMPDIR"
```

This catches all three root causes:
- Root cause 1: substitution failure → wrapper is empty →
  `grep -E "^exec"` finds no exec line → die
- Root cause 2: perm stripping → chmod fixes it
- Root cause 3: repack dropped → die

## Alternative fix (cheaper but more invasive)

Add an **install-time** script (similar to install_stage2.sh)
that creates the wrapper if it's missing on the device:

```bash
# /app/sd/install_pgphoto_wrapper.sh (auto-runs on install)
if [ ! -f /app/bin/pgphoto ]; then
    cat > /app/bin/pgphoto <<'WRAPPER'
#!/bin/sh
D=/app/lib/stage2
export CAMLIBS=$D/libgphoto2/2.5.34
export IOLIBS=$D/libgphoto2_port/0.12.2
export LD_LIBRARY_PATH=$D:/app/lib
export LD_PRELOAD=$D/libpolaris_stage2.so
exec $D/pgphoto.stage2ondisk "$@"
WRAPPER
    chmod +x /app/bin/pgphoto
fi
```

This is what the user's manual recovery on this device did
at 14:20 — it manually exec'd pgphoto.stage2ondisk with the
env vars. The wrapper would be the persistent form.

## Recommended approach

**Use the post-build verification (the first fix).** The
install-time fix is a workaround, not a real fix. The
verification step makes the bug a **build-time** failure,
not a **runtime** failure, which is what the patcher's
existing fail-closed design (see `test_patch_fail_closed.sh`)
requires.

## What this fix does NOT do

- Does NOT fix patcher#38 (the 38KB stub). Separate fix.
- Does NOT replace the actual stock pgphoto binary (which is
  on the device but not in /app/bin).
- Does NOT change the running state. New FwPkt.zip must be
  flashed.

## Acceptance criteria

After flashing a fixed FwPkt.zip on a K-3 III:

1. `unzip -p FwPkt.zip FwPkt/camera/appfs.ubifs | strings |
   grep -E "stage2.*exec"` shows the wrapper exec line.
2. The build verification step passes (no die).
3. After flash: `ls -la /app/bin/pgphoto` shows the file
   exists with mode 0755 (or 0750 with chmod +x done by
   install_stage2.sh).
4. After flash: `/app/restart_gphoto` succeeds and
   `pgphoto.stage2ondisk` is the running process.
5. After flash: `port 8080 is bound`, `/preview` returns
   valid multipart JPEG.

## Time estimate

- Add the post-build verification: 30 min
- Verify it catches the bug on the existing build: 15 min
- Rebuild FwPkt.zip: 1-2 hours
- Flash + verify on K-3 III: 30 min
- Total: ~3 hours, requires physical K-3 III with charged
  battery

## Cross-references

- patcher#39 (this issue)
- patcher#38 (the stub fix) — must be combined with this
- patcher#34 (restart_gphoto failure) — related
- patcher#33 (PID file design) — should be tested too
- docs/evidence/2026-09-07/patcher-38-fix-design/README.md
  — the strip-debug fix
- docs/evidence/2026-09-07/patcher-36-investigation/PLAN-fw-enhancements.md
  — the original plan

## What the patcher agent should do

1. Read this design doc
2. Add the post-build verification to `patch.sh`
3. Run the build on the existing source — it should **die**
   with the message "REPACK DROPPED /app/bin/pgphoto"
4. Find the actual root cause (one of the 3 above)
5. Fix it (sed substitution, chmod, or repack path)
6. Rebuild FwPkt.zip with the same provenance
7. Combine with the patcher#38 fix
8. Hand back to OpenPolaris agent for K-3 III / K-1 II
   qualification
