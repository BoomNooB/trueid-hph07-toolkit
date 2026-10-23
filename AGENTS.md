# Working on this repository

Choose the task before contacting a device.

- **Set up an owner's TV:** read [docs/agent-runbook.md](docs/agent-runbook.md), then follow its gates. A clone or code review alone does not authorize contacting a TV. Use the owner's address; this toolkit performs no discovery scan.
- **Change tools/docs:** use offline tests in `tests/` and build helpers with `tools/build.py`. Device access is unnecessary unless the owner explicitly requests a live test. Report public packaging checks separately from the original real-device evidence in [docs/validation.md](docs/validation.md).
- **Work on the launcher:** read [docs/upstream-integration.md](docs/upstream-integration.md). Fetch the pinned cleanyourtv source into `.deps/`, follow its `AGENTS.md`/`PROMPT.md`, and keep this board's adaptation in `launcher/hph07-config.xml`.
- **Publish:** stage an explicit file list or review `git diff --cached` before committing. `.state/`, builds, credentials, APKs, vendor extracts and raw TV screenshots are local runtime material. Read [docs/publishing.md](docs/publishing.md) before any public push.

All commands run from the repository root. Runtime originals live in `.state/ledger.json`; retain first originals when rerunning. Never reuse a different owner's signing/ADB keys or ledger. Update the private checkpoint below after each completed device phase so another agent can resume without guessing.
