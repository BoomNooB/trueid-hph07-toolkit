# Follow-up: a wake/Home fix broke Live TV

Observed on the same HPH07, later on 23 October 2026, after the original setup. This is a record of a real regression and recovery, not a claim that the original public configuration used the broken value.

## Trigger and symptom

A later assistant session tried to bring the custom launcher forward after power/sleep by adding this external theme value:

```json
{ "stock_home_redirect": "com.truedmp.idtv" }
```

The owner's dock now used a separate Live TV shortcut, opening a TrueID LIVE deep link. After restart, Home still reached the custom launcher, but selecting Live TV briefly opened TrueID and then bounced back. The owner could no longer watch Live TV through the tile.

## Evidence and isolation

An automated device probe launched the actual shortcut and sampled the resumed activity. Before the fix it consistently showed:

```text
TrueID MainActivity → custom launcher MainActivity
FAIL: LiveTV does not stay in TrueID
```

The existing launcher service builds a set of the configured stock package's HOME activity class names. On a window-state event matching one of those classes it starts its own Home. This firmware uses **the same TrueID MainActivity for Home and Live TV**. The class-based redirect cannot tell those uses apart.

Three causes were separated: accessibility redirect, a later background RAM guard and an app crash. Only the redirect value was changed to `""`; the Live TV shortcut, TrueID components, background guard and other preferences remained unchanged. After reloading the launcher/accessibility services, the same device probe stayed in TrueID for all seven samples. Temporary ADB interruptions were handled as inconclusive observations/reconnects, not misreported as an app crash.

The owner then confirmed picture/audio, physical Home and opening Live TV again. After reboot the empty value persisted and the probe passed again. Finally the owner independently reported that both **reboot and sleep/wake** now worked normally.

## Recovery on this firmware

Back up the current theme and accessibility list before changing anything. Keep `stock_home_redirect` empty, preserve the rest of the theme, reload the launcher and restore the enabled accessibility services using the upstream theme tooling's procedure. Test Live TV → Home → Live TV, reboot and sleep/wake. Keep TrueID enabled; disabling the activity breaks the same playback entry point.

The public built-in HPH07 config already leaves this value empty. Inspect external `config.json` overlays too: a later theme can override a correct APK default. There is no new claim that native HOME priority or direct-to-launcher boot was solved.

The owner's later private setup also added boot-persistent ADB and a server-side RAM guard. Those later choices are separate from the public toolkit's original bounded six-hour maintenance helper; this fix preserved that private setup rather than replacing its policy. Don't copy a private always-on maintenance arrangement into a generic owner's setup by assumption.
