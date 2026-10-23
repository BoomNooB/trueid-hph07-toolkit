package local.tv.adbcontrol;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;

/** Visible local controls; never starts debugging at boot or without a button press. */
public final class AdbSettingsActivity extends Activity {
    private TextView state;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(64,40,64,40);
        layout.setBackgroundColor(0xff101820);
        TextView title = new TextView(this);
        title.setText("TrueTV ADB Control"); title.setTextSize(30); title.setTextColor(0xffffffff);
        layout.addView(title);
        state = new TextView(this); state.setTextSize(22); state.setTextColor(0xffffffff);
        state.setPadding(0,20,0,20); layout.addView(state);
        TextView info = new TextView(this);
        info.setText("TrueID can switch USB debugging off when Home opens.\nEnable below keeps it available for a 6-hour maintenance session.\nThis is a workaround; it does not remove TrueID's policy.\nDisable stops the helper and closes ADB. No automatic start at boot.");
        info.setTextSize(18); info.setTextColor(0xffc1cfdd); layout.addView(info);
        Button on = new Button(this); on.setText("Enable ADB (6-hour session)");
        on.setOnClickListener(v -> {
            try {
                requirePermission();
                Settings.Global.putInt(getContentResolver(), "adb_enabled",1);
                startForegroundService(new Intent(this,AdbSessionService.class).setAction("start"));
                refresh();
            } catch (Exception error) { state.setText("Could not enable: "+error.getMessage()); }
        }); layout.addView(on);
        Button off = new Button(this); off.setText("Disable ADB and stop session");
        off.setOnClickListener(v -> {
            try {
                requirePermission();
                getSharedPreferences("maintenance",0).edit().putBoolean("enabled",false).apply();
                stopService(new Intent(this,AdbSessionService.class));
                Settings.Global.putInt(getContentResolver(),"adb_enabled",0);
                refresh();
            } catch (Exception error) { state.setText("Could not disable: "+error.getMessage()); }
        }); layout.addView(off);
        Button settings = new Button(this); settings.setText("Open Android Developer options");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
        layout.addView(settings);
        setContentView(layout); on.requestFocus(); refresh();
    }
    private void requirePermission() {
        if(checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)!=PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("One-time ADB permission grant is missing");
    }
    private void refresh() {
        boolean enabled=Settings.Global.getInt(getContentResolver(),"adb_enabled",0)==1;
        state.setText("USB debugging: "+(enabled?"ON":"OFF")+"\nRemote authorizations remain controlled by Android.");
    }
    @Override public void onResume() { super.onResume(); if(state!=null)refresh(); }
}
