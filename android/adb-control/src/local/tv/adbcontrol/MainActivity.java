package local.tv.adbcontrol;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final String PROP = "persist.truetv.adbcommand";
    private static final String MANAGER = "com.truedmp.ottapp.ISystemServiceControlManager";
    private static final String UTILS = "com.truedmp.ottapp.ISystemServiceUtilsControl";
    private TextView status;
    private IBinder utils;
    private boolean bound;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder manager) {
            Parcel request = Parcel.obtain(), reply = Parcel.obtain();
            try {
                request.writeInterfaceToken(MANAGER);
                if (!manager.transact(1, request, reply, 0)) throw new SecurityException("Vendor manager refused API access");
                reply.readException();
                utils = reply.readStrongBinder();
                if (utils == null) throw new SecurityException("No utility interface returned");
                status.setText("Vendor service connected.\nCurrent ADB-control property: [" + readProperty() + "]\nNo property has been changed yet.");
            } catch (Exception e) { utils = null; status.setText("Probe refused: " + e); }
            finally { request.recycle(); reply.recycle(); }
        }
        @Override public void onServiceDisconnected(ComponentName name) { utils = null; }
    };

    private String readProperty() throws Exception {
        Parcel request = Parcel.obtain(), reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(UTILS); request.writeString(PROP);
            if (!utils.transact(18, request, reply, 0)) throw new SecurityException("Read refused");
            reply.readException(); return reply.readString();
        } finally { request.recycle(); reply.recycle(); }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!"HPH07".equals(android.os.Build.MODEL) || android.os.Build.VERSION.SDK_INT != 28) {
            TextView unsupported = new TextView(this);
            unsupported.setText("Vendor unlock is scoped to Skyworth HPH07 Android 9. No vendor call was made.");
            setContentView(unsupported);
            return;
        }
        if (getIntent().getBooleanExtra("reset_vendor_session", false)) {
            TextView reset = new TextView(this);
            reset.setText("Closing temporary vendor API debug session...");
            setContentView(reset);
            Intent service = new Intent("local.tv.adbcontrol.RESTORE");
            service.setComponent(new ComponentName("com.truedmp.ottapp", "com.truedmp.ottapp.TrueDmpSystemService"));
            service.putExtra("show_build_type", "release");
            ServiceConnection restore = new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                    reset.setText("Temporary vendor API debug session closed. USB debugging is unchanged.");
                    unbindService(this);
                    finish();
                }
                @Override public void onServiceDisconnected(ComponentName name) { }
            };
            if (!bindService(service, restore, BIND_AUTO_CREATE)) reset.setText("Could not close vendor API session.");
            return;
        }
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(64, 40, 64, 40);
        view.setBackgroundColor(0xff101820);
        status = new TextView(this);
        status.setTextColor(0xffffffff);
        status.setTextSize(24);
        status.setText("HPH07 vendor unlock (public source)\nProbe the vendor service before changing the single ADB-control property.\nUSB debugging remains a separate switch in Developer options.");
        view.addView(status);
        Button probe = new Button(this);
        probe.setText("Probe TrueTV service (read property)");
        probe.setOnClickListener(v -> {
            if (bound) { status.setText("Already bound; reopen this app to retry."); return; }
            Intent service = new Intent("local.tv.adbcontrol.PROBE");
            service.setComponent(new ComponentName("com.truedmp.ottapp", "com.truedmp.ottapp.TrueDmpSystemService"));
            service.putExtra("show_build_type", "debug");
            try {
                bound = bindService(service, connection, BIND_AUTO_CREATE);
                if (!bound) status.setText("Vendor service binding refused.");
            } catch (SecurityException e) { status.setText("Binding refused: " + e); }
        });
        view.addView(probe);
        Button enable = new Button(this);
        enable.setText("Unlock USB debugging control");
        enable.setOnClickListener(v -> {
            try {
                if (utils == null) throw new SecurityException("Run the read-only probe first");
                String before = readProperty();
                Parcel request = Parcel.obtain(), reply = Parcel.obtain();
                try {
                    request.writeInterfaceToken(UTILS); request.writeString(PROP); request.writeString("true");
                    if (!utils.transact(19, request, reply, 0)) throw new SecurityException("Property write refused");
                    reply.readException();
                } finally { request.recycle(); reply.recycle(); }
                status.setText("ADB-control property before: [" + before + "]\nRead-back after request: [" + readProperty() + "]\nCheck the USB debugging switch in Developer options.");
            } catch (Exception e) {
                status.setText("Request refused: " + e);
            }
        });
        view.addView(enable);
        Button restore = new Button(this);
        restore.setText("Restore original empty property (tested firmware only)");
        restore.setOnClickListener(v -> {
            try {
                if (utils == null) throw new SecurityException("Run the probe first");
                Parcel request = Parcel.obtain(), reply = Parcel.obtain();
                try {
                    request.writeInterfaceToken(UTILS); request.writeString(PROP); request.writeString("");
                    if (!utils.transact(19, request, reply, 0)) throw new SecurityException("Restore refused");
                    reply.readException();
                } finally { request.recycle(); reply.recycle(); }
                status.setText("Property restored; read-back: [" + readProperty() + "]. Close vendor API session next.");
            } catch (Exception e) { status.setText("Restore refused: " + e); }
        });
        view.addView(restore);
        Button close = new Button(this);
        close.setText("Close temporary vendor API debug session");
        close.setOnClickListener(v -> {
            Intent reset = new Intent(this, MainActivity.class);
            reset.putExtra("reset_vendor_session", true);
            startActivity(reset);
            finish();
        });
        view.addView(close);
        Button settings = new Button(this);
        settings.setText("Open Developer options");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
        view.addView(settings);
        setContentView(view);
        probe.requestFocus();
    }

    @Override protected void onDestroy() {
        if (bound) { unbindService(connection); bound = false; }
        super.onDestroy();
    }
}
