# j2me-browser

A web browser for J2ME (MIDP 2.0 / CLDC 1.1) feature phones — the Java
runtime shared by Nokia's Series 40 / Asha platform devices and a wide range
of other feature phones from that era. Developed and tested primarily
against Nokia S40/Asha hardware, but not limited to it: any MIDP 2.0 / CLDC
1.1 device should run it.

This is a real, in-progress browser project — not a throwaway template.
Right now it's at its first milestone: a Hello World MIDlet proving out the
build/install pipeline end to end. Actual browser functionality (fetching
pages, rendering HTML) is the next step.

## Prerequisites

Install these before running `make setup`:

- `java`, `javac`, `jar` (JDK required, recommended: Temurin/OpenJDK 17)
- `curl`
- `unzip`

Detailed Java install instructions: `docs/java-setup.md`

## Quick Start

```bash
make setup
make run
```

`make run` builds the MIDlet and launches it in MicroEmulator.

## Commands

```bash
make help   # command list
make setup  # install pinned project toolchain
make build  # compile -> preverify -> package
make run    # build and run in MicroEmulator
make serve  # build and serve app.jad/app.jar over HTTP for installing on a real device
make doctor # show prerequisites and toolchain status
make clean  # remove generated artifacts
```

## Installing on a real device

Over Wi-Fi (OTA install):

1. `make serve`
2. On the device (same Wi-Fi network), open the browser and go to
   `http://<this-machine-ip>:8765/app.jad`, then confirm the install.

Over Bluetooth:

1. `make build`
2. Send `dist/app.jar` to the device via Bluetooth OBEX push, then confirm
   the install prompt. The jar's manifest carries all the metadata the
   device needs, so no separate `.jad` is required for this path.

## Toolchain (Pinned)

- Eclipse ECJ `3.38.0`
- CLDC API stubs `2.0.4`
- MIDP API stubs `2.0.4`
- ProGuard `7.8.2` (`-microedition` preverification)
- MicroEmulator Swing `2.0.0`

## Build Pipeline

1. `compile` compiles MIDlet sources against CLDC/MIDP stubs
2. `preverify` writes a manifest with the required `MIDlet-*` attributes and
   runs ProGuard in microedition mode
3. `jar` writes `dist/app.jar` and updates JAD fields
4. `run` launches MicroEmulator with `app.jad`

## Project Layout

- `src/MainMIDlet.java` — MIDlet entry point and UI
- `app.jad` — app descriptor (name/version/vendor metadata)
- `Makefile` — build pipeline
- `scripts/serve.py` — HTTP server for OTA installs, serving from repo root
