# AGENTS.md

- Env: this is a J2ME (MIDP 2.0 / CLDC 1.1) project, not modern Java. Source
  is compiled with ECJ targeting `-source 1.3 -target 1.1` against CLDC/MIDP
  API stubs, not a regular JDK classpath — see `Makefile`. Don't assume any
  API newer than that baseline is available (no generics, no annotations,
  no `java.util.StringTokenizer`, no `java.net.*`, no JSON classes, no
  `java.util.regex`, no varargs, no enhanced `for` over non-arrays, no
  `java.util.zip`, no `java.math.BigInteger`, no built-in crypto, without
  first confirming CLDC 1.1 actually supports it).
- Build/run: `make setup` (once, downloads the pinned toolchain) then
  `make build` (compile → preverify → package) or `make run` (build + launch
  in MicroEmulator). `make doctor` shows what's missing.
- Test: there's no automated test suite — feature-phone UI can't be
  meaningfully scripted. Don't try to automate it with throwaway scripts;
  build and hand the change to the user to validate in MicroEmulator or on
  real hardware instead. For rendering/parsing changes, prefer pointing
  `FETCH_URL` at a fixture in `testdata/` (served locally by
  `scripts/serve.py`, no setup needed) over an external site — fixtures
  don't change out from under you and can be crafted to hit a specific
  code path.
- Network: only plain HTTP works today — there is no on-device TLS yet (see
  `docs/adr/0002-tls-primitives-vs-protocol-split.md` and
  `docs/adr/0003-ecdhe-p256-aes-gcm-cipher-suite.md` for the HTTPS plan and
  why it's a separate, high-risk effort). Before adding a new HTTP call,
  confirm with `curl` that the exact endpoint works over `http://`, not just
  `https://` — most modern APIs force-redirect to HTTPS and will silently
  break this app. The sibling project `tunemeta-midlet` has a worked example
  of this kind of endpoint research in its `DEVLOG.md`.
- Scope: JavaScript, frames/iframes, video/audio playback, and modern CSS
  are permanently out of scope, not deferred — see the README's "How much
  of a browser is this?" section before adding anything in that territory.
- Layout: one file per concern, flat default package (no `package`
  declarations) — `MainMIDlet.java` (MIDlet lifecycle, commands,
  navigation stack), `Http.java` (transport: fetch/redirects/cookies),
  `HtmlText.java` (parsing: tag stripping/entities/block structure),
  `BrowserCanvas.java` (rendering: word-wrap/paint/scroll). New roadmap
  layers (CSS engine, forms, history/bookmarks) get their own new file,
  not an addition to one of these four. See
  `docs/adr/0006-one-file-per-concern.md` for the rationale and the
  real-world projects (`tunemeta-midlet`, `discord-j2me`, LWUIT, Dillo,
  Lynx) it's grounded in.
- Keep `CHANGELOG.md` up to date per
  `https://keepachangelog.com/en/1.1.0/`. It's user-facing ("what's new"),
  not a dev log — no internal/technical details (root causes, research
  trails, refactors). Put those in `DEVLOG.md` instead.
- Versioning is tracked via `MIDlet-Version` in `app.jad` (semantic
  versioning) — there's no separate version file to keep in sync.
