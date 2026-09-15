# 8080 MJPEG stream probe — K-1 II, 2026-09-15 ~16:35

Gimbal FW: 4.0.0.32 (2025.05.09). Gimbal repowered before camera attach.

## Findings
1. **8080 IS bound and serving valid JPEGs** (patcher #44 not triggered after power-cycle):
   - `GET /?action=stream` → HTTP/1.0 200, `multipart/x-mixed-replace;boundary=boundarydonotcross`,
     `Server: MJPG-Streamer/0.2`, parts are valid JPEGs (~30 KB each).
2. **Frame rate is very low: ~1 frame per 7–8 s** (2 frames in a 35 s window, gap 8.5 s).
   - Consequence for the app: `PREVIEW_READ_TIMEOUT_MS = 10_000` sits right at the edge of the
     inter-frame gap → the transport's SocketTimeoutException fires between frames, cycling the
     pane between a frame and "Stream unavailable: Preview stream stalled".
3. **266 (CAM_GET_STATE) gets NO response from this firmware**: Mlog shows the app's 266 request
   (`val:-100` = EMPTY_CONTENT sentinel) but zero `SP_SendMsgToApp ... code[266]` lines and zero
   `MsgFromCamera ... code[266]` lines. The K-3 III evidence (k3iii-live-test-2026-09-06) DOES show
   266 pushes with `RD:0;V:;R:...` payloads, so this is camera/firmware-dependent.
   - Consequence for the app: the #60 capture state machine (which completes on a 266 idle poll)
     never sees a transition → sits in Requested until the 15 s watchdog reports Failed("timeout")
     even though the shot succeeded.

## App-side fixes needed
- Bump PREVIEW_READ_TIMEOUT_MS 10 s → 30 s (covers the K-1 II inter-frame gap).
- #60 fallback: if no 266 response arrives within the capture window, treat as Completed
  ("camera did not report capture state") instead of Failed.
