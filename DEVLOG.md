# Developer Log

Technical/internal notes on how this project has been built — not a
user-facing changelog. See [CHANGELOG.md](CHANGELOG.md) for what's new
from a user's perspective.

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
