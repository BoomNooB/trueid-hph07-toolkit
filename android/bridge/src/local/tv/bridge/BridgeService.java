package local.tv.bridge;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.*;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.*;
import android.os.*;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class BridgeService extends Service {
    private static final int PORT = 18766;
    private static final String MAC = HostConfig.REGISTER_URL;
    private final ExecutorService workers = new ThreadPoolExecutor(4, 4, 0L,
        TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(16));
    private volatile boolean running;
    private volatile byte[] frame;
    private volatile String captureError = "";
    private String token;
    private ServerSocket listener;
    private HandlerThread captureThread;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader images;
    private volatile long lastFrame;
    private volatile long frameSequence;
    private Handler captureHandler;

    @Override public void onCreate() {
        super.onCreate();
        token = getSharedPreferences("bridge", 0).getString("token", null);
        if (token == null) {
            token = UUID.randomUUID().toString() + UUID.randomUUID().toString();
            getSharedPreferences("bridge", 0).edit().putString("token", token).apply();
        }
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel("bridge", "TV Bridge", NotificationManager.IMPORTANCE_LOW));
        Intent end = new Intent(this, BridgeService.class).setAction("stop");
        PendingIntent stop = PendingIntent.getService(this, 1, end, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "bridge")
            .setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("TV Bridge is running")
            .setContentText("Local diagnostics; Stop ends the connection and screen sharing")
            .addAction(new Notification.Action.Builder(null, "Stop", stop).build()).setOngoing(true).build();
        startForeground(1, notification);
        running = true;
        new Thread(this::serve, "tv-bridge-http").start();
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && "stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (intent != null && "capture".equals(intent.getAction())) {
            Intent consent = intent.getParcelableExtra("data");
            try { startCapture(intent.getIntExtra("result", -1), consent); }
            catch (Exception e) { captureError = e.toString(); }
        }
        new Thread(this::register, "tv-bridge-register").start();
        return START_NOT_STICKY;
    }

    private void register() {
        HttpURLConnection connection = null;
        try {
            JSONObject body = new JSONObject().put("token", token).put("port", PORT)
                .put("package", getPackageName()).put("model", Build.MODEL);
            connection = (HttpURLConnection)new URL(MAC).openConnection();
            connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
            connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            connection.getInputStream().close();
        } catch (Exception ignored) { /* Reconnect is a visible button, not a polling loop. */ }
        finally { if (connection != null) connection.disconnect(); }
    }

    private void serve() {
        try {
            listener = new ServerSocket(PORT, 16);
            while (running) {
                Socket socket = listener.accept();
                try { workers.execute(() -> handle(socket)); }
                catch (RejectedExecutionException e) { socket.close(); }
            }
        } catch (IOException e) { if (running) captureError = e.toString(); }
    }

    private void handle(Socket socket) {
        try (Socket client = socket) {
            client.setSoTimeout(5000);
            BufferedReader input = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
            String line = limitedLine(input);
            if (line == null) return;
            String[] parts = line.split(" ");
            if (parts.length != 3 || !"GET".equals(parts[0])) { reply(client, 405, "text/plain", "GET only".getBytes(StandardCharsets.UTF_8)); return; }
            int headers = 0;
            while ((line = limitedLine(input)) != null && !line.isEmpty()) {
                if (++headers > 64) throw new IOException("Too many headers");
            }
            URI uri = new URI(parts[1]);
            Map<String,String> query = new HashMap<>();
            if (uri.getRawQuery() != null) for (String pair : uri.getRawQuery().split("&")) {
                String[] entry = pair.split("=", 2);
                if (entry.length == 2) query.put(URLDecoder.decode(entry[0], "UTF-8"), URLDecoder.decode(entry[1], "UTF-8"));
            }
            String supplied = query.get("token");
            if (supplied == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                reply(client, 403, "text/plain", "Forbidden".getBytes(StandardCharsets.UTF_8)); return;
            }
            String path = uri.getPath();
            if ("/status".equals(path)) {
                JSONObject status = new JSONObject().put("package", getPackageName()).put("sdk", Build.VERSION.SDK_INT)
                    .put("model", Build.MODEL).put("fingerprint", Build.FINGERPRINT)
                    .put("frameSequence", frameSequence).put("frameAgeMs", frame == null ? -1 : SystemClock.elapsedRealtime() - lastFrame)
                    .put("screenReady", frame != null).put("captureActive", projection != null).put("captureError", captureError);
                json(client, status);
            } else if ("/packages".equals(path)) {
                JSONArray result = new JSONArray();
                for (PackageInfo info : getPackageManager().getInstalledPackages(PackageManager.GET_PERMISSIONS | PackageManager.GET_DISABLED_COMPONENTS)) result.put(packageJson(info));
                json(client, new JSONObject().put("packages", result));
            } else if ("/components".equals(path)) {
                PackageInfo info = getPackageManager().getPackageInfo(query.get("package"), PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES | PackageManager.GET_PROVIDERS | PackageManager.GET_RECEIVERS | PackageManager.GET_PERMISSIONS | PackageManager.GET_DISABLED_COMPONENTS);
                JSONObject result = packageJson(info);
                result.put("activities", components(info.activities)).put("services", components(info.services))
                    .put("receivers", components(info.receivers)).put("providers", components(info.providers));
                json(client, result);
            } else if ("/apk".equals(path)) {
                ApplicationInfo info = getPackageManager().getApplicationInfo(query.get("package"), 0);
                String filename = info.sourceDir;
                if (query.containsKey("split")) {
                    int index = Integer.parseInt(query.get("split"));
                    if (info.splitSourceDirs == null || index < 0 || index >= info.splitSourceDirs.length) throw new IOException("Invalid split index");
                    filename = info.splitSourceDirs[index];
                }
                File file = new File(filename);
                try (InputStream apk = new FileInputStream(file)) {
                    head(client, 200, "application/vnd.android.package-archive", file.length());
                    byte[] buffer = new byte[65536]; int count;
                    while ((count = apk.read(buffer)) != -1) client.getOutputStream().write(buffer, 0, count);
                }
            } else if ("/screen.jpg".equals(path)) {
                byte[] current = frame;
                reply(client, current == null ? 503 : 200, current == null ? "text/plain" : "image/jpeg",
                    current == null ? "Screen sharing is not ready".getBytes(StandardCharsets.UTF_8) : current);
            } else if ("/diagnostics".equals(path)) {
                JSONObject result = new JSONObject();
                for (String key : new String[]{"adb_enabled", "development_settings_enabled", "device_provisioned", "verifier_verify_adb_installs", "package_verifier_enable"}) {
                    try { result.put(key, Settings.Global.getString(getContentResolver(), key)); }
                    catch (Exception e) { result.put(key, e.toString()); }
                }
                Bundle restrictions = ((UserManager)getSystemService(USER_SERVICE)).getUserRestrictions();
                JSONObject restrictionsJson = new JSONObject();
                for (String key : restrictions.keySet()) restrictionsJson.put(key, restrictions.getBoolean(key));
                result.put("userRestrictions", restrictionsJson);
                java.lang.Process process = new ProcessBuilder("/system/bin/getprop").redirectErrorStream(true).start();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream props = process.getInputStream()) {
                    byte[] buffer = new byte[4096]; int count;
                    while (bytes.size() < 262144 && (count = props.read(buffer)) != -1) bytes.write(buffer, 0, count);
                } finally { process.destroy(); }
                result.put("getprop", bytes.toString("UTF-8"));
                json(client, result);
            } else reply(client, 404, "text/plain", "Unknown endpoint".getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { /* End this request, retaining the service. */ }
    }

    private static String limitedLine(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder(); int c;
        while ((c = reader.read()) != -1) {
            if (c == '\n') return line.toString();
            if (c != '\r') line.append((char)c);
            if (line.length() > 8192) throw new IOException("Line too long");
        }
        return line.length() == 0 ? null : line.toString();
    }

    private JSONObject packageJson(PackageInfo info) throws JSONException {
        ApplicationInfo app = info.applicationInfo;
        return new JSONObject().put("package", info.packageName).put("version", info.versionName)
            .put("label", app.loadLabel(getPackageManager()).toString()).put("uid", app.uid)
            .put("enabled", app.enabled).put("system", (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
            .put("sourceDir", app.sourceDir).put("splitSourceDirs", app.splitSourceDirs == null ? new JSONArray() : new JSONArray(Arrays.asList(app.splitSourceDirs)))
            .put("requestedPermissions", info.requestedPermissions == null ? new JSONArray() : new JSONArray(Arrays.asList(info.requestedPermissions)));
    }

    private static JSONArray components(ComponentInfo[] values) throws JSONException {
        JSONArray result = new JSONArray();
        if (values != null) for (ComponentInfo value : values) result.put(new JSONObject()
            .put("name", value.name).put("enabled", value.enabled).put("exported", value.exported));
        return result;
    }

    private static void json(Socket socket, JSONObject body) throws IOException {
        reply(socket, 200, "application/json", body.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void head(Socket socket, int status, String type, long length) throws IOException {
        String reason = status == 200 ? "OK" : status == 403 ? "Forbidden" : status == 404 ? "Not Found" : status == 503 ? "Unavailable" : "Error";
        String header = "HTTP/1.1 " + status + " " + reason + "\r\nContent-Type: " + type + "\r\nContent-Length: " + length
            + "\r\nCache-Control: no-store\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\n\r\n";
        socket.getOutputStream().write(header.getBytes(StandardCharsets.US_ASCII));
    }

    private static void reply(Socket socket, int status, String type, byte[] body) throws IOException {
        head(socket, status, type, body.length); socket.getOutputStream().write(body);
    }

    private void startCapture(int result, Intent data) {
        stopCapture();
        captureThread = new HandlerThread("tv-bridge-capture"); captureThread.start();
        Handler handler = new Handler(captureThread.getLooper());
        captureHandler = handler;
        MediaProjectionManager manager = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = manager.getMediaProjection(result, data);
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { frame = null; captureError = "Screen sharing stopped"; }
        }, handler);
        DisplayMetrics metrics = new DisplayMetrics();
        ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(metrics);
        int width = Math.min(1280, metrics.widthPixels);
        int height = Math.max(1, Math.round((float)metrics.heightPixels * width / metrics.widthPixels));
        images = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        final ImageReader source = images;
        // Keep the newest buffer queued until the throttle expires. Dropping it in
        // the arrival callback loses the final frame when a UI becomes static.
        final boolean[] scheduled = {false};
        final Runnable encode = () -> {
            synchronized (BridgeService.this) {
                scheduled[0] = false;
                if (images != source) return;
                Image image = null;
                Bitmap padded = null;
                Bitmap crop = null;
                try {
                    image = source.acquireLatestImage();
                    if (image == null) return;
                    Image.Plane plane = image.getPlanes()[0];
                    ByteBuffer pixels = plane.getBuffer();
                    int paddedWidth = plane.getRowStride() / plane.getPixelStride();
                    padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888);
                    padded.copyPixelsFromBuffer(pixels);
                    crop = Bitmap.createBitmap(padded, 0, 0, width, height);
                    ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
                    crop.compress(Bitmap.CompressFormat.JPEG, 85, jpeg);
                    frame = jpeg.toByteArray();
                    lastFrame = SystemClock.elapsedRealtime();
                    frameSequence++;
                    captureError = "";
                } catch (Exception e) { captureError = e.toString(); }
                finally {
                    if (crop != null && crop != padded) crop.recycle();
                    if (padded != null) padded.recycle();
                    if (image != null) image.close();
                }
            }
        };
        images.setOnImageAvailableListener(reader -> {
            if (images != source || scheduled[0]) return;
            scheduled[0] = true;
            handler.postDelayed(encode, Math.max(0, 900 - (SystemClock.elapsedRealtime() - lastFrame)));
        }, handler);
        display = projection.createVirtualDisplay("TV Bridge", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, images.getSurface(), null, handler);
        captureError = "";
    }

    private synchronized void stopCapture() {
        if (captureHandler != null) { captureHandler.removeCallbacksAndMessages(null); captureHandler = null; }
        if (display != null) { display.release(); display = null; }
        if (images != null) { images.close(); images = null; }
        if (projection != null) { projection.stop(); projection = null; }
        if (captureThread != null) { captureThread.quitSafely(); captureThread = null; }
        frame = null;
    }

    @Override public void onDestroy() {
        running = false;
        try { if (listener != null) listener.close(); } catch (IOException ignored) {}
        workers.shutdownNow(); stopCapture(); stopForeground(true); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
