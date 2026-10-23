package local.tv.adbcontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Starts AdbSessionService automatically after boot, so you never have to open
 * AdbSettingsActivity and press "Enable ADB" by hand again after a reboot.
 *
 * Ships DISABLED in the manifest (android:enabled="false") on purpose: this repo's
 * default is "ADB is off until you explicitly turn it on", and auto-starting it at every
 * boot is a real standing tradeoff — the TV's ADB port is then reachable on your LAN
 * indefinitely, not just during a maintenance session you started on purpose. That's a
 * reasonable choice on your own box once you understand it (it's still only reachable by
 * keys you've already authorized; a never-seen-before key still gets the normal Android
 * "Allow USB debugging?" prompt), but it shouldn't be the default for everyone who clones
 * this repo for their own TV. Turn it on yourself if you want it:
 *
 *     python tools/tv.py shell 'pm enable local.tv.adbcontrol/.BootReceiver'
 *
 * and back off with:
 *
 *     python tools/tv.py shell 'pm disable local.tv.adbcontrol/.BootReceiver'
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        context.startForegroundService(new Intent(context, AdbSessionService.class).setAction("start"));
    }
}
