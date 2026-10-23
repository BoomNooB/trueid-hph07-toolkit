# Before ADB: give the agent buttons, then eyes

The first useful milestone is **a verified screenshot from TV Bridge while ADB is still off**. The original session did not start with shell access or screen capture. Read this branch when the owner cannot enable ADB or is tired of photographing the screen.

| Stage | Agent can do | Owner still needs to do |
|---|---|---|
| No pairing | Build helpers; prepare an APK host | Show model/IP and keep the box powered |
| Native remote paired | Send remote keys; attempt text/app links | Accept pairing and provide its code; report essential screen state |
| Downloader installed | Drive the downloader if text/focus work | Confirm installer/unknown-source prompts when necessary |
| Bridge installed and capture consent granted | Fetch and inspect screenshots; read permitted package/APK diagnostics | Grant Android screen-sharing consent once per required capture session |
| Vendor gate unlocked and ADB authorized | Shell, complete backups, verified file transfers | Accept the matching computer key; later check real Home/volume/playback |

Pairing is not ADB and does not transmit pictures. A Bridge registration proves communication, not that screen sharing is ready. A healthy `/status` plus an inspected `/screen.jpg` is the completion gate. Only then does the agent stop asking for repeated photos.

## The app that actually installed

Use **[Downloader by AFTVnews](https://www.aftvnews.com/downloader/)**, package **`com.esaba.downloader`**, from its [official Play listing](https://play.google.com/store/apps/details?id=com.esaba.downloader). In the original session X-plore failed, while this app installed. That was not a Play Store protection bypass; we used another app's download/install flow. Its success doesn't explain X-plore's failure.

An already-paired agent can try opening the listing through the native remote's app-link command:

```text
launch https://play.google.com/store/apps/details?id=com.esaba.downloader
```

Verify which page appeared before pressing Install. App links can fail on this firmware; the owner can find the exact app in Play Store if needed. Do not report installation based only on a sent key or a successful download request.

## Preferred path: local APK URL

1. Build Bridge with the computer's LAN IP, start `tools/host.py` and pair `tools/remote.py` as described in `setup.md`.
2. Open Downloader and focus its URL field. Use `http://<computer-ip>:18765/tv-bridge.apk`.
3. Try sending the text once and check whether it appears. If letters fail while digits work, switch paths; a successful key transport is not successful text input. Clear the old field before another attempt.
4. If a phone/TV keyboard can enter the LAN URL more easily, use it. Don't make the owner generate an Internet short code when the local URL works.
5. Confirm Install, open TV Bridge, choose Share screen and accept Android's prompt. Then run the client checks below.

The original BrowseHere path reached “Are you sure to download?” but then showed no usable install flow. Keyboard focus and toolbar navigation also stalled. Repeating those same actions was not a solution; Downloader was the practical alternate route.

## Fallback: only numeric keys work

The original session got past the text-input failure using an **AFTVnews numeric short code**. The [official shortener](https://go.aftvnews.com/) maps a URL to digits that can be entered in Downloader's URL field. The code is a redirect, not an Android PIN or privilege bypass.

First try a normal LAN URL/manual keyboard. If a numeric code is needed:

1. Start an **APK-only** loopback origin in another terminal:

   ```sh
   python tools/transfer.py bridge
   ```

2. If `cloudflared` is installed, expose that origin temporarily:

   ```sh
   cloudflared tunnel --url http://127.0.0.1:18767
   ```

   It prints a temporary HTTPS hostname. Append `/tv-bridge.apk`. This is the [Cloudflare Quick Tunnel flow](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/); its hostname changes across restarts and stops working when the process stops.

3. Verify the HTTPS endpoint returns the same APK as the local build, including SHA256. Serve **only** the APK, not `host.py`, the authenticated Bridge, a directory of backups or any ADB port. The provided transfer origin has no registration or diagnostics routes.
4. Open `https://go.aftvnews.com/` on the computer/phone and paste that exact live APK URL. The agent can prepare/open/copy the URL; the owner may have to complete CAPTCHA and submit it. The shortener says submitted URLs are publicly viewable: use an APK URL without credentials/tokens.
5. Give the resulting digits to Downloader, choose Go, confirm installation and open Bridge. The original LAN URL was rejected by the shortener with “invalid”; we did not establish a universal rule for private IPs/ports from that one result.
6. Keep the transfer server **and** tunnel alive until the installed app is verified. The original session closed its tunnel too soon, got Cloudflare Error1033 and required a new endpoint/code. The old session's numeric codes are intentionally not published and cannot install the current build.
7. Stop the tunnel and transfer server after this step succeeds. Diagnostics/screen sharing use the private LAN host/Bridge, not the public tunnel.

If `cloudflared` is unavailable or the owner can type the LAN address, the local path remains sufficient; no permanent public service is required. The new public `transfer.py` is an adaptation of the original APK-only server, tested over loopback; this publication did not open a new public tunnel or reinstall on the TV.

## Verify that the agent now has eyes

Keep the normal **LAN** `host.py` running so Bridge can register its token into `.state/bridge-connection.json`. In the TV app, Reconnect retries registration explicitly; it is not a polling loop.

```sh
python tools/bridge_client.py status
python tools/bridge_client.py screen.jpg shots/first-bridge.jpg
```

Inspect `.state/shots/first-bridge.jpg`. If status works but `captureActive`/`screenReady` are false, obtain Android's capture consent rather than repeatedly requesting an unavailable picture. If the screen is static, an old `frameAgeMs` alone does not prove a broken capture; compare a deliberate focus change after settling. Protected video can still be black.

At this point the agent can navigate from screenshots and the paired remote. On the original Android9 box, the read-only Bridge also retrieved installed package information and readable installed base APKs for local analysis **before ADB was enabled**. That is how the Settings/OTT gate was investigated; the procedure did not require an ADB shell to obtain the first APKs. This visibility is scoped to the app permissions and this firmware, not unrestricted filesystem access.

Now continue to `setup.md`'s vendor-unlock step. Install the next helper using Downloader and the on-screen keyboard with the newly available screen view. ADB authorization remains a later, separate consent step.
