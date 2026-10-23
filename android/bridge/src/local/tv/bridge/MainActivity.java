package local.tv.bridge;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(64, 40, 64, 40);
        layout.setBackgroundColor(0xff101820);
        TextView title = new TextView(this);
        title.setText("TV Bridge\nLocal diagnostics and screen sharing for your Mac");
        title.setTextSize(26);
        title.setTextColor(0xffffffff);
        layout.addView(title);
        TextView detail = new TextView(this);
        detail.setText("Keeps a local connection open while running.\nScreen sharing starts only after Android's permission dialog.\nNo microphone, camera, cloud upload, or package changes.");
        detail.setTextSize(18);
        detail.setTextColor(0xffc1cfdd);
        detail.setPadding(0, 24, 0, 24);
        layout.addView(detail);
        Button share = new Button(this);
        share.setText("Share screen with Mac");
        share.setOnClickListener(v -> {
            MediaProjectionManager manager = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(manager.createScreenCaptureIntent(), 100);
        });
        layout.addView(share);
        Button reconnect = new Button(this);
        reconnect.setText("Reconnect to Mac");
        reconnect.setOnClickListener(v -> startForegroundService(new Intent(this, BridgeService.class)));
        layout.addView(reconnect);
        Button stop = new Button(this);
        stop.setText("Stop and close");
        stop.setOnClickListener(v -> { stopService(new Intent(this, BridgeService.class)); finish(); });
        layout.addView(stop);
        setContentView(layout);
        share.requestFocus();
        startForegroundService(new Intent(this, BridgeService.class));
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 100 && result == RESULT_OK && data != null) {
            startForegroundService(new Intent(this, BridgeService.class)
                .setAction("capture").putExtra("result", result).putExtra("data", data));
        }
    }
}
