package local.tv.adbcontrol;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import java.util.ArrayDeque;

/**
 * Explicit, bounded maintenance session. No network socket or polling; a boot receiver
 * exists (BootReceiver.java) but ships disabled — see its own comment for why that is
 * opt-in rather than default.
 *
 * The correction rate limit and session length were widened from an earlier 5-per-minute
 * / 6-hour build after field use found a vendor cloud policy (synced independently of any
 * local Home-open event, observed via a Firestore listener inside the vendor's own app —
 * see docs/device-findings.th.md) resets adb_enabled more often than that original guard
 * assumed, tripping its "give up" breaker well before 6 hours. The new numbers are a
 * judgement call, not a measured ceiling: raise MAX_CORRECTIONS_PER_WINDOW further if you
 * still see it give up, but a circuit breaker of some kind should stay, so a genuine
 * runaway loop still backs off instead of fighting forever.
 */
public final class AdbSessionService extends Service {
    private static final int MAX_CORRECTIONS_PER_WINDOW = 60;
    private static final long CORRECTION_WINDOW_MS = 60_000;
    private static final long SESSION_LENGTH_MS = 30L * 24 * 60 * 60 * 1000; // 30 days
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Long> corrections = new ArrayDeque<>();
    private ContentObserver observer;
    private long expires;
    private int count;
    private void notification(String detail) {
        Intent stop = new Intent(this, AdbSessionService.class).setAction("stop");
        PendingIntent pending = PendingIntent.getService(this, 1, stop,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        startForeground(7, new Notification.Builder(this, "adb-session")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("ADB maintenance session")
            .setContentText(detail)
            .addAction(new Notification.Action.Builder(null, "Stop session", pending).build())
            .setOngoing(true).build());
    }
    @Override public void onCreate() {
        super.onCreate();
        NotificationManager notifications = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        notifications.createNotificationChannel(new NotificationChannel("adb-session",
            "Temporary ADB maintenance", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        notification("Waiting for explicit start; use Stop session to end");
        if (intent == null || !"start".equals(intent.getAction())) {
            stopSelf(); return START_NOT_STICKY;
        }
        getSharedPreferences("maintenance",0).edit().putBoolean("enabled",true).apply();
        if (observer != null) return START_NOT_STICKY;
        if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            Log.w("TvAdbSession", "WRITE_SECURE_SETTINGS not granted; no action");
            stopSelf(); return START_NOT_STICKY;
        }
        expires = SystemClock.elapsedRealtime() + SESSION_LENGTH_MS;
        observer = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) { maintain(); }
        };
        getContentResolver().registerContentObserver(Settings.Global.getUriFor("adb_enabled"), false, observer);
        handler.postDelayed(this::stopSelf, SESSION_LENGTH_MS);
        notification("Active for this maintenance session; corrections: 0");
        maintain();
        return START_NOT_STICKY;
    }
    private void maintain() {
        if (observer == null) return;
        if (!getSharedPreferences("maintenance",0).getBoolean("enabled",false)) { stopSelf(); return; }
        long now = SystemClock.elapsedRealtime();
        if (now >= expires) { stopSelf(); return; }
        if (Settings.Global.getInt(getContentResolver(), "adb_enabled", 0) == 1) return;
        while (!corrections.isEmpty() && now - corrections.peekFirst() > CORRECTION_WINDOW_MS) corrections.removeFirst();
        if (corrections.size() >= MAX_CORRECTIONS_PER_WINDOW) {
            Log.w("TvAdbSession", "Over " + MAX_CORRECTIONS_PER_WINDOW + " resets in a minute; stopping instead of looping");
            stopSelf(); return;
        }
        corrections.addLast(now);
        try {
            if (!Settings.Global.putInt(getContentResolver(), "adb_enabled", 1)) throw new SecurityException("Write refused");
            Log.i("TvAdbSession", "Restored adb_enabled after policy reset; count=" + (++count));
            notification("Active; restored ADB " + count + " time(s); Stop session ends this override");
        } catch (Exception error) {
            Log.w("TvAdbSession", "Could not restore ADB", error); stopSelf();
        }
    }
    @Override public void onDestroy() {
        getSharedPreferences("maintenance",0).edit().putBoolean("enabled",false).apply();
        if (observer != null) { getContentResolver().unregisterContentObserver(observer); observer = null; }
        handler.removeCallbacksAndMessages(null);
        stopForeground(true);
        Log.i("TvAdbSession", "Maintenance override stopped; no future settings writes");
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
