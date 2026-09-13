# Developer Log

Technical/internal notes on how this project has been built — not a
user-facing changelog. See [CHANGELOG.md](CHANGELOG.md) for what's new
from a user's perspective.

## 2026-09-13

- Extracted `Theme.java`: color constants (text, background, link, link
  highlight) that were previously inline `0xRRGGBB` literals scattered
  through `BrowserCanvas`'s paint code. No behavior change -- this is
  purely so an eventual user-configurable theme setting is a values swap
  in one small file, not a rendering-logic change.
- Added `testdata/alice-test.html`, a real Project Gutenberg excerpt
  (Alice's Adventures in Wonderland, ch. 1) used to test link navigation
  and scrolling against real, readable prose instead of Lorem Ipsum --
  much easier to judge "did I lose my place while scrolling" against text
  a human can actually follow. Includes a `#`-anchor link to exercise the
  in-page-anchor guard in `MainMIDlet.onActivateLink`.
- Split `MainMIDlet.java` (718 lines, four tangled concerns) into
  `MainMIDlet.java` (orchestration), `Http.java` (transport), `HtmlText.java`
  (parsing), and `BrowserCanvas.java` (rendering) -- flat default package,
  no behavior change. See
  [ADR-0006](docs/adr/0006-one-file-per-concern.md) for the rationale and
  the five real-world projects (`tunemeta-midlet`, `discord-j2me`, LWUIT,
  Dillo, Lynx) researched to ground the file boundaries and the decision
  to stay in the default package rather than introduce sub-packages.
- Real-device finding: the Asha 500's native browser fails to load
  local/LAN sites, reporting expired certs -- its platform cert store is
  frozen from whenever the device shipped and has rotted since. This
  browser is unaffected since it's plain HTTP with no cert validation
  involved yet, but it's a concrete illustration of why the TLS plan
  (ADR-0002) bundles its own trust material instead of depending on the
  OS's cert store: an unmaintained platform cert store is exactly the kind
  of rot a from-scratch, actively-maintained implementation can avoid.
- Added `testdata/` for local HTML test fixtures (starting with
  `scroll-test.html`, covering entities/lists/long paragraphs for
  exercising the tag-stripping and scrolling code paths without depending
  on external sites' content staying stable). No new server code needed --
  `scripts/serve.py` already serves the whole repo root as static files,
  so anything dropped in `testdata/` is automatically reachable at
  `http://<host>:8765/testdata/<file>`. Also doubles as a real exercise of
  the no-proxy design's ability to reach local/LAN addresses directly (see
  [ADR-0001](docs/adr/0001-no-proxy-self-sufficient-client.md)).

## 2026-09-12

- Adopted the project-scaffolding conventions worked out in the sibling
  project `tunemeta-midlet` (same toolchain, same target device baseline):
  `AGENTS.md`, this file, `CONTRIBUTING.md`, MIT `LICENSE`, GitHub issue
  templates, and CI/release GitHub Actions workflows. `CHANGELOG.md`/
  `DEVLOG.md` split follows the same rule in both repos: user-facing "what
  changed" vs. internal "why/how", respectively.
- Release workflow uses fixed (non-versioned) asset names
  (`j2me-browser.jar`/`j2me-browser.jad`) and rewrites `MIDlet-Jar-URL` in
  the uploaded `.jad` to the absolute
  `.../releases/latest/download/j2me-browser.jar` URL before uploading —
  needed so the GitHub Pages landing page (`docs/index.html`) can link a
  stable install URL without tracking the current version itself.
