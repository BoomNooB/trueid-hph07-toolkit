package local.tv.livetv;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Trampoline tile: opens straight into TrueID TV's Live TV player, skipping its own home
 * grid/menu (and every other section: VOD, catchup, cloud gaming, social, settings)
 * entirely, then removes itself from the back stack so Back from Live TV returns to the
 * launcher Home, not to this empty shell. Point a launcher's dock/Live TV slot at this
 * package instead of com.truedmp.idtv directly (see docs/device-findings.th.md for the
 * cleanyourtv config.json keys: dock_apps, hidden_apps, live_tv_package).
 *
 * The deep link is truedmpatv://trueidapp/LIVE/<channel index>. idtv's own ATVHelper
 * crashes on a bare "LIVE/" with no index (IndexOutOfBoundsException reading Uri path
 * segment 1 — a real bug in the shipped app, reproduced on TrueID TV 25.02.21.22), so a
 * channel number is always required; this never calls it without one.
 *
 * Channel numbering is this app's internal index into its own channel list, not a
 * broadcast channel number, and almost certainly differs by region/operator build — 1
 * happened to be "Channel 3 HD" on the device this was built against. Find yours by
 * trying a few small integers with `tools/tv.py` and watching what comes up, or override
 * per-launch with an Intent extra:
 *
 *     adb shell am start -n local.tv.livetv/.MainActivity --es channel 3
 */
public final class MainActivity extends Activity {
    private static final String DEFAULT_CHANNEL = "1"; // whatever idtv's channel index 1 is on your box

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        String channel = getIntent() != null && getIntent().getStringExtra("channel") != null
                ? getIntent().getStringExtra("channel") : DEFAULT_CHANNEL;
        Intent live = new Intent(Intent.ACTION_VIEW,
                Uri.parse("truedmpatv://trueidapp/LIVE/" + channel))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(live);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open TrueID Live TV: " + e, Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
