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
