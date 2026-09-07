# Camera command map — ground truth vs OpenPolaris implementation

Source: `com.snoppa.application.constant.polaris.PolarisCMD` extracted
from `BenroConnect_1727595281455.apk` (v3.0.30, build 240930).

## Codes 258-282 (camera settings)

| Benro (ground truth) | OpenPolaris Codes.kt | MATCH? |
|---|---|---|
| 258 = SP_SET_ISO | CAM_GET_ISO = 258 | **WRONG** — should be SET |
| 259 = SP_SET_WB | CAM_SET_ISO = 259 | **WRONG** (off by 2: 259 is SET WB) |
| 260 = SP_SET_EV | CAM_GET_WB = 260 | **WRONG** — should be SET EV |
| 261 = SP_SET_SHUTTER | CAM_SET_WB = 261 | **WRONG** — should be SET SHUTTER |
| 262 = SP_SET_FOCUS | CAM_GET_FNUM = 262 | **WRONG** — should be SET FOCUS |
| 263 = SP_SET_VIDEO_RECORD_STATUS | CAM_SET_FNUM = 263 | **WRONG** |
| 264 = SP_SET_PHOTO_RECORD_STATUS | CAM_GET_EV = 264 (also CAM_CAPTURE) | partially correct (CAPTURE is subtype 4) |
| 265 = SP_GET_ISO_INFO | CAM_SET_EV = 265 | **WRONG** — should be GET ISO |
| 266 = SP_GET_WB_INFO | CAM_CAPTURE_STATE = 266 (not in Codes.kt) | — |
| 267 = SP_GET_EV_INFO | (not in Codes.kt) | — |
| 268 = SP_GET_SHUTTER_INFO | CAM_GET_FOCUS = 268 | **WRONG** — should be GET SHUTTER |
| 270 = SP_FOCUS_STACK | CAM_GET_IMG_SIZE = 270 | **WRONG** — should be FOCUS_STACK |
| 271 = SP_PANORAMIC | CAM_SET_IMG_SIZE = 271 | **WRONG** — should be PANORAMIC |
| 272 = SP_DELAY_SHOT | CAM_GET_IMG_FMT = 272 | **WRONG** — should be DELAY_SHOT |
| 275 = SP_GET_FNUM_INFO | CAM_GET_COLOR = 275 | **WRONG** — should be GET FNUM |
| 276 = SP_SET_FNUM | CAM_GET_SHUTTER = 276 | **WRONG** — should be SET FNUM |
| 277 = SP_SUN_SHOT | CAM_SET_SHUTTER = 277 | **WRONG** — should be SUN_SHOT |
| 280 = SP_HDR | (not in Codes.kt) | — |
| 282 = SP_GET_IMG_FORMAT | (not in Codes.kt) | — |
| 283 = SP_PLC | (not in Codes.kt) | — |
| 286 = SP_CAMERA_INFO | CAM_INFO = 286 | ✓ CORRECT |
| 289 = SP_REMOVE_PEOPLE_SHOT | (not in Codes.kt) | — |
| 291 = SP_SET_CAMERA_PREVIEW | CAM_LIVEVIEW_SET = 291 | ✓ CORRECT |
| 292 = SP_GET_CAMERA_PREVIEW | CAM_LIVEVIEW_GET = 292 | ✓ CORRECT |
| 296 = SP_GET_CONTROL_MODE | (not in Codes.kt) | — |
| 297 = SP_SET_CONTROL_MODE | (not in Codes.kt) | — |
| 298 = SP_GET_EX_TIME | (not in Codes.kt) | — |
| 299 = SP_SET_EX_TIME | (not in Codes.kt) | — |
| 300 = SP_GET_HDMI_SUPPORT | (HDMI_OUTPUT = 300) | partially correct |
| 301 = SP_SET_HDMI_STATE | (HDMI_MODE = 301) | partially correct |
| 302 = SP_REBOOT_CONFIRM_MODE | (not in Codes.kt) | — |
| 303 = SP_GET_HDMI_STATE | (not in Codes.kt) | — |
| 304 = SP_PUSH_HDMI_STREAM_STATE | (not in Codes.kt) | — |
| 305 = SP_HOLY_GRAIL | (not in Codes.kt) | — |
| 306 = SP_GET_INTERVAL_TYPE | (not in Codes.kt) | — |
| 307 = SP_SET_INTERVAL_TYPE | (not in Codes.kt) | — |
| 311 = SP_SET_FOCUS_ADJ | CAM_FOCUS = 311 | ✓ CORRECT |

## Codes 770-825 (file / system / cellular / OMS)

| Benro | OpenPolaris | MATCH? |
|---|---|---|
| 770 = SP_GET_FILE_COUNT | FILE_LIST = 770 | **WRONG** — should be GET FILE COUNT |
| 771 = SP_GET_FILE_LIST | FILE_DELETE = 771 | **WRONG** — should be GET FILE LIST |
| 772 = SP_DEL_FILE | FILE_DOWNLOAD_START = 772 | **WRONG** — should be DEL FILE |
| 773 = SP_ADD_FILE | FILE_DOWNLOAD_DATA = 773 | **WRONG** — should be ADD FILE |
| 774 = SP_SD_FORMAT | FILE_DOWNLOAD_END = 774 | **WRONG** — should be SD FORMAT |
| 775 = SP_GET_SD_INFO | FILE_SD_STATUS = 775 | ✓ CORRECT (SD_INFO == SD_STATUS) |
| 776 = SP_PUSH_SD_INFO | FILE_SD_FORMAT = 776 | **WRONG** — should be PUSH SD INFO |
| 777 = SP_PUSH_SD_HINT_ID | FILE_SET_TYPE = 777 | **WRONG** — should be PUSH SD HINT |
| 778 = SP_GET_BAT_STATE | BATTERY_STATUS = 778 | ✓ CORRECT |
| 779 = SP_PUSH_BAT_STATE | BATTERY_DETAIL = 779 | **WRONG** — should be PUSH BAT |
| 780 = SP_GET_DEVICE_VERSION | DEVICE_INFO = 780 | ✓ CORRECT (semantically same) |
| 781 = SP_GET_SYSTEM_TIME | (not in Codes.kt) | — |
| 782 = SP_SET_SYSTEM_TIME | (not in Codes.kt) | — |
| 783 = SP_SET_UPGRADE_START | (not in Codes.kt — was 282?) | — |
| 784 = SP_LOAD_UPGRADE_FW_STATE | (FILE_UPLOAD_FW = 784, deprecated) | — |
| 785 = SP_PUSH_UPGRADE_STATUS | (FILE_BACKUP = 785) | — |
| 786 = SP_GET_CLASS_FILE_COUNT | FILE_RESTORE = 786 | **WRONG** — should be GET CLASS COUNT |
| 787 = SP_DEL_CLASS | FILE_CAM_LIST = 787 | **WRONG** — should be DEL CLASS |
| 788 = SP_APP_ADD_FILE | FILE_LIST_BY_DATE = 788 | **WRONG** — should be APP ADD FILE |
| 789 = (NOT IN BENRO CONNECT!) | FILE_DELETE_ALL = 789 | **DANGEROUS** — only this app uses 789! |
| 790 = SP_APP_PASSWORD_INFO | FILE_PROTECT = 790 | **WRONG** — should be APP PASSWORD |
| 791 = SP_EXDEV_UPGRADE_START | FILE_UNPROTECT = 791 | **WRONG** — should be EXDEV UPGRADE |
| 792 = SP_LOAD_EXDEV_FW_STATE | FILE_QUOTA = 792 | **WRONG** — should be EXDEV FW STATE |
| 793 = SP_PUSH_EXDEV_STATUS | FILE_PAGINATE = 793 | **WRONG** — should be PUSH EXDEV STATUS |
| 796 = SP_GET_ISP_CFG_FILE | FILE_CAM_RAW = 796 | ✓ CORRECT (CAM_RAW ≈ ISP_CFG) |
| 797 = SP_ERROR_CODE | FILE_SCAN_COMPLETE = 797 | **WRONG** — should be ERROR CODE |
| 798 = SP_GET_LOG_LIST | FILE_RENAME = 798 | **WRONG** — should be LOG LIST |
| 799 = SP_GET_CELLULAR_STATE | (GET_CELLULAR_STATE = 799) | ✓ CORRECT |
| 802 = SP_GET_WIFI_BAND | GET_WIFI_BAND = 802 | ✓ CORRECT |
| 803 = SP_SET_WIFI_BAND | SET_WIFI_BAND = 803 | ✓ CORRECT |
| 804 = SP_GET_WARNING_TONE_STATE | WIFI_CONNECT = 804 | **WRONG** — should be WARNING TONE |
| 805 = SP_SET_WARNING_TONE_STATE | WIFI_DISCONNECT = 805 | **WRONG** — should be WARNING TONE |
| 808 = SP_SOCKET_CLIENT_TYPE | SYS_VERSION = 808 | (semantically same) |
| 809 = SP_SET_CELLULAR_APN | SYS_SERIAL = 809 | (semantically same — both `sn:`) |
| 811 = SP_GET_CELLULAR_IMSI | SYS_FW_PROGRESS = 811 | (audit doc noted this conflict) |
| 812 = SP_GET_CELLULAR_IMEI | SYS_REBOOT = 812 | (audit doc noted) |
| 813 = SP_SET_CELLULAR_COMUSB | SYS_SHUTDOWN = 813 | (audit doc noted) |
| 814 = SP_GET_CELLULAR_HV | SYS_TIME = 814 | (audit doc noted) |
| 815 = SP_GET_AUTO_OFF_SW | SYS_TIMEZONE = 815 | (audit doc noted) |
| 816 = SP_SET_AUTO_OFF_SW | SYS_LANGUAGE = 816 | (audit doc noted) |

## Code 789 = **FILE_DELETE_ALL** is NOT in Benro Connect

This is the most dangerous finding. **Code 789 does not exist in
the official Benro Connect APK** at all. It only exists in our
OpenPolaris Codes.kt. This is a **synthetic code** that we
**created** in the OpenPolaris app, not something the Benro
firmware or app ever sends.

This means:
1. When OpenPolaris sends `1&789&2&path:normal/#` over the wire,
   the firmware receives an **unknown code**, but the firmware
   still parses the payload (`path:normal`) and **executes a
   file delete on the path**.
2. The firmware has its own undocumented code 789, distinct
   from anything in Benro Connect, which is **destructive and
   silent** (the firmware emits no Mlog/Clog entry for the op).
3. This is a **firmware-side feature that Benro Connect does
   not use** — they have no UI for it. The "destructive file
   delete" function is built into the firmware but never
   invoked by the official app.

## Summary

| Category | Total codes | Correct in OpenPolaris | Wrong | Missing |
|---|---|---|---|---|
| Camera 258-282 | 25 | 4 (286, 291, 292, 311) | 14 | 7 |
| File/system 770-825 | 56 | 5 (775, 778, 780, 796, 799) | 19 | 32 |
| **Total** | **81** | **9 (11%)** | **33 (41%)** | **39 (48%)** |

**Only 11% of the codes in the 258-825 range are correctly
named in OpenPolaris Codes.kt.** The other 89% is either wrong,
missing, or mis-mapped.

This is **far worse than the issue #62 body suggested**. The
issue said "the sequential GET/SET pairs from 258 onward are
wrong" — the actual situation is that **most of the codes
in Codes.kt are misnamed, and many codes the firmware actually
uses are not in Codes.kt at all**.

## Recommended fix

1. **Generate a new table** by decompiling the Benro Connect
   APK (the script in `/tmp/extract-polaris-cmd.py` is
   reproducible)
2. **Replace Codes.kt** with the decompile-derived mapping
3. **For each missing code**, decide:
   - Add to Codes.kt (most cases)
   - Mark as deprecated/dangerous (789)
4. **Re-test** every UI control on a K-3 III or K-1 II with
   a charged battery
5. **Lock the table** behind a regression test that fails if
   the codes change without updating PROTOCOL.md
