# Public export checks — 23 October 2026

Performed on macOS arm64 with an isolated JDK 21 and Android platform/build tools 35; launcher used the existing Android 34 SDK and its pinned Gradle wrapper. Build outputs and generated signing keys stay local and are not committed.

| Public packaging check | Result |
|---|---|
| Python offline unittest suite | 11 passed |
| Python source compile check | Passed |
| Fresh Python 3.13 environment | Requirements installed; adb-shell0.4.4/androidtvremote2 0.3.2; same 11 tests passed |
| Public export | 44 source/doc files; no runtime/binary paths or known private session identifiers; local Markdown links resolved |
| ADB Control public source | Built; APK signature verification passed |
| Bridge public source with configurable host address | Built; APK signature verification passed |
| PackageGuard public source | Compiled to DEX JAR |
| Upstream fetch script | Fetched exact pinned cleanyourtv revision |
| Launcher wrapper build | Full release build passed, 44 tasks executed |
| Local HTTP host | Both APK downloads matched local SHA256 |
| Host file allowlist | Unlisted README URL returned 404 |
| Bridge registration | Accepted a synthetic loopback registration; token file mode 0600 |

The loopback smoke test used a synthetic token, not a real TV's credentials. It stopped the server afterwards. No real TV was contacted during these public packaging checks, and the completed original TV's ADB was not reopened. Real-device outcomes and the limits of their evidence are in [validation.md](validation.md).

The new original-property restore button, model gate, visible unlock entry and portable Python setup/restore wrappers were **not run against the original device**. Compile success/offline tests do not substitute for that runtime verification. Newly generated APK signatures differ from the original private session's keys; a clone does not contain an update key for that installation.
