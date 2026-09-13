# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and versioning follows [Semantic Versioning](https://semver.org/) (tracked
via `MIDlet-Version` in `app.jad`).

## [Unreleased]

### Added
- Back/Forward navigation: `Back` and `Forward` commands walk an in-session
  history stack, re-fetching each page rather than caching content. Going
  to a new address clears any forward history. Verified in MicroEmulator.
- HTML tags are now stripped to plain readable text instead of showing raw
  markup, with `<script>`/`<style>` content dropped and common entities
  (`&amp;`, `&copy;`, `&mdash;`, etc.) decoded. No block/paragraph structure
  yet -- that's layout, a later stage. Verified in MicroEmulator.
- Dropped the "HTTP 200 - N bytes" status line for successful page loads --
  it was only ever useful for proving the network stack worked, and now
  just eats screen space. Still shown for non-2xx responses and failures.
- Block-level structure: paragraphs, headings, and list items now start on
  their own line instead of running together, with list items getting a
  `"- "` prefix. A text-flow approximation, not real block-box layout --
  see [ADR-0005](docs/adr/0005-staged-text-flow-rendering.md). Verified in
  MicroEmulator.
- Touch scrolling: drag-to-scroll for full-touch devices (e.g. the Asha UI)
  that have no D-pad, so no key event ever fires. Whole-line scrolling,
  same as the existing key-based scroll -- no smooth/inertial scrolling.
  Verified on real hardware.
- History: visited pages are recorded via `RecordStore` (page title when
  available, falling back to the URL) and browsable from a "History"
  command, most recently visited first. Verified in MicroEmulator.
- Inline formatting: `<b>`/`<strong>`, `<i>`/`<em>`, and `<h1>`-`<h6>`
  (bold plus a larger size) now render styled instead of as plain text,
  including nested combinations like bold-containing-italic. Verified in
  MicroEmulator, including on a real page (`info.cern.ch`'s own `<h1>`).
- Link navigation: `<a href>` links render blue and underlined, with one
  link at a time focused (highlighted). UP/DOWN moves focus to the next
  visible link, scrolling one line at a time toward it (never jumping) when
  it isn't on screen yet; the fire/select key activates the focused link.
  Touch-only devices (no fire key) activate by tapping the link directly.
  In-page anchors (`#...`) and `javascript:` links are safely ignored
  rather than mis-navigating. Verified in MicroEmulator and on real
  hardware, including touch activation.

## [0.2.0] - 2026-09-12

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
  up to 5 hops, with `Location` header resolution handled by hand (no
  `java.net.URL` in CLDC 1.1). Verified in MicroEmulator and on real
  hardware.
- Session cookies: `Set-Cookie` responses are captured (name=value only) into
  an in-memory, per-host jar and echoed back via `Cookie` on later requests.
  No persistence across runs. Verified in MicroEmulator and on real hardware.
- Go to address: a "Go to..." command opens a URL entry screen and reuses
  the existing fetch/render pipeline, so the MIDlet can now visit any
  address, not just the hardcoded startup URL. Also fixes stale page
  content lingering on screen after a failed fetch. Verified in
  MicroEmulator and on real hardware.
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
