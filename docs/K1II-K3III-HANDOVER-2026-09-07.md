# Pentax K-1 II / K-3 III handover — 2026-09-07

## Reproducible source and firmware

- libgphoto2: `6aa3e4e66240d4b4d68a65b75631e0f6aadf308a`
- Benro firmware patcher: `af5b0d3`
- OpenPolaris firmware workflow: `c81a363`
- candidate `FwPkt.zip` SHA-256:
  `61a000cf62d911b8494c9f70e7a3d82775223acc6ca98b407601651cd6a4f02a`
- candidate `FwPkt.zip` MD5: `955ec67a093e7cedd0b0342ce40ec94e`
- candidate `appfs.ubifs` MD5: `d745fe162d6691cd2f1647813c49d3d1`

The package was built from the clean libgphoto2 commit above. Its retained
corresponding-source archive and embedded provenance were checked against the
image payload before installation.

## Physical Polaris install proof

The ZIP was transferred as one artifact to `/app/sd/FwPkt.zip` and its remote
MD5 was verified. Protocol code 783 made the stock updater validate and extract
the package. All six entries in `firmwareInfo` and `crcInfo` matched. A reboot
then invoked the boot-time SD scan.

After reboot:

- `/app/Mlog.txt` recorded `SP_EVENT_UPGRADE_SUCCESS`;
- `/app/openpolaris-libgphoto2-provenance.txt` reports the exact clean
  libgphoto2 commit above;
- the packaged `pgphoto.stage2ondisk` owns TCP 8080;
- `polestar_app` owns TCP 9090;
- the staged ZIP and extracted tree were consumed.

Do not use the incomplete 810/784/794/795 wire uploader. Its 794 implementation
does not transmit chunk bytes. OpenPolaris `c81a363` disables that UI choice and
implements the hardware-proven desktop flow: SSH transfer, 783 extraction,
manifest readiness check, then mandatory reboot.

## Camera results

K-3 III directly attached to the PC passed detection, summary, settings
read/write verification, preview, and two full-resolution capture downloads at
libgphoto2 `6aa3e4e6`.

### K-3 III capture timing observed through Polaris

The exact capture frame `1&264&4&state:1;bulb:0;c:-1;#` **does take a
photograph**. Do not classify the intermediate `264@state:-1005#` response as a
terminal capture failure:

- the initial `264@state:1#` acknowledgement arrived after about 12 ms;
- `264@state:-1005#` arrived about 2.38 seconds after the request;
- the camera returned to idle at about 3.17 seconds;
- the completed image appeared in the client about 3–4 seconds after shutter;
- the visible client sequence was **Error**, then **Camera busy**, then the
  captured image appeared.

This behavior has been seen repeatedly during physical testing. It establishes
that capture and eventual image delivery work, while status/UI handling during
the asynchronous capture is wrong or misleading. Tests must wait for the final
image/file event before deciding success or failure, and should record the
intermediate states rather than collapsing `-1005` into a terminal error.

Preview remains a separate problem: the embedded Pentax path repeatedly
reports `0xa008` (`NoUpdateImage`) for 30 attempts over roughly 1.24 seconds,
followed by a successful restore (`0x2001`). Do not infer preview failure from
the transient capture messages, or capture failure from the preview result.

Controlled live-view cycling proves this is not merely a log warning:

- OFF was acknowledged by 291 and read back as `292@state:0`;
- HTTP 8080 returned `200 OK` but only the 22-byte opening delimiter
  `--boundarydonotcross\r\n`, with no JPEG;
- ON was acknowledged by 291 and read back as `292@state:1`;
- HTTP 8080 still returned only the same 22-byte delimiter over a 10-second
  observation, with no part headers, body or JPEG;
- a second OFF/ON/fetch cycle failed identically;
- a clean `pgphoto` restart left exactly one replacement process owning 8080,
  but a third live-view attempt again produced only 22 bytes.

The control plane and HTTP status therefore falsely look healthy while the
preview data plane is dead. OpenPolaris additionally has no first-frame
deadline (`JvmPreviewTransport.readTimeout = 0`), so this server behavior can
leave the client in `Connecting` indefinitely; that is tracked in #61.

After the same `pgphoto` restart, camera-info returned
`manufacturer:none;model:none;state:-2` over six polls. The user subsequently
confirmed that the K-3 III battery was flat. This result is therefore invalid
as evidence of a runtime rediscovery failure: process/listener replacement
passed, but camera recovery remains untested and must be repeated with a
powered camera. Do not use this run to support patcher #34.

### K-3 III qualification decision

K-3 III testing is **not complete**, and neither client is currently qualified
as 100% functional with it.

| Capability | Direct PC/libgphoto2 | Polaris + Benro Connect | OpenPolaris |
|---|---|---|---|
| Detect/config/read-write | Passed for the exercised controls | Camera identified; full matrix not complete | Protocol support exists; physical UI matrix incomplete |
| Still capture | Two captures passed | Photograph arrives after 3–4 s, but UI reports Error then Camera busy first | Correct frame exists; completion/file-delivery workflow is not yet implemented or hardware-qualified |
| Live preview | Passed | Embedded runtime repeatedly returns `0xa008`; not qualified | Transport exists, but cannot be functional while Polaris produces no frame |
| Focus/AF | Not completely qualified | Not completely qualified | Not completely qualified |
| Repeated capture soak | Not completed | Not completed | Not completed |
| Preview stop/restart | Direct preview passed; cycle matrix incomplete | Not completed because first frame fails | Not completed because first frame fails |
| USB/process/power-cycle recovery | Basic direct reconnect passed | Full recovery matrix incomplete | Full recovery matrix incomplete |

The failed embedded preview with a direct-host pass at the same libgphoto2 SHA
assigns the current defect to the Polaris integration/runtime until contrary
lower-level evidence exists. It does not justify another base-libgphoto2 change.
The intermediate capture state needs fixes in both the runtime contract and the
OpenPolaris client workflow, followed by a physical retest.

Owning issues:

- `ian-morgan99/benro-polaris-firmware-patcher#36` — embedded K-3 III preview
  returns `0xa008` while the same libgphoto2 SHA passes directly;
- `ian-morgan99/benro-polaris-firmware-patcher#37` — capture emits transient
  `-1005` before successful delayed image delivery;
- `ian-morgan99/OpenPolaris#60` — model the asynchronous capture lifecycle and
  await the final image/file event;
- `ian-morgan99/OpenPolaris#61` — detect an HTTP-200 multipart stream that
  never produces its first valid JPEG instead of remaining Connecting forever;
- `ian-morgan99/OpenPolaris#56` — incomplete physical qualification rows,
  including focus, soak, preview cycling and recovery;
- `ian-morgan99/benro-polaris-firmware-patcher#35` — broader K-3 III embedded
  runtime isolation, corrected by the 2026-09-07 capture evidence;
- `ian-morgan99/libgphoto2#44` — direct hardware qualification matrix. No new
  base-libgphoto2 defect is supported by the current A/B evidence.

K-1 II enumerated on the flashed Polaris and configuration reads worked,
including the corrected White Balance choice table. It subsequently physically
disconnected (`usb 1-1.2: USB disconnect`). The Polaris currently exposes only
its internal USB hub, so camera status is `state:-5` and capture returns
`state:-1002`. Those are no-device results, not camera-driver failures.

The packaged `/app/restart_gphoto` helper was tested after the disconnect. It
terminated the sole old PID, started one replacement, verified TCP 8080, and
returned success. Exactly one daemon/listener remained after settling.

## Next hardware gate

Before declaring K-3 III complete, resolve or explicitly defer the preview and
asynchronous capture-state defects, then complete the missing focus, soak,
preview-cycle and recovery rows above. After those defects are recorded, the
physical camera can be swapped back to K-1 II without losing the K-3 III
handover state.

Power/reseat the K-1 II so its Pentax USB VID/PID appears below the Polaris hub,
then run, in order:

1. camera-info 286 and the full settings read/write/read-back matrix;
2. preview from TCP 8080 and validate the returned JPEG;
3. exact capture frame `1&264&4&state:1;bulb:0;c:-1;#`;
4. verify a new full-size file under `/app/sd/normal/` and hash it;
5. restart `pgphoto` once with the camera attached and repeat preview/capture;
6. after K-1 II qualification, swap the K-3 III back only for targeted
   regression confirmation of the fixes and incomplete matrix rows.

The Polaris network is now 5 GHz: SSID `polaris_d13e86`, 5180 MHz, 80 MHz VHT.
Always require `ip route get 192.168.0.1` to report `dev wlp8s0`; otherwise the
address is the Hitron router, not the mount.
