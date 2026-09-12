# j2me-browser

[![CI](https://github.com/AbhyasKanaujia/j2me-browser/actions/workflows/ci.yml/badge.svg)](https://github.com/AbhyasKanaujia/j2me-browser/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A from-scratch, self-sufficient web browser for J2ME (MIDP 2.0 / CLDC 1.1)
feature phones — no proxy, no third-party service, no expiration date.

Project page with install instructions:
[abhyaskanaujia.github.io/j2me-browser](https://abhyaskanaujia.github.io/j2me-browser/)

## Table of Contents

- [About](#about)
- [Status](#status)
- [How much of a browser is this?](#how-much-of-a-browser-is-this)
- [Architecture & Roadmap](#architecture--roadmap)
- [HTTPS / TLS plan](#https--tls-plan)
- [Device & compatibility target](#device--compatibility-target)
- [Getting Started](#getting-started)
- [Installing on a real device](#installing-on-a-real-device)
- [Toolchain (Pinned)](#toolchain-pinned)
- [Build Pipeline](#build-pipeline)
- [Project Layout](#project-layout)
- [Feature requests & bugs](#feature-requests--bugs)
- [Contributing](#contributing)
- [License](#license)

## About

Most "mobile browsers" for feature phones of this era — Opera Mini, UC
Browser — worked by proxying every request through a company-run server
that did the real fetching, JS execution, and TLS, then sent the phone a
compressed, transcoded result. j2me-browser does the opposite: **everything
runs on-device** — HTTP(S), HTML parsing, and rendering. No server
dependency, no single point of failure beyond the phone itself. Full
rationale: [ADR-0001](docs/adr/0001-no-proxy-self-sufficient-client.md).

The rendering bar is intentionally modest: think ~2011 Android Gingerbread's
stock browser, not a modern desktop-grade engine. Real HTTP(S) and real (if basic)
page content, not a fully modern layout engine on 2009-era silicon — that
expectation would be wrong to hold, and holding it is how these projects
usually die from scope creep.

## Status

- ✅ Build/install pipeline (compile → preverify → package → OTA/Bluetooth install)
- ✅ Live network I/O — the MIDlet fetches `http://info.cern.ch/` over plain
  HTTP on startup and renders the result, proving the transport stack works
  end to end on both emulator and real hardware
- ✅ Fetched bytes are decoded as UTF-8 and rendered as word-wrapped,
  scrollable text on screen (raw HTML markup, since there's no HTML parser
  yet — tags show up literally rather than being stripped/styled)
- ✅ HTTP redirects (301, 302, 303, 307, 308) are followed automatically,
  up to 5 hops
- ✅ Session cookies (`Set-Cookie`/`Cookie`, in-memory, per-host, no
  persistence across runs)
- 🚧 Everything else below is planned, not yet built

## How much of a browser is this?

**In scope:**
- HTTP and HTTPS
- Basic HTML: headings, paragraphs, lists, tables, links
- Simple inline formatting: bold, italic, font sizes
- Images, scaled down to fit the screen
- Basic forms: text fields, checkboxes, submit buttons
- A tiny CSS subset: font size, alignment, margins — nothing more
- Local history, bookmarks, downloads, and page saving

**Explicitly out of scope, permanently — not "later," ignored by design:**
- JavaScript
- Frames / iframes
- Video and audio playback
- Modern CSS (flexbox, grid, animations, media queries, ...)
- Pixel-perfect fidelity with how a page renders on a modern desktop browser

Pages that depend on the "out of scope" list will render degraded or blank,
the same way they did on real feature-phone and early-smartphone browsers
from this era. That's expected behavior, not a bug to chase.

## Architecture & Roadmap

Work is organized into four layers. TLS is deliberately decoupled from
parsing and rendering — the plan is to prove the parse → layout → render
pipeline against plain HTTP first, since it's independently testable and
doesn't need to wait on the hardest problem in the project.

### 1. Transport
- [x] Plain HTTP client
- [x] Redirects (301, 302, 303, 307, 308, up to 5 hops)
- [x] Cookies (session-only, in-memory, name=value pairs per host)
- [ ] TLS / HTTPS — see [HTTPS / TLS plan](#https--tls-plan)
- Compression (gzip) is being sidestepped entirely by not sending
  `Accept-Encoding` — most servers only compress if asked, and CLDC 1.1 has
  no `java.util.zip` to decompress with anyway.

### 2. Parsing
- [x] UTF-8 → `char` decoding
- [ ] A tolerant ("tag soup") HTML parser — the real complexity here is
  handling malformed real-world markup gracefully, not the tag set itself
- [ ] A minimal CSS parser for the font-size/alignment/margin subset above

### 3. Rendering / UI
- [x] Text layout: word wrapping (including hard-breaking tokens like long
  URLs that don't fit the screen on their own) and scrolling
- [ ] Bold/italic/size (blocked on the HTML/CSS parser above — there's no
  markup structure to style yet, just a flat decoded string)
- [ ] Image scaling (J2ME's `Image.createImage(byte[])` already decodes
  PNG/JPEG natively — this module is scaling logic, not a decoder)
- [ ] Link hit-testing from the layout pass
- [ ] Form input handling (text fields, checkboxes) and submit-request
  building

### 4. App shell
- [ ] History, bookmarks, downloads, and page saving via MIDP `RecordStore`
  — mechanically simple, no research risk, can be built in parallel with
  anything above

## HTTPS / TLS plan

This is the hardest and highest-risk part of the project, so it gets its
own section.

CLDC 1.1 has no `java.math.BigInteger` and no built-in crypto — there is no
standard-library HTTPS to fall back on, and no actively-maintained
CLDC-targeting crypto ecosystem to depend on either. Whatever ships here
*is* the long-term-maintained version; there's no upstream project to defer
to. That constraint drives the design:

- **Primitives (AES, SHA-2, RSA/EC big-integer math) vs. protocol
  (handshake, record layer, cipher negotiation) are architecturally
  separate** — primitives are adapted from existing prior art, protocol
  logic is implemented directly against the IETF RFCs. Full rationale:
  [ADR-0002](docs/adr/0002-tls-primitives-vs-protocol-split.md).
- **One deliberately narrow cipher suite, not a flexible negotiator:**
  ECDHE (P-256) key exchange + AES-GCM + SHA-256, with RSA key exchange
  skipped entirely rather than deprioritized. Full rationale:
  [ADR-0003](docs/adr/0003-ecdhe-p256-aes-gcm-cipher-suite.md).
- Rough performance target, based on scaling a sourced ECC benchmark
  (174-bit curve, ~400ms on a 104MHz ARM7) up to this project's baseline
  hardware (~235MHz): a P-256 operation should land somewhere around
  ~175ms — acceptable UX, not a multi-second stall.

## Device & compatibility target

CLDC 1.1 / MIDP 2.0 is the floor, and it's the broadest reasonable one:
it's the de facto standard that Nokia (S40 and S60), Sony Ericsson,
Samsung, Motorola, and LG all converged on by the mid-2000s. (BlackBerry OS
only ever implemented MIDP 2.0 partially, and KaiOS-era "dumbphones" use an
HTML5 app model rather than J2ME at all — both out of scope.)

The baseline development/test hardware is an early-to-mid Series 40
feature phone (~200-300MHz ARM9-class CPU, mid-2000s generation) — real
hardware in hand, not a hypothetical. It's chosen deliberately as the
*harder* constraint rather than the easiest one: more capable devices, like
later Asha-platform-era hardware (~1GHz ARM11 class) — also real hardware
in hand — run the same build comfortably with no special-casing or
separate build variant required. Full rationale:
[ADR-0004](docs/adr/0004-device-baseline-constrained-floor.md).

## Getting Started

### Prerequisites

Install these before running `make setup`:

- `java`, `javac`, `jar` (JDK required, recommended: Temurin/OpenJDK 17)
- `curl`
- `unzip`

Detailed Java install instructions: `docs/java-setup.md`

### Quick Start

```bash
make setup
make run
```

`make run` builds the MIDlet and launches it in MicroEmulator.

### Commands

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
- `docs/adr/` — Architecture Decision Records: the why behind foundational,
  hard-to-reverse choices (no-proxy design, TLS split, cipher suite,
  device baseline), one file per decision, starting with
  [ADR-0000](docs/adr/0000-use-architecture-decision-records.md) on why
  this repo uses ADRs at all

As the modules described in [Architecture & Roadmap](#architecture--roadmap)
land, this layout will grow to reflect them (e.g. separate packages for
transport, parsing, rendering, and TLS) rather than staying a single file.

## Feature requests & bugs

- [Request a feature](https://github.com/AbhyasKanaujia/j2me-browser/issues/new?template=feature_request.yml)
- [Report a bug](https://github.com/AbhyasKanaujia/j2me-browser/issues/new?template=bug_report.yml)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Short version: you don't need to own
real feature-phone hardware to contribute. Development and most testing
happens against the vendored MicroEmulator in `tools/open/` — real devices
matter for final validation (some bugs, like OS-level network permission
prompts, only show up there), but aren't required to write or review code.

## License

MIT — see [LICENSE](LICENSE).
