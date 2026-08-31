#!/usr/bin/env bash
# wake-and-probe.sh — post-reboot wake + first-look probe for the Polaris
#
# The Benro Polaris (Hi3559V200) keeps its SoC, WiFi AP and BLE radio in
# deep sleep together. When the AP drops, the BT radio goes with it, so
# `bluetoothctl scan` returns nothing on a sleeping gimbal.
#
# The OpenPolaris project discovered (see
# `shared/src/jvmMain/kotlin/dev/openpolaris/core/net/BluetoothProbe.kt`,
# `wake()` at line 120, comment "Benro Polaris wakes on a bare GATT
# connect") that a bare `bluetoothctl connect <MAC>` is the wake pulse
# once the polaris is powered up. The connect settles ~2s and then the
# AP and SSH come up.
#
# If BT wake does not work, the polaris MAC may not yet be known to
# bluez — try the second-stage scan loop. As a final fallback, watch
# `nmcli device wifi list` for the polaris_d13e86 SSID.
#
# Run AFTER the user has hard-rebooted the gimbal and confirmed the
# gimbal is showing blue (idle-wake LED state).
#
# Polaris BT MAC: 48:E7:DA:D4:B5:72  (WiFi MAC is ...73, BT is ...72)

set -u
POLARIS_BT="48:E7:DA:D4:B5:72"
POLARIS_SSH="root@192.168.0.1"
AP_SSID_RE='polaris_d13e86'

echo "== stage 1: BT wake pulse =="
# In a single bluetoothctl session, power on, pair, trust, connect.
# The connect IS the wake — after 2s settle, the AP should come up.
timeout 30 bluetoothctl <<EOF
power on
scan on
pair ${POLARIS_BT}
trust ${POLARIS_BT}
connect ${POLARIS_BT}
quit
EOF
BT_RC=$?
echo "bluetoothctl exit: ${BT_RC}"

echo
echo "== stage 2: wait for AP =="
AP_UP=""
WAIT_AP_SECS="${WAIT_AP_SECS:-60}"   # override with WAIT_AP_SECS=300 for slow wake
for i in $(seq 1 ${WAIT_AP_SECS}); do
  if nmcli -t -f SSID device wifi list 2>/dev/null | grep -qE "${AP_SSID_RE}"; then
    AP_UP="yes"
    echo "AP visible after ${i}s"
    break
  fi
  # also accept any visible 48:e7:da:* BSSID as a wake signal
  if nmcli -t -f BSSID device wifi list 2>/dev/null | grep -qi "48:E7:DA"; then
    AP_UP="yes"
    echo "polaris BSSID 48:E7:DA:* visible after ${i}s"
    break
  fi
  sleep 1
done
if [ -z "${AP_UP}" ]; then
  echo "AP not visible after ${WAIT_AP_SECS}s — polaris may still be in deep sleep."
  echo "Possible next steps:"
  echo "  1. WAIT_AP_SECS=300 $0          (longer wait, polaris may be slow to wake after hard-reboot)"
  echo "  2. Re-run $0                    (try the BT wake pulse again)"
  echo "  3. Press the 2-press on the gimbal, or open the polaris app on the phone"
  echo "  4. Confirm with: nmcli device wifi list | grep -i polaris"
  exit 1
fi

echo
echo "== stage 3: connect to AP =="
nmcli device wifi connect "${AP_SSID_RE}" --ask || true
# AP may have an empty PSK or a default one; if ask prompts, the user must
# answer at the terminal. The default polaris_d13e86 has no password in
# known firmwares; if yours does, pass it via --password.

echo
echo "== stage 4: confirm SSH =="
for i in $(seq 1 30); do
  if ssh -o ConnectTimeout=3 -o BatchMode=yes ${POLARIS_SSH} 'uname -a; uptime' 2>/dev/null; then
    echo "SSH up after ${i}s"
    break
  fi
  sleep 1
done

echo
echo "== stage 5: first-look probe =="
EVID_DIR="/home/ian/Documents/VSCodeProjects/OpenPolaris/docs/evidence/gimbal-ssh-2026-08-31"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${EVID_DIR}/probes-${STAMP}"
mkdir -p "${OUT_DIR}"
echo "evidence -> ${OUT_DIR}"

# Run the on-device probe and tee every output to a file.
ssh -o ConnectTimeout=5 -o BatchMode=yes ${POLARIS_SSH} '
  echo "--- uname/uptime ---"
  uname -a; uptime
  echo "--- ps full ---"
  ps -ef 2>/dev/null || ps
  echo "--- install daemon? (polestar|upgrade|fwPkt) ---"
  ps | grep -E "polestar|upgrade|fwPkt" | grep -v grep
  echo "--- candidate watch paths (H1) ---"
  for p in /app/sd /app/sdcard /mnt/sd1 /firmware /tmp /mnt; do
    echo "== ls ${p}/ =="
    ls -la "${p}/" 2>&1 | head -10
  done
  echo "--- /app/sd FwPkt.zip MD5 (expect 7a4c...e1f4 if present) ---"
  md5sum /app/sd/FwPkt.zip 2>&1
  echo "--- find any FwPkt.zip on device (H1 hint) ---"
  find / -name FwPkt.zip 2>/dev/null
  echo "--- USB-UART driver state (SP_TtyUsbUartInit gate) ---"
  lsmod 2>&1 || cat /proc/modules
  echo "--- /dev/ttyUSB* present? ---"
  ls -la /dev/ttyUSB* 2>&1
  echo "--- modules.ko / kernel modules search ---"
  find / -name "*.ko" 2>/dev/null | head -30
  echo "--- /proc/modules detail ---"
  cat /proc/modules 2>&1
  echo "--- USB device tree (gimbal-side) ---"
  lsusb 2>&1 || cat /proc/bus/usb/devices 2>&1
  echo "--- recent dmesg (last 80 lines, USB/UART/firmware) ---"
  dmesg 2>&1 | tail -80 | grep -iE "usb|uart|serial|ftdi|cdc|firmware|upgrade|fwpkt|cdc_acm" || dmesg 2>&1 | tail -80
  echo "--- Mlog FwPkt touch (H1/H3) ---"
  ls /app/mcu_debug/ 2>&1
  grep -E "fwpkt|firmware|crc|upgrade|SP_UpgradeCheckFw|TtyUsbUartInit" /app/mcu_debug/Mlog_* 2>&1 | tail -60
  echo "--- ps in mcu_debug (firmware check helpers) ---"
  ls -la /app/ 2>&1
' | tee "${OUT_DIR}/01-first-look.txt"

# Pull files we know exist on the polaris for offline analysis.
echo
echo "== stage 6: pull on-device evidence =="
for rel in /proc/version /proc/cmdline /proc/mtd /etc/os-release; do
  base="$(basename "${rel}")"
  ssh -o ConnectTimeout=5 -o BatchMode=yes ${POLARIS_SSH} "cat ${rel}" \
    >"${OUT_DIR}/02-${base}.txt" 2>/dev/null || true
done

# Pull the most recent Mlog if reachable.
Mlog_remote="$(ssh -o ConnectTimeout=5 -o BatchMode=yes ${POLARIS_SSH} \
  'ls -t /app/mcu_debug/Mlog_* 2>/dev/null | head -1' 2>/dev/null | tr -d '\r')"
if [ -n "${Mlog_remote}" ]; then
  echo "pulling ${Mlog_remote}"
  scp -o ConnectTimeout=5 ${POLARIS_SSH}:"${Mlog_remote}" "${OUT_DIR}/03-${Mlog_remote##*/}"
else
  echo "no Mlog_* found on device"
fi

echo
echo "== done. evidence in ${OUT_DIR} =="
ls -la "${OUT_DIR}"
