# H4 fix — executed 2026-09-07 14:25

**Status**: COMPLETE.
**User approval**: granted (user said "1" = execute option 1 from
the design doc).

## The single command that was run

```sh
ssh root@192.168.0.1 '
  cp /app/lib/libgphoto2_port.so.12 \
     /app/lib/stage2/libgphoto2_port.so.12.stub.bak
  chmod 644 /app/lib/stage2/libgphoto2_port.so.12.stub.bak
'
```

That's it. No other files touched, no restarts, no process
changes.

## Verification: sha256sum before/after

| File | Before | After | Notes |
|---|---|---|---|
| `.stub.bak` | `b6f7e58e...` (38,620 B, == deployed) | `6fca483d...` (105,852 B) | **NOW a real rollback target** |
| deployed | `b6f7e58e...` (38,620 B) | `b6f7e58e...` (38,620 B) | unchanged |
| pristine (stock 2.5.27) | `6fca483d...` (105,852 B) | `6fca483d...` (105,852 B) | unchanged source |
| `.stock.bak` | `4d4bfe48...` (30,260 B) | `4d4bfe48...` (30,260 B) | unchanged — already == stock 0.12.0 usb1.so |
| deployed usb1.so | `4d4bfe48...` (30,260 B) | `4d4bfe48...` (30,260 B) | unchanged |

## ls -la after the fix

```
-rwxr-xr-x    1 root     root         38620 Sep  7 13:17 /app/lib/stage2/libgphoto2_port.so.12
-rw-r--r--    1 root     root        105852 Sep  7 14:25 /app/lib/stage2/libgphoto2_port.so.12.stub.bak
-rwxr-xr-x    1 root     root         30260 Sep  7 13:17 /app/lib/stage2/libgphoto2_port/0.12.2/usb1.so
-rwxr-xr-x    1 root     root         30260 Sep  7 13:15 /app/lib/stage2/libgphoto2_port/0.12.2/usb1.so.stock.bak
```

## What this enables

```sh
# Real rollback: from the freshly-built 2.5.34 port to the stock 2.5.27 port
ssh root@192.168.0.1 'cp /app/lib/stage2/libgphoto2_port.so.12.stub.bak \
                          /app/lib/stage2/libgphoto2_port.so.12'
# Then restart pgphoto to load the stock port
```

Or combined with the patcher's designed rollback path
(`/app/sd/pgphoto.prestage2.bak` and per-file
`.prestage2.bak` at the stock camlib/iolib paths), the device
now has a working 2-stage rollback:
1. `.stub.bak` / `.stock.bak` — quick local rollback
2. Patcher-designed `.prestage2.bak` — full firmware-flash-time
   rollback

## What this does NOT do

- Does NOT fix patcher#38 (the libgphoto2_port iolibs-lookup
  defect). Separate problem.
- Does NOT fix patcher#39 (empty `/app/bin/`). Separate.
- Does NOT restore the 3 deleted Pentax JPEGs (H1, irrecoverable).
- Does NOT change the deployed libgphoto2_port — the
  freshly-built 2.5.34 stays in place.
- Does NOT require a restart — the file is on disk; if pgphoto
  is running, it will continue with the 2.5.34 lib loaded in
  memory until restarted.
