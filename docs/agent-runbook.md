# Agent runbook: an authorized HPH07 session

Use this only when the owner asks you to work on their physical box. The [setup guide](setup.md) contains commands and recovery branches; the steps below define what evidence is needed before moving on.

## 1. Establish scope and prerequisites

Read `README.md`, `docs/setup.md` and `docs/validation.md`. Ask for the TV address if the owner has not supplied it. Confirm **HPH07 / Android9 / firmware** from the device UI or a trusted owner screenshot. For another model, stop the HPH07 unlock branch and investigate that model separately; a familiar SoC or old patch date is not compatibility evidence.

Ask what is in USB ports and separately whether the network uses USB Ethernet. Use Wi-Fi/VPN information the owner provides. Preserve Live TV, streaming/DRM, input/remote, assistant/casting and updates by default. Resolve dock/apps preferences in one short question unless the owner has already delegated them. Reuse prior authorization; avoid repetitive permission questions.

**Done:** exact device/address/network path and authorized scope are recorded in ignored `.state/checkpoint.md`, with no assumption that ADB already works.

## 2. Bootstrap before ADB

When ADB is unavailable or the owner is tired of photos, read [bootstrap.md](bootstrap.md) first. Pairing provides buttons, not screenshots. Aim for a consented Bridge screenshot before the vendor unlock; if authenticated ADB already works, use its screenshots and continue to the backup phase.

Build the local helper APKs. Pair the native remote, then install Bridge with **Downloader by AFTVnews (`com.esaba.downloader`)** and the known LAN APK URL. If letters fail but digits work, follow the numeric-short-code fallback in bootstrap.md, including its APK-only tunnel and CAPTCHA handoff. Prefer a working LAN/manual keyboard over that fallback. Ask the owner only for physical actions the available channel cannot perform. A download prompt is not an installation result.

If installation is refused across verified methods, record the exact failure and end this branch; don't improvise a firmware flash, factory menu or unverified SD boot. If a TV browser cannot open a downloaded APK, use its download view or Downloader instead of repeating the same click. Never use another person's keys or an opaque “unlock APK”.

**Done:** Bridge registered, Android capture consent was granted and the agent fetched **and inspected** a real screenshot, or the concrete bootstrap blocker is documented. Keep any temporary APK endpoint alive until installation is verified, then stop it. Credentials/diagnostics remain LAN-only.

## 3. Unlock and authenticate

Use the newly visible screen and paired remote to install/open the vendor helper. Record the original property before writing. Follow Probe → Unlock → read-back → close temporary vendor API debug session → Android USB debugging toggle. Warn the owner just before the ADB authorization prompt; match the generated key fingerprint. Run `id`.

**Done:** property read-back true, switch usable, and authenticated uid2000 shell evidenced. A port open or a broadcast sent is insufficient. The protected broadcast in the original investigation **failed**; the Binder route was the successful one.

## 4. Back up, then maintain

Run the complete backup before package/Home/settings changes. If the owner wants an uninterrupted work session, grant the secure-settings permission only to our helper and start its bounded maintenance session. Stock TrueID can still switch ADB off; this observer is not policy removal. Preserve all first originals on rerun. Avoid logging any pairing code, token, account, serial or key into a public document.

**Done:** full private package/disabled/properties/settings backups and a restorable ledger exist. Explicitly note that the first complete ADB snapshot is after the initial property unlock.

## 5. Hand over Home without breaking TrueID

Follow [upstream integration](upstream-integration.md). Confirm installed launcher APK hash, save and set its per-package uninstall block, then merge/enable HomeKeyService. Keep TrueID enabled: its Home activity also plays Live TV. Keep theme `stock_home_redirect` **empty** on this firmware: redirecting `com.truedmp.idtv` also ejects the Live TV player. Never claim a native HOME takeover solely from `set-home-activity` printing Success.

Check physical Home and a held volume key with the owner. Injected Home does not prove the accessibility path. Capture and **view** screenshots after changes; let transitions settle. If capture is black/empty during protected video, check the foreground activity/media session and obtain a real-screen report instead of treating capture failure as breakage.

**Done:** physical Home reaches the new launcher, repeat behavior is known, and rollback to the original accessibility/protection values is recorded. Note that boot may initially show TrueID.

## 6. Cleanup and playback

Use only the two-package debloat command, with one `pm` call per package and actual disabled-list checks. If every disable is refused, stop retrying. Inspect screenshots between changes. Reboot, compare the disabled list, confirm the launcher remains installed, and check Home again. Test Live TV plus the owner's subscribed DRM service with real picture/audio; Netflix is not mandatory. Ask only for the one human playback check when protected capture prevents verification.

**Done:** each claimed result has a device observation or owner report; unknown services/resolutions/HDR remain explicitly untested. Don't claim general speed gains without a before/after measurement or removal of in-stream ads from a launcher replacement.

## 7. Close and hand over

Follow [restore.md](restore.md): stop maintenance, revoke ADB authorizations, turn debugging off, reboot, and get explicit ConnectionRefused on port5555 while the box is otherwise reachable. End Bridge and local host/remote processes. Preserve the visible helper and property unlock only as documented owner conveniences, with no automatic maintenance at boot.

**Done:** checkpoint contains final status, exact changes, restore command, remaining limitations and port-closure evidence. Give the owner links to their local guide and recovery instructions. A timeout is not closure evidence; missing human confirmation remains pending rather than reported as success.

## Private checkpoint format

Keep `.state/checkpoint.md` small: device/build; current address/channel; completed phases with evidence paths; original values/ledger location; next action; pending human input; open services and their shutdown steps. Keep credentials in ignored state files, never in the checkpoint text. Update it before context compaction or an unavoidable handoff. Do not create quota/account-switching automation as part of this TV toolkit.
