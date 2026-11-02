# Running Open Polaris from either remote desktop

Open Polaris has two remote-desktop paths on the development host:

- The GNOME Remote Desktop service on port **3389** is a Wayland session. The
  launch scripts detect this and run the JVM inside a local Xephyr X11 display.
  This keeps Skiko and Java2D on X11 instead of allowing the application to
  create a graphics surface through the host Wayland compositor.
- The xrdp service on port **3391** is an X11 session. The launch scripts use
  its existing `DISPLAY` directly.

Use one of the normal entry points:

```sh
./scripts/launch-desktop-image.sh
./scripts/launch-desktop.sh
./scripts/run-desktop.sh
```

The default rendering path is software. `OPENPOLARIS_GPU=1` opts out of the
software flags when the selected X11 display has a known-good GPU path.

The display policy can be inspected or overridden for diagnosis:

```sh
OPENPOLARIS_NESTED_X=auto   ./scripts/run-desktop.sh  # default: detect Wayland
OPENPOLARIS_NESTED_X=always ./scripts/run-desktop.sh  # force Xephyr
OPENPOLARIS_NESTED_X=never  ./scripts/run-desktop.sh  # direct X11 only
```

`auto` fails closed on Wayland if `Xephyr` is not installed. It does not fall
back to launching the JVM directly through Wayland because the host has
previously lost GNOME Shell during that path. `never` is only appropriate when
the caller has independently confirmed that `DISPLAY` is an isolated, safe
X11 session. The nested server listens only on the local UNIX socket and has
TCP listening disabled. Its display is selected from `:100` through `:199`,
and its output is logged to `/tmp/openpolaris-xephyr.log` by default.

The helper does not start Open Polaris by itself. The regression test
`scripts/desktop-display-test.sh` checks direct X11 routing, the Wayland
fail-closed gate, invalid policy handling, and the software-rendering
environment without creating a window. A real launch remains a separate
supervised test because it exercises the desktop compositor and graphics
stack.
