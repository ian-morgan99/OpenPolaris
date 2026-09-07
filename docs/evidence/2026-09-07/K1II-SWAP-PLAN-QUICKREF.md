# K-1 II swap — quick reference card

**Full plan**: [K1II-SWAP-PLAN.md](K1II-SWAP-PLAN.md)
**Authority**: this plan supersedes any conversation summary
the agent may have read. **Do not deviate from the plan
without writing a follow-up issue first.**

## Decision tree

```
START
  │
  ▼
Are all 8 pre-conditions true? (plan §0)
  ├─ NO  → STOP, write follow-up to OpenPolaris#56
  └─ YES → Claim hardware ownership in LIVE-PROGRESS.md (§1)
            │
            ▼
          Is the gimbal at FwVer 4.0.0.32 with 25fb:0189 visible? (§2)
            ├─ NO  → STOP, file issue
            └─ YES → Power off gimbal, swap camera, power on (§3-4)
                      │
                      ▼
                    Is 25fb:0188 visible after boot? (§5)
                      ├─ NO  → Try warm plug (§5)
                      │         ├─ works → continue
                      │         └─ fails → file new issue, STOP
                      └─ YES → Run §6 protocol probes
                                │
                                ▼
                              Record matrix in LIVE-PROGRESS.md (§7)
                                │
                                ▼
                              Release hardware ownership (§10)
                                │
                                ▼
                              DONE
```

## The 8 pre-conditions (plan §0)

1. `pwd` = OpenPolaris repo
2. `git status` clean
3. `git log --oneline -1` = `bc8f4b5 ...` (or newer)
4. `ssh root@192.168.0.1 cat /app/FwVer` = `FwVer:4.0.0.32;date:2025.05.09;`
5. `ssh root@192.168.0.1 cat /app/openpolaris-libgphoto2-provenance.txt` shows `git_commit=6aa3e4e66240d4b4d68a65b75631e0f6aadf308a`
6. `ip route get 192.168.0.1` shows `dev wlp8s0`
7. K-1 II body + charged battery on bench
8. K-1 II USB cable on bench

## The 5 most important SSH commands

```sh
# State check
ssh root@192.168.0.1 'cat /app/FwVer'
ssh root@192.168.0.1 'lsusb 2>&1 | grep 25fb'
ssh root@192.168.0.1 'echo "1&286&2&#" | nc -w 2 127.0.0.1 9090'
ssh root@192.168.0.1 'tail -50 /app/Clog.txt'
ssh root@192.168.0.1 'tail -50 /app/Mlog.txt'
```

## The 3 most important cli-probe commands

```sh
# 286 = camera identify
tools/cli-probe/build/install/cli-probe/bin/cli-probe 286 "" 2 127.0.0.1 9090

# 264 = capture (subtype 4) — only works if pgphoto is running
tools/cli-probe/build/install/cli-probe/bin/cli-probe 264 "state:1;bulb:0;c:-1;" 4 127.0.0.1 9090

# 291 = liveview SET ON
tools/cli-probe/build/install/cli-probe/bin/cli-probe 291 "state:1;" 2 127.0.0.1 9090
```

## The 5 things you must NOT do

1. **Do not flash any firmware.** The user explicitly said
   the SD card is expendable, the PC is not. Don't even build
   a new FwPkt.zip — the previous session's zip
   (`/home/ian/Downloads/FwPkt(1).zip`) is the one in use.
2. **Do not `cp`, `mv`, `rm`, `pkill`, or write to any file
   on the gimbal** unless plan §5 explicitly says to (the
   warm-plug workaround is the only exception, and even that
   uses sysfs `authorized` files, not regular files).
3. **Do not "fix" any of the open issues** (patcher#36/37/38/39,
   OpenPolaris#60/61/62/63/64/65, libgphoto2#48/49). The
   fixes are already designed; this plan is for the K-1 II
   matrix, not for fixing those issues.
4. **Do not claim hardware ownership without committing and
   pushing LIVE-PROGRESS.md first.** The push IS the claim.
5. **Do not stop mid-swap** with the gimbal powered off and
   the K-1 II attached. Either complete §3-§10 or power
   the gimbal back on with the K-3 III re-attached.

## If you find something unexpected

1. Note the exact symptom in `docs/evidence/2026-09-07/`
   with a timestamped filename
2. File a new OpenPolaris issue with:
   - Title: short description (e.g. "K-1 II cold-boot
     enumeration fails after vendor mode")
   - Body: the symptom, the commands that triggered it,
     the relevant log lines
   - Cross-link to OpenPolaris#56
3. Continue with the next plan step unless the next step
   is also blocked

## Plan completion checklist (plan §14)

- [ ] All K-1 II matrix rows have a status (no `?` or `TBD`)
- [ ] LIVE-PROGRESS.md has a swap completion entry
- [ ] LIVE-PROGRESS.md hardware-test owner is `UNCLAIMED`
- [ ] Everything committed and pushed
- [ ] The 4 prior handoff docs are still present
- [ ] (Optional) New issue links in OpenPolaris#56 comment
