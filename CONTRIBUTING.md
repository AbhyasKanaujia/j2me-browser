# Contributing

## Report a bug

Open a [bug report](https://github.com/AbhyasKanaujia/j2me-browser/issues/new?template=bug_report.yml). Include whether you hit it in MicroEmulator or on a real device, and the exact steps.

## Request a feature

Open a [feature request](https://github.com/AbhyasKanaujia/j2me-browser/issues/new?template=feature_request.yml) describing the problem you're trying to solve, not just the solution. Check the README's [Architecture & Roadmap](README.md#architecture--roadmap) and [scope](README.md#how-much-of-a-browser-is-this) sections first — some things (JavaScript, frames, video/audio, modern CSS) are permanently out of scope by design, not just unbuilt yet.

## Contribute code

1. Fork the repo and clone it.
2. `make setup` — downloads the pinned toolchain (ECJ, CLDC/MIDP API stubs, ProGuard, MicroEmulator) into `tools/open/`. See `docs/java-setup.md` first if you don't have a JDK.
3. Make your change. If it's user-facing, add an entry under `[Unreleased]` in [CHANGELOG.md](CHANGELOG.md) ([Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format).
4. `make build` (compile → preverify → package) and `make run` to try it in MicroEmulator.
5. There's no automated UI test suite — feature-phone UI can't be meaningfully scripted. Actually run the change in MicroEmulator (and on real hardware if you can) before opening a PR, and describe what you tested in the PR description.
6. Open a pull request explaining what the change does and why.

### Where things live

- `src/MainMIDlet.java` — MIDlet entry point, the `Canvas` subclass, and all rendering logic (currently a single file; see `AGENTS.md` for how this is expected to split up as more modules land).
- `app.jad` — app descriptor (name/version/vendor/permissions).
- `Makefile` — build pipeline; `make help` lists commands.
- `testdata/` — local HTML fixtures for exercising the rendering pipeline without depending on external sites' content staying stable; served automatically by `scripts/serve.py` at `http://<host>:8765/testdata/<file>`, no separate server needed.
- `docs/adr/` — Architecture Decision Records: the rationale behind foundational, hard-to-reverse decisions (no-proxy design, TLS split, cipher suite, device baseline, staged text-flow rendering).
- `docs/index.html` — the GitHub Pages landing page (install link + project summary).

### Why plain HTTP only, for now

There's no on-device TLS yet — CLDC 1.1 has no `java.math.BigInteger` and no built-in crypto, so HTTPS support has to be built from scratch rather than pulled off a shelf. See [ADR-0002](docs/adr/0002-tls-primitives-vs-protocol-split.md) and [ADR-0003](docs/adr/0003-ecdhe-p256-aes-gcm-cipher-suite.md) for the plan. Until that lands, any new HTTP call needs to be verified to work over plain `http://` — most modern APIs and sites force-redirect to HTTPS.
