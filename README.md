# TrueID HPH07 Toolkit

From a greyed-out debugging switch to an accessible Android TV box: source, reversible tools and a Thai case study for **TrueID Skyworth HPH07, Android 9**.

**[อ่านบทความภาษาไทย →](blog/from-locked-box-to-usable-tv.th.md)** · [Setup](docs/setup.md) · [Restore](docs/restore.md) · [Evidence and limits](docs/validation.md)

The owner confirmed physical Home opens the new launcher, held volume still repeats, and HBO Max plays with picture and sound after reboot. Live TV also worked. No root, firmware flashing or DRM provisioning changes were used.

This is a device-specific investigation and toolkit, not a universal unlocker. Tested firmware: `P2.2.0_20231207`, Android security patch December 5, 2023; TrueID app `25.02.21.22`. A similar-looking box can have a different service or policy.

| Problem | What worked | Remaining limit |
|---|---|---|
| USB debugging switch greyed out | Narrow vendor Binder call sets `persist.truetv.adbcommand=true` | Requires installing our local helper first; other firmware untested |
| TrueID turns ADB off again | Visible, explicitly started six-hour maintenance observer | Vendor policy remains; stopping/expiry ends the observer, not necessarily the open port |
| TrueID deletes third-party HOME apps | Package-manager uninstall block for our launcher only | Original false value is recorded for restore |
| Native HOME priority still favors TrueID | cleanyourtv's HomeKeyService handles physical Home | Boot can initially show TrueID; press Home |
| Slow/ad-heavy stock home | Lightweight cleanyourtv launcher | No measured general speedup; in-app Live TV ads can remain |
| Opening Live TV always shows TrueID's full home grid/menu first | `android/livetv-shortcut/` deep-links straight to the player (`truedmpatv://trueidapp/LIVE/<channel>`) | Channel numbering is TrueID's own index, differs per box; a bare `LIVE/` with no index crashes the app (real bug, see device findings) |
| TrueID stays resident (~88MB, pinned by two silent foreground services) even when not in use | `tools/ram_guard.py` force-stops it (and any other app you name) whenever it's not foreground | `pm disable` on its individual components is blocked by Android itself, not by TrueID; must run as a continuous outside watcher, not from the TV |
| Live TV pre-roll ads | Traced to Firebase Remote Config + Google IMA/doubleclick/PubMatic, documented with real DNS capture | No verified non-destructive block yet; the ad-decision domains are separate from content CDN domains but blocking them is untested end-to-end (see device findings) |

## Repository map

```text
android/adb-control/    Vendor unlock UI + bounded, user-controlled ADB maintenance
android/bridge/         Consent-based screen sharing and authenticated read-only diagnostics
android/package-guard/ One-shot uninstall protection for com.gathin.tvhome only
android/livetv-shortcut/ Trampoline tile: deep-links straight into TrueID's Live TV, skipping its menu
tools/                 Portable build, pairing, APK hosting, ADB, backup and restore
tools/ram_guard.py     Continuous watcher (run it off-TV, e.g. a home server): force-stops named
                       packages whenever they aren't foreground, for apps that pin themselves with
                       a silent foreground service and so resist normal background eviction
launcher/              HPH07 configuration for a pinned upstream launcher
docs/                  Setup, recovery, validation and device findings
blog/                  Thai long-form explanation, ready to edit for publication
examples/              Non-private configuration examples
tests/                 Offline safety/restore/transfer checks
```

Runtime data belongs in ignored `.state/`; builds in ignored `build/`; upstream source in ignored `.deps/`. Keep signing keys if you want to update your own installations later. Each fresh clone generates its own keys, so it cannot silently update an APK signed by somebody else's private key.

## Start here

Use Python 3.10+, Git, JDK 21 and the Android SDK. The setup guide explains installing an APK **before ADB is available**, including the TV browser/Downloader path and remote pairing.

```sh
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
export TV_HOST=192.168.1.50    # replace with YOUR box address; no scanning
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
sdkmanager 'platforms;android-35' 'platforms;android-34' 'build-tools;35.0.0' 'build-tools;34.0.0'

python tools/build.py adb-control
python tools/build.py bridge --host-ip 192.168.1.20  # YOUR computer LAN IP
python tools/build.py package-guard
python tools/host.py --bind 192.168.1.20
```

The host serves only known helper APK paths, not your working directory. On the TV open `http://192.168.1.20:18765/`. Install the helper, use its **HPH07 vendor unlock** entry, probe the property, unlock it, and close the temporary vendor API debug session. Then enable USB debugging in Android Settings and accept your computer's ADB key. [Detailed setup](docs/setup.md) covers the separate screen-sharing consent and the maintenance permission grant.

With ADB authenticated:

```sh
python tools/tv.py shell 'id'
python tools/setup.py backup
python tools/setup.py maintenance
python tools/upstream.py
python tools/launcher.py --install
python tools/setup.py home
# Check physical Home, held volume and streaming before disabling anything.
python tools/setup.py debloat
python tools/tv.py screenshot .state/after.png

# Optional: a dock/Inputs tile that opens straight into Live TV, skipping TrueID's own
# menu, and a continuous off-TV watcher that force-stops apps pinned by a silent
# foreground service (TrueID itself is one) whenever they're not in use.
python tools/build.py livetv-shortcut
python tools/tv.py push build/livetv-shortcut/livetv-shortcut.apk /data/local/tmp/livetv.apk
python tools/tv.py shell 'pm install -r /data/local/tmp/livetv.apk'
# then point cleanyourtv's config.json dock_apps/live_tv_package at local.tv.livetv
# instead of com.truedmp.idtv — see docs/device-findings.th.md for the exact keys.
python tools/ram_guard.py com.truedmp.idtv   # run this continuously somewhere that's on 24/7
```

Only two reporting packages are in the debloat command. Core Android, TrueID/Live TV, remote/input, Play Services, keyboard, casting, assistant, updates and streaming apps stay enabled. The original session tested packages-only rollback and reboot persistence. [Validation](docs/validation.md) distinguishes those real-device results from the newly packaged public commands, which were built and tested offline rather than reinstalled on the already-finished TV.

## Restore and finish

```sh
python tools/setup.py restore          # preview; no device connection
python tools/setup.py restore --apply  # restore recorded package/settings/protection values
```

Restore leaves the helper APKs and the unlocked vendor property in place. An optional visible restore button can put the tested firmware's original empty property back; its new public implementation is build-checked, not device-tested. [Read the recovery details first](docs/restore.md).

When finished, stop maintenance, revoke USB debugging authorizations, turn debugging off and reboot through Android Settings. Then run:

```sh
python tools/adb_closed.py
```

`CLOSED` requires an explicit refused connection. A timeout or macOS privacy error is inconclusive. The original session verified refusal after reboot and stopped screen sharing as well. Use the HTTP Bridge only on your trusted LAN; it has a bearer token but does not encrypt traffic. Do not expose it or plaintext ADB to the Internet.

## Development

```sh
python -m unittest discover -s tests -v
python -m compileall -q tools
```

Keep reports to the model/build, reproducible steps and sanitized output. Raw `getprop`, diagnostics and screenshots can include identifiers, accounts and network names. No vendor APKs are redistributed. MIT for this repository's original work; see [NOTICE](NOTICE.md) for upstream credits.
