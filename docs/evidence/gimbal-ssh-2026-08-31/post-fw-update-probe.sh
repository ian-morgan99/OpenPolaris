#!/usr/bin/env bash
# post-fw-update-probe.sh — capture the post-firmware-update state of the Polaris
#
# Companion to wake-and-probe.sh. By the time you run this script you should
# already be on the polaris_* AP and have SSH to root@192.168.0.1 — i.e. you
# have either:
#   (a) just run wake-and-probe.sh, or
#   (b) the gimbal woke on its own (e.g. user opened the Benro app on the
#       phone, which keeps the SoC alive).
#
# What this script captures, in this order:
#   1. FwVer (expect it to differ from the pre-update baseline in
#      docs/evidence/firmware-update-2026-08-31/01-pre-probe-state.txt)
#   2. /app/sd/FwPkt.zip md5 (the file that was scp'd in)
#   3. /app/sd/FwPkt/ extracted directory listing, if the upgrade path left
#      one behind (live-capture showed the state machine extracted it before
#      running crcInfo/getFwInfo/getOmsFwInfo)
#   4. /app/sd/HDR/ contents (header-validation temp dir; should be empty
#      after a successful upgrade)
#   5. New Mlog tail filtered for SP_UpgradeCheckFw / SP_UpgradeFirmwareProC /
#      SP_UpgradeReboot / SP_TtyUsbUartInit / FwVer / appfs / rootfs / md5
#   6. Clog last 20 lines (look for any post-upgrade error reports)
#   7. /proc/net/tcp listener check for port 9090 (the protocol daemon;
#      if the upgrade replaced the polestar binary it may have re-bound)
#   8. One-shot 9090 GET_TEMPERATURE=525 frame to confirm the wire format
#      hasn't changed in incompatible ways
#
# Output is written to:
#   docs/evidence/firmware-update-2026-08-31/post-update-probes-${STAMP}/
#
# The pre-update baseline (for diff) lives in:
#   docs/evidence/firmware-update-2026-08-31/01-pre-probe-state.txt
#
# Usage:
#   ./post-fw-update-probe.sh                              # full snapshot
#   ./post-fw-update-probe.sh --no-protocol                # skip the 525 frame
#   ./post-fw-update-probe.sh --baseline <path>            # diff vs this file
#
# Set FW_UPDATE_BASELINE=<path> in the environment to permanently point at
# a different baseline file (e.g. an earlier probe from a different card).

set -u
POLARIS_SSH="root@192.168.0.1"
GIMBAL_PORT="9090"

EVID_ROOT="/home/ian/Documents/VSCodeProjects/OpenPolaris/docs/evidence"
FW_UPDATE_DIR="${EVID_ROOT}/firmware-update-2026-08-31"
DEFAULT_BASELINE="${FW_UPDATE_DIR}/01-pre-probe-state.txt"
BASELINE="${FW_UPDATE_BASELINE:-${DEFAULT_BASELINE}}"

# ----- argument parsing --------------------------------------------------
# Parse flags first so --help exits before we create an evidence dir.

DO_PROTOCOL=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-protocol) DO_PROTOCOL=0; shift ;;
    --baseline)    BASELINE="$2"; shift 2 ;;
    --baseline=*)  BASELINE="${1#*=}"; shift ;;
    -h|--help)
      sed -n '2,40p' "$0"
      exit 0
      ;;
    *)
      echo "WARN: unknown argument '$1' (ignored)" >&2
      shift
      ;;
  esac
done

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${FW_UPDATE_DIR}/post-update-probes-${STAMP}"
mkdir -p "${OUT_DIR}"
echo "evidence -> ${OUT_DIR}"
echo "baseline -> ${BASELINE}"
echo

# ----- helpers -----------------------------------------------------------

log() { printf '%s\n' "$*"; }

# Filter Mlog on the host (BusyBox grep lacks --line-buffered).
filter_mlog_remote() {
  local pattern="$1"
  local outfile="$2"
  ssh -o ConnectTimeout=5 -o BatchMode=yes ${POLARIS_SSH} \
    "ls -t /app/Mlog.txt /app/mcu_debug/Mlog_* 2>/dev/null" 2>/dev/null \
    | tr -d '\r' \
    | head -1 \
    | while read -r mlog; do
        ssh -o ConnectTimeout=5 -o BatchMode=yes ${POLARIS_SSH} \
          "tail -n 4000 '${mlog}' 2>/dev/null" \
          | grep -E "${pattern}" \
          | tail -200
      done \
    > "${outfile}"
}

# ----- 1. FwVer ---------------------------------------------------------

log "== 1/8: FwVer =="
ssh -o ConnectTimeout=5 -o BatchMode=yes ${POLARIS_SSH} '
  echo "--- /app/FwVer ---"
  cat /app/FwVer 2>&1
  echo "--- /app/FwVer detail (if file) ---"
  ls -la /app/FwVer 2>&1
' | tee "${OUT_DIR}/01-fwver.txt"

# Diff against the baseline (best-effort).
if [[ -f "${BASELINE}" ]]; then
  log "  baseline FwVer (from ${BASELINE}):"
  grep -E "^FwVer:|^FwPkt|^Installed FwVer" "${BASELINE}" 2>/dev/null \
    | sed 's/^/    /' || echo "    (baseline has no FwVer line)"
else
  log "  baseline not present at ${BASELINE} (skipping diff)"
fi

# ----- 2. FwPkt.zip md5 + size -----------------------------------------

log
log "== 2/8: FwPkt.zip final md5 =="
ssh -o ConnectTimeout=5 -o BatchMode=yes ${POLARIS_SSH} '
  echo "--- FwPkt.zip presence ---"
  ls -la /app/sd/FwPkt.zip 2>&1
  echo "--- FwPkt.zip md5 ---"
  md5sum /app/sd/FwPkt.zip 2>&1
  echo "--- FwPkt.zip size + crc32 ---"
  stat -c "size=%s blocks=%b mtime=%Y" /app/sd/FwPkt.zip 2>&1
  echo "--- find any FwPkt.zip on device ---"
  find / -name FwPkt.zip 2>/dev/null
' | tee "${OUT_DIR}/02-fwpkt-md5.txt"

# ----- 3. /app/sd/FwPkt/ extracted dir --------------------------------

log
log "== 3/8: /app/sd/FwPkt/ extracted directory =="
ssh -o ConnectTimeout=5 -o BatchMode=yes ${POLARIS_SSH} '
  echo "--- /app/sd/FwPkt/ presence ---"
  ls -la /app/sd/FwPkt/ 2>&1
  echo "--- /app/sd/FwPkt/ tree (limited) ---"
  ( command -v tree >/dev/null && tree -L 2 /app/sd/FwPkt/ 2>&1 ) || \
    find /app/sd/FwPkt/ -maxdepth 2 -print 2>/dev/null
  echo "--- FwPkt content sample md5s ---"
  find /app/sd/FwPkt/ -maxdepth 2 -type f 2>/dev/null \
    | head -20 \
    | while read -r f; do md5sum "$f" 2>/dev/null; done
' | tee "${OUT_DIR}/03-fwpkt-dir.txt"

# ----- 4. /app/sd/HDR/ ------------------------------------------------

log
log "== 4/8: /app/sd/HDR/ header-validation dir =="
ssh -o ConnectTimeout=5 -o BatchMode=yes ${POLARIS_SSH} '
  echo "--- /app/sd/HDR/ contents ---"
  ls -la /app/sd/HDR/ 2>&1
' | tee "${OUT_DIR}/04-hdr-dir.txt"

# ----- 5. Mlog tail filtered for upgrade markers ----------------------

log
log "== 5/8: Mlog tail filtered for upgrade markers =="
UPGRADE_PATTERN='SP_UpgradeCheckFw|SP_UpgradeFirmwareProC|SP_UpgradeReboot|SP_TtyUsbUartInit|FwVer|appfs|rootfs|crcInfo|firmwareInfo|getFwInfo|getOmsFwInfo|FwPkt|EXDEV_FW_PATH|GIMBAL_FW_PATH|FwSize'
filter_mlog_remote "${UPGRADE_PATTERN}" "${OUT_DIR}/05-mlog-upgrade.txt"
LINES_FILTERED=$(wc -l < "${OUT_DIR}/05-mlog-upgrade.txt" 2>/dev/null || echo 0)
log "  ${LINES_FILTERED} matching lines -> 05-mlog-upgrade.txt"

# ----- 6. Clog last 20 -------------------------------------------------

log
log "== 6/8: Clog last 20 lines =="
ssh -o ConnectTimeout=5 -o BatchMode=yes ${POLARIS_SSH} '
  echo "--- Clog locations ---"
  ls -la /app/Clog.txt /app/sd/Clog.txt 2>&1
  echo "--- /app/Clog.txt last 20 ---"
  tail -n 20 /app/Clog.txt 2>&1
' | tee "${OUT_DIR}/06-clog.txt"

# ----- 7. Port 9090 listener ------------------------------------------

log
log "== 7/8: port ${GIMBAL_PORT} listener + polestar PID =="
ssh -o ConnectTimeout=5 -o BatchMode=yes ${POLARIS_SSH} '
  echo "--- /proc/net/tcp listeners (look for 9090 = 0x2384) ---"
  cat /proc/net/tcp | awk '"'"'$2 ~ /:2384$/ || $3 ~ /:2384$/ {print}'"'"'
  echo "--- polestar process (if running) ---"
  ps -ef 2>/dev/null | grep -E "polestar" | grep -v grep || ps | grep polestar | grep -v grep
  echo "--- ss / netstat fallback ---"
  ( command -v ss >/dev/null && ss -tlnp 2>&1 | grep 9090 ) || \
    ( command -v netstat >/dev/null && netstat -tlnp 2>&1 | grep 9090 ) || \
    echo "  (no ss/netstat — using /proc/net/tcp above)"
' | tee "${OUT_DIR}/07-port-9090.txt"

# ----- 8. One-shot 525 GET_TEMPERATURE ---------------------------------

if [[ "${DO_PROTOCOL}" -eq 1 ]]; then
  log
  log "== 8/8: 9090 one-shot GET_TEMPERATURE=525 =="
  # Use the cli-probe's v0 single-shot entry point. The tool is built
  # by the project but not installed system-wide; the easiest way is to
  # let the user run the gradle task and tee the output here. If gradle
  # is not available, fall back to a raw TCP write.
  if [[ -x "/home/ian/Documents/VSCodeProjects/OpenPolaris/gradlew" ]]; then
    log "  firing cli-probe 284 single-shot (PUSH_MODE_STATE) via gradle"
    (
      cd /home/ian/Documents/VSCodeProjects/OpenPolaris
      timeout 15 ./gradlew --console=plain :tools:cli-probe:run \
        --args "192.168.0.1 ${GIMBAL_PORT} 525" 2>&1
    ) | tee "${OUT_DIR}/08-cli-probe-525.txt" || \
      log "  WARN: gradle probe failed; see 08-cli-probe-525.txt"
  else
    log "  gradlew not present — writing a raw '525\\n' to 192.168.0.1:${GIMBAL_PORT}"
    {
      printf '525\n'
      sleep 1
    } | timeout 5 bash -c "exec 3<>/dev/tcp/192.168.0.1/${GIMBAL_PORT}; cat <&3" \
      > "${OUT_DIR}/08-raw-525.txt" 2>&1 || \
      log "  WARN: raw TCP probe failed; see 08-raw-525.txt"
  fi
else
  log
  log "== 8/8: skipped (--no-protocol) =="
  echo "skipped" > "${OUT_DIR}/08-SKIPPED.txt"
fi

# ----- done ------------------------------------------------------------

log
log "== done. evidence in ${OUT_DIR} =="
ls -la "${OUT_DIR}"
