# Evidence, scope and reproducibility

## Original real-device session

| Check | Result |
|---|---|
| Model / Android / firmware | HPH07 / 9 (SDK28) / P2.2.0_20231207 |
| TrueID application inspected | 25.02.21.22 |
| RAM reported by device | About 1.9 million KB, approximately 2 GB |
| Property gate | Empty → true; read-back and Settings toggle verified |
| Temporary vendor API flag | Reset to release after the helper bind |
| ADB | Own key authorized; uid2000 shell, not root |
| Visible ADB helper | OFF → port refused; ON through paired remote → shell worked |
| Stock automatic ADB-off policy | Still present; maintenance observer is a workaround |
| Launcher | cleanyourtv 2.6 at pinned commit, config-only customization |
| Vendor launcher cleanup | Seen in inspected code and observed disappearance; launcher protected from deletion and survived cold-start/reboot |
| Physical Home | Owner confirmed; fresh activity/screen evidence matched new launcher |
| Held volume | Owner confirmed continuous repeat with helper enabled |
| Debloat | Exactly two reporting packages; original disabled packages preserved |
| Rollback | Original session's packages-only restore actually executed, original disabled set matched, then cleanup reapplied |
| Reboot | Launcher installed, uninstall block true, disabled set unchanged |
| Live TV | Changing Thai PBS broadcast frames observed after changes |
| HBO Max | Installed from Play Store; owner confirmed actual content picture and sound after reboot |
| Netflix / Prime | Installed/opened but subscription playback not validated; no Netflix subscription |
| Final access closure | Guard stopped, Android revoke dialog confirmed, USB debug off, reboot; port5555 explicitly refused while remote service returned |

Private evidence is intentionally not shipped: raw screenshots/settings/APKs can contain account names, serials, Wi-Fi names, tokens or proprietary code. This is a documented report of one session, not independently reproduced evidence across multiple boards. Neither HBO playback nor an empty DRM dump establishes Widevine level, HD/4K eligibility, HDR or every service's compatibility.

## Public packaging changes

The public source derives from the working helpers, with portable paths, per-user keys, an explicit computer IP for Bridge registration, a model/SDK check in the vendor-unlock UI, a visible unlock entry and an optional original-empty-property restore button. Python wrappers now keep runtime data in `.state/` and fetch the pinned launcher separately. These changes are **not represented as having been reinstalled and device-tested**; the original TV was left finished with ADB closed.

Offline tests cover transfer integrity/path handling, preservation of existing accessibility services and generation of narrow restore plans. APKs and the one-shot DEX helper are compiled and signature-checked with the local Android toolchain. See the checked-in `validation-results.md` for the actual public packaging checks performed. Cross-platform runtime support and other firmware remain unverified.

## What is not claimed

- No permanent removal of the vendor's ADB policy, native HOME priority takeover, boot-direct-to-custom-launcher guarantee or root shell.
- No SD boot image, generic UART unlock or matching kernel exploit.
- No measured before/after CPU, RAM or launch-latency improvement. Dormant reporting services do not explain the original sluggishness by themselves.
- No removal of all TrueID/in-stream ads, or YouTube video ad blocking.
- No full automatic restore test on the public wrapper; only the original packages-only rollback was run on the device.
- No prebuilt vendor APKs/firmware or signing keys. APK byte hashes vary across newly generated signing keys; buildable source is the reproducibility target, not identical signing identity.

## Later regression verified on the same device

A later private sleep/power session added a TrueID-wide `stock_home_redirect`, which also redirected Live TV because of the shared MainActivity. Clearing only that theme value restored the same shortcut probe; the owner confirmed picture/audio, physical Home and reopening Live TV, followed by successful reboot and sleep/wake tests. See [livetv-regression.md](livetv-regression.md). This later fix preserved the owner's later always-on ADB/server guard arrangement; the original closure results above describe the earlier handover, not the current private maintenance policy.
