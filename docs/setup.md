# Setup from a locked box

This is the tested HPH07 path, not a promise that every firmware can be unlocked. The public tools require Python 3.10+, an installed Android SDK and JDK 21. Windows support has not been validated; the walkthrough uses a macOS/Linux shell.

## 1. Confirm the model and reach the box

In Android Settings → Device Preferences → About, confirm **HPH07**, Android 9, and record the firmware. Record the current state of Developer options and the USB debugging switch. In the original session the property gate was empty and ADB was off. Do not factory-reset, enter factory/test menus, flash another board's image or assume that an SD slot implies boot support.

Put the computer and box on the same trusted network. Read the box's IP in Settings and set `TV_HOST`; the tools do not scan your network. If working remotely, use the owner's supplied VPN address rather than scanning a different home.

Check what is plugged into USB, **including whether the network itself uses a USB Ethernet adapter**. Some firmware cuts USB host power when debugging is on. The tested session used Wi-Fi. If an adapter is involved, prefer a session you can finish in one sitting and verify actual Ethernet again after debugging is off.

## 2. Build before ADB exists

Follow the prerequisites and build commands in the root README. Android SDK command-line tools, platforms 34/35 and build-tools 34/35 are required. Launcher Gradle may download dependencies and its wrapper distribution; there are no bundled SDK or JDK binaries.

Build `adb-control`, `bridge` and `package-guard`. Bridge's `--host-ip` is the **computer's address**, not the TV address; it is embedded so the app can register its random token with your local host. Rebuild Bridge if that address changes.

Run `tools/host.py --bind <computer-lan-ip>`. It serves these exact paths:

| URL | Purpose |
|---|---|
| `/` | Short download page |
| `/adb-control.apk` | Unlock + visible maintenance controls |
| `/tv-bridge.apk` | Consented screen capture and read-only local diagnostics |
| `/register` | POST from the specified TV IP only; saves its token privately |

On the TV use its browser or Downloader to open the LAN URL. A browser may download an APK without opening it: check that browser's downloads or use Downloader's install flow. In our session X-plore's Play Store install failed but other apps, including Downloader and later HBO Max, installed. We did not establish one cause for X-plore's generic error; don't conclude that all sideloading is forbidden from that message alone.

If **every** verified APK install is refused by firmware, stop here: the local-app bootstrap is a prerequisite. This repository does not supply an SD image, UART root shell or a remote no-install exploit. Downloading an APK is not proof it was installed.

## 3. Optional: pair a computer remote

```sh
python tools/remote.py
```

Accept pairing on the TV and enter the six-character pairing code locally. Keys/certificates are generated under `.state/`. Commands include `key HOME`, `key DPAD_DOWN`, `key DPAD_CENTER`, `key BACK`, `status`, `text ...`, `launch <uri>` and `quit`.

Text input depends on the focused TV app/keyboard; it failed for letters in some original browser fields. Don't spend an hour forcing that path: use the on-screen keyboard or phone/remote text entry. The native remote protocol does **not** provide screenshots and does not grant ADB privileges.

## 4. Unlock the switch, then authorize ADB

Install ADB Control and open its **HPH07 vendor unlock** TV app entry. On the tested firmware:

1. Press **Probe**; read and record the current property before writing. The original was empty.
2. Press **Unlock USB debugging control**; confirm read-back is `true`.
3. Press **Close temporary vendor API debug session**. This restores the vendor service's temporary API build flag to release; it does not undo the unlocked property.
4. Open Android Developer options and enable USB debugging. The developer master switch is separate.
5. Run `python tools/tv.py shell 'id'`. Accept **Allow USB debugging?** on the TV only after matching the displayed ADB key fingerprint.

The normal result is uid 2000 `shell`, **not root**. A macOS Local Network block can make the stock adb report “No route to host”; the Python ADB transport is the tested fallback. A plain USB-A-to-A cable does not override a policy-disabled ADB service and is not part of this procedure.

The public helper adds a model/SDK gate, visible unlock entry and original-empty-property restore button. Those packaging changes compile successfully but have not been reinstalled on the finished original box. The core property read/write and vendor API reset flow were device-tested in the original helper.

## 5. Back up and keep a bounded maintenance session

```sh
python tools/setup.py backup
python tools/setup.py maintenance
```

Backups include full package/disabled lists, properties, all three settings namespaces, USB and connectivity. `.state/ledger.json` records only this toolkit's changes and preserves first originals on reruns. Use a separate `TV_STATE_DIR` per device. Full ADB backups necessarily happen **after** the initial property unlock; record that original property before changing it.

TrueID can turn ADB off again when its configuration initializes. `maintenance` grants `WRITE_SECURE_SETTINGS` **only to our own helper** and starts its visible foreground observer for up to six hours. It can correct up to five resets per minute and then stops. It does not remove the vendor policy. Never revoke this permission from the OTT gateway: it shares system UID 1000. Stopping/expiry stops correction; the visible Disable button also turns ADB off.

Install/open Bridge if screen sharing is useful. Press Share screen and accept Android's MediaProjection prompt. `python tools/bridge_client.py status` and `python tools/bridge_client.py screen.jpg shots/example.jpg` use the private token file. Output paths are relative to `.state/`. End the service using its Stop button when finished. This is trusted-LAN HTTP, not encrypted remote access.

## 6. Build and enable Home

```sh
python tools/upstream.py
python tools/launcher.py --install
python tools/setup.py home
```

The launcher is fetched at a pinned revision and only its `config.xml` is overlaid. The installer verifies the installed APK's SHA256 rather than assuming an interrupted `pm install` failed. `setup home` records the original per-package uninstall block and accessibility settings, protects **only** `com.gathin.tvhome`, preserves existing accessibility service entries and enables HomeKeyService.

The tested built-in dock is Netflix / YouTube / Jellyfin / TrueID. Edit `launcher/hph07-config.xml` to choose 4–7 columns and package order, then rebuild. `examples/theme-config.json` demonstrates HBO Max in the first slot; it is an example, not the configuration installed in the original session. For wallpaper/theme changes use the pinned cleanyourtv theme tools and their documentation; preserve the Home app-key mapping on this board.

Do not disable TrueID's package or whole MainActivity: it also supplies Live TV. Native HOME priority can remain stock even when `set-home-activity` says Success. Check the **physical** Home button, not just an ADB-injected key. Hold volume up to check repeats before and after enabling the helper. If repeats break, disable the helper and choose the tradeoff explicitly. On the tested HPH07, the owner confirmed both Home and volume repeat worked. At boot you may initially see TrueID; Home opens the custom launcher.

## 7. Minimal cleanup and verification

```sh
python tools/setup.py debloat
```

This command only disables Google Feedback and TV Bug Report Sender, one `pm` call at a time, verifies the actual disabled list and captures after each change. Inspect those captures before proceeding. There is no broad “remove bloat” list. Both services were dormant in our baseline, so there is no measured CPU/RAM gain to promise.

Reboot once, compare disabled packages with the backup plus intended changes, and test Live TV and a subscribed DRM service. A black protected screenshot is inconclusive; ask the person at the TV to confirm actual picture/audio. Our owner verified HBO Max playback. Netflix was not subscription-tested.

Finally follow [Restore and close access](restore.md). Optional ad-blocking Private DNS was not applied. It cannot remove YouTube video ads or all in-app promotions. Read the upstream DNS instructions and test streaming if you choose to change it.
