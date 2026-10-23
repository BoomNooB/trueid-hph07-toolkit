# Restore and close access

The toolkit records changes before writing and never uninstalls a system package. Keep `.state/` locally; it contains the original values, signing/ADB keys and private backups. A different clone's empty state cannot reconstruct your originals.

```sh
export TV_HOST=<your-box-ip>
python tools/setup.py restore          # preview: no TV connection
python tools/setup.py restore --apply
```

Restore returns recorded reporting-package enabled states, accessibility settings and the launcher-only uninstall block to their originals. It stops the maintenance observer and revokes the helper permission if this toolkit added it. It does not delete accounts, firmware, DRM provisioning or APKs. It does not set a new native HOME preference because this toolkit's Home setup never changed that preference.

Removing uninstall protection can let TrueID's original cleanup delete the third-party launcher again; that is restoring the original behavior. Removing the Home service lets the original Home behavior return. If the TV isn't reachable, use Settings → Accessibility to disable the launcher Home helper, and Android Developer options / TrueTV ADB Control to reopen an explicit maintenance session when needed. Don't disable TrueID to recover Home.

To restore just one reporting package to the normal default state:

```sh
python tools/tv.py shell 'pm default-state --user 0 com.google.android.feedback'
python tools/tv.py shell 'pm default-state --user 0 com.google.android.tv.bugreportsender'
```

For a byte-for-byte original enabled-state choice, use the recorded ledger rather than assuming every package started in the default state.

## Optional: undo the vendor property unlock

Normal restore intentionally leaves `persist.truetv.adbcommand=true` so the owner can use the debug switch later. The original tested box had an **empty** value. If you deliberately want the original greyed-out switch back, use the public helper's HPH07 vendor unlock entry → Probe → Restore original empty property → Close temporary vendor API debug session. Do not use this button if your own original was not empty. Its new public UI/source has been build-checked, not tested as a restore operation on the finished TV. The core get/set API was tested in the original session.

Changing the gate property and switching ADB on/off are separate operations. Stop maintenance before switching ADB off. Do not revoke shared system UID permissions or clear app data to force the issue.

## Close maintenance completely

1. In TrueTV ADB Control choose **Disable ADB and stop session**, or stop its session before using Android's USB debugging switch.
2. In Android Developer options revoke USB debugging authorizations and turn off USB/network debugging. No separate network-debugging toggle was present in the tested UI.
3. Reboot through Android Settings → Device Preferences → About → Restart. This also ends Bridge screen sharing.
4. Run `python tools/adb_closed.py` from the computer while the box is otherwise reachable. `CLOSED` means explicit connection refusal. `INCONCLUSIVE` is not a pass.
5. Stop `host.py` and `remote.py` on the computer. If the box's network uses USB Ethernet, replug the adapter after debugging is off and confirm Ethernet rather than a Wi-Fi fallback.

These UI steps were executed in the original session. After reboot the native paired remote reconnected, port 5555 explicitly refused connections, the Bridge port also refused, and Home opened the new launcher again. Re-enabling ADB later requires a fresh authorization because the old authorizations were revoked.
