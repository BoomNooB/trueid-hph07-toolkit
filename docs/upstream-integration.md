# Relationship to cleanyourtv

The clean home screen is **cleanyourtv's launcher**, not a new launcher invented in this toolkit. The upstream repository also supplies the general cleanup workflow, device notes, guarded package-disabling tools, theme support and recovery conventions:

https://github.com/Gathin23/cleanyourtv

This toolkit supplies the HPH07-specific access/bootstrap investigation, small local helper apps, one-shot launcher uninstall protection, portability wrappers, and a documented result. It complements the upstream project; it does not replace its general device workflow.

## For an agent using the two projects

1. Run `python tools/upstream.py` to fetch the tested revision into `.deps/cleanyourtv`.
2. Read that checkout's `AGENTS.md` and `PROMPT.md` before device work. For the TV-setup branch, the upstream expects reuse of its launcher/tools and config/theme customization, with no unsolicited code/docs edits or PRs there.
3. Keep HPH07 vendor helpers in **this** repository. `tools/launcher.py` changes only the upstream `config.xml`, using `launcher/hph07-config.xml`. Don't apply a generic list that disables the TrueID package.
4. Install and verify the launcher, then use this toolkit's ledger/protection/Home helper setup. Generic `set-home-activity` success is insufficient on this board, and the vendor's delayed cleanup can uninstall a third-party HOME app.
5. Follow the real-device checks in `docs/agent-runbook.md`: actual Home, held volume, reboot and subscribed playback. Leave the main TrueID app enabled for Live TV/DRM.

The pinned revision is an evidence anchor, not a guarantee that later upstream releases behave identically. Review release/config changes before updating the pin. Preserve all previous work in `.deps/` if it has local changes; the fetch script refuses to overwrite a dirty checkout.

## For upstream readers

The useful HPH07 findings are the separate property gate vs automatic ADB-off policy; TrueID's bundled Live TV/Home role; higher native HOME intent priority; and delayed silent deletion of non-system HOME packages. Device/build scope and confidence are listed in [validation.md](validation.md). These are local findings, not an upstream-endorsed procedure or an accepted device submission. The two-package list in `examples/` and the article can be cited when discussing support for this exact firmware.

REA was an APK analysis workflow reference: https://github.com/morluto/rea. It did not itself remotely unlock the box. Both projects are credited in the root NOTICE.
