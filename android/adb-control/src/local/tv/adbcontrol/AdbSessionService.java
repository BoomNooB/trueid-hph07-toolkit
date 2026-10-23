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

/** Explicit, bounded maintenance session. No network socket, boot receiver or polling. */
public final class AdbSessionService extends Service {
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
            .setContentTitle("ADB maintenance session (6 hour limit)")
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
        expires = SystemClock.elapsedRealtime() + 6L * 60 * 60 * 1000;
        observer = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) { maintain(); }
        };
        getContentResolver().registerContentObserver(Settings.Global.getUriFor("adb_enabled"), false, observer);
        handler.postDelayed(this::stopSelf, 6L * 60 * 60 * 1000);
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
        while (!corrections.isEmpty() && now - corrections.peekFirst() > 60000) corrections.removeFirst();
        if (corrections.size() >= 5) {
            Log.w("TvAdbSession", "Repeated policy conflict; stopping instead of looping");
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
