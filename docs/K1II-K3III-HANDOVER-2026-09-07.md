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

K-1 II enumerated on the flashed Polaris and configuration reads worked,
including the corrected White Balance choice table. It subsequently physically
disconnected (`usb 1-1.2: USB disconnect`). The Polaris currently exposes only
its internal USB hub, so camera status is `state:-5` and capture returns
`state:-1002`. Those are no-device results, not camera-driver failures.

The packaged `/app/restart_gphoto` helper was tested after the disconnect. It
terminated the sole old PID, started one replacement, verified TCP 8080, and
returned success. Exactly one daemon/listener remained after settling.

## Next hardware gate

Power/reseat the K-1 II so its Pentax USB VID/PID appears below the Polaris hub,
then run, in order:

1. camera-info 286 and the full settings read/write/read-back matrix;
2. preview from TCP 8080 and validate the returned JPEG;
3. exact capture frame `1&264&4&state:1;bulb:0;c:-1;#`;
4. verify a new full-size file under `/app/sd/normal/` and hash it;
5. restart `pgphoto` once with the camera attached and repeat preview/capture;
6. swap the K-3 III onto Polaris and repeat the same E2E matrix.

The Polaris network is now 5 GHz: SSID `polaris_d13e86`, 5180 MHz, 80 MHz VHT.
Always require `ip route get 192.168.0.1` to report `dev wlp8s0`; otherwise the
address is the Hitron router, not the mount.
