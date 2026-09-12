# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and versioning follows [Semantic Versioning](https://semver.org/) (tracked
via `MIDlet-Version` in `app.jad`).

## [Unreleased]

### Added
- Live HTTP fetch on startup: the MIDlet requests `http://info.cern.ch/` on
  a background thread and shows the response code and byte count on screen,
  proving the network stack end to end. Verified on emulator and real
  hardware.
- Fetched response bytes are now decoded as UTF-8 and rendered as
  word-wrapped, scrollable text (UP/DOWN) instead of being discarded after
  counting. Long unbroken tokens (e.g. URLs in raw `href` attributes) are
  hard-broken by character since the display has no horizontal scroll.
  Still shows raw HTML markup as text — no tag-soup parser yet. Verified in
  MicroEmulator.
- HTTP redirects (301, 302, 303, 307, 308) are now followed automatically,
  up to 5 hops. `Location` headers are resolved by hand (absolute,
  protocol-relative, absolute-path, and same-directory-relative forms) since
  CLDC 1.1 has no `java.net.URL`. The status line notes how many redirects
  were followed. Verified in MicroEmulator against a real cross-protocol
  redirect (`http://github.com/` → `https://github.com/`), which is the one
  redirect case desktop Java's `HttpURLConnection` — what MicroEmulator
  wraps — won't silently auto-follow on its own, so it actually exercises
  this code path rather than masking it.
- Full project README: scope, architecture/roadmap, TLS plan.
- Architecture Decision Records (`docs/adr/`) recording the rationale
  behind foundational, hard-to-reverse decisions: using ADRs at all,
  the no-proxy self-sufficient design, the TLS primitives/protocol split,
  the ECDHE P-256 + AES-GCM cipher suite choice, and the constrained-device
  baseline. README now links to these instead of duplicating the full
  rationale inline.

## [0.1.0] - Initial commit

### Added
- Hello World MIDlet: build/install pipeline, LCDUI UI, OTA/Bluetooth install.
