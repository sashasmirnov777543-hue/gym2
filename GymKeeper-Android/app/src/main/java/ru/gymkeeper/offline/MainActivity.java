package ru.gymkeeper.offline;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.KeyguardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int IMPORT_BACKUP_REQUEST = 4107;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 4108;
    private static final int BLUETOOTH_PERMISSION_REQUEST = 4109;
    private static final int DEVICE_AUTH_REQUEST = 4110;
    private static final String REST_CHANNEL_ID = "gymkeeper_rest_v2";
    private static final int REST_NOTIFICATION_BASE_ID = 7300;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable restTimerRunnable;
    private long restTimerDeadlineMs;
    private boolean restSignalSent = true;
    private WebView webView;
    private HeartRateBle heartRateBle;
    private boolean connectHeartRateAfterPermission;
    private boolean authenticationInProgress;
    private long lastAuthenticatedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(17, 24, 39));
        getWindow().setNavigationBarColor(Color.rgb(11, 18, 32));
        configureRestNotifications();
        heartRateBle = new HeartRateBle(this, this::emitHeartRateToJs);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(248, 250, 252));
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(false);

        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return uri.getScheme() != null && !"file".equals(uri.getScheme());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void configureRestNotifications() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
                REST_CHANNEL_ID,
                "Окончание отдыха",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Три сигнала после завершения таймера отдыха");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 250});
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
            );
        }
    }

    public final class AndroidBridge {
        @JavascriptInterface
        public void exportBackup(String json, String filename) {
            runOnUiThread(() -> saveBackup(json, filename));
        }

        @JavascriptInterface
        public void chooseBackup() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                startActivityForResult(intent, IMPORT_BACKUP_REQUEST);
            });
        }

        @JavascriptInterface
        public void keepScreenOn(boolean enabled) {
            runOnUiThread(() -> webView.setKeepScreenOn(enabled));
        }

        @JavascriptInterface
        public void scheduleRestTimer(int seconds) {
            runOnUiThread(() -> scheduleNativeRestTimer(seconds));
        }

        @JavascriptInterface
        public void cancelRestTimer() {
            runOnUiThread(MainActivity.this::cancelNativeRestTimer);
        }

        @JavascriptInterface
        public void completeRestTimer() {
            runOnUiThread(() -> finishNativeRestTimer(false));
        }

        @JavascriptInterface
        public void connectHeartRate() {
            runOnUiThread(() -> {
                if (heartRateBle.hasPermissions()) {
                    heartRateBle.start();
                } else {
                    connectHeartRateAfterPermission = true;
                    requestPermissions(heartRateBle.requiredPermissions(), BLUETOOTH_PERMISSION_REQUEST);
                }
            });
        }

        @JavascriptInterface
        public void disconnectHeartRate() {
            runOnUiThread(() -> {
                heartRateBle.stop();
                emitHeartRateToJs("idle", null, null, null);
            });
        }

        @JavascriptInterface
        public boolean isAppLockEnabled() {
            return getPreferences(MODE_PRIVATE).getBoolean("app_lock_enabled", true);
        }

        @JavascriptInterface
        public void setAppLockEnabled(boolean enabled) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("app_lock_enabled", enabled).apply();
            if (enabled) runOnUiThread(() -> {
                lastAuthenticatedAt = 0L;
                requestDeviceAuthentication();
            });
        }

        @JavascriptInterface
        public void closeApp() {
            runOnUiThread(MainActivity.this::finish);
        }

        @JavascriptInterface
        public void toast(String text) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show());
        }
    }

    private void scheduleNativeRestTimer(int seconds) {
        cancelNativeRestTimer();
        long delayMs = Math.max(1, seconds) * 1000L;
        restTimerDeadlineMs = SystemClock.elapsedRealtime() + delayMs;
        restSignalSent = false;
        restTimerRunnable = () -> finishNativeRestTimer(true);
        mainHandler.postDelayed(restTimerRunnable, delayMs);
    }

    private void cancelNativeRestTimer() {
        if (restTimerRunnable != null) mainHandler.removeCallbacks(restTimerRunnable);
        restTimerRunnable = null;
        restTimerDeadlineMs = 0L;
        restSignalSent = true;
    }

    /**
     * Двойная страховка: этот метод вызывается и нативным Handler, и экранным
     * таймером WebView. Флаг гарантирует ровно один тройной сигнал.
     */
    private void finishNativeRestTimer(boolean fromNativeHandler) {
        if (restSignalSent || restTimerDeadlineMs == 0L) return;
        long remaining = restTimerDeadlineMs - SystemClock.elapsedRealtime();
        if (!fromNativeHandler && remaining > 750L) return;
        if (remaining > 0L) {
            if (restTimerRunnable != null) mainHandler.removeCallbacks(restTimerRunnable);
            restTimerRunnable = () -> finishNativeRestTimer(true);
            mainHandler.postDelayed(restTimerRunnable, remaining);
            return;
        }
        restSignalSent = true;
        restTimerRunnable = null;
        restTimerDeadlineMs = 0L;
        sendTripleRestSignal();
    }

    private void sendTripleRestSignal() {
        // Три отдельных уведомления надёжнее передаются Huawei Health на часы,
        // чем один телефонный vibrationPattern.
        for (int index = 0; index < 3; index++) {
            final int signal = index;
            mainHandler.postDelayed(() -> postRestNotification(signal), index * 800L);
        }
    }

    private void postRestNotification(int signal) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (signal == 0) vibratePhoneTriple();
            return;
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new Notification.Builder(this, REST_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("Отдых завершён")
                .setContentText("Можно начинать следующий подход")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(false)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(REST_NOTIFICATION_BASE_ID + signal, notification);
        if (signal == 2) {
            mainHandler.postDelayed(() -> {
                manager.cancel(REST_NOTIFICATION_BASE_ID);
                manager.cancel(REST_NOTIFICATION_BASE_ID + 1);
            }, 5000L);
        }
    }

    private void vibratePhoneTriple() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(
                    new long[]{0, 250, 350, 250, 350, 250},
                    -1
            ));
        }
    }

    private void emitHeartRateToJs(String status, Integer bpm, String deviceName, String error) {
        if (webView == null) return;
        String script = "window.GymKeeper && window.GymKeeper.onHeartRate("
                + JSONObject.quote(status) + ","
                + (bpm == null ? "null" : bpm) + ","
                + (deviceName == null ? "null" : JSONObject.quote(deviceName)) + ","
                + (error == null ? "null" : JSONObject.quote(error)) + ")";
        runOnUiThread(() -> webView.evaluateJavascript(script, null));
    }

    private void saveBackup(String json, String requestedName) {
        String safeName = requestedName != null && requestedName.endsWith(".json")
                ? requestedName.replaceAll("[^a-zA-Z0-9._-]", "-")
                : "gymkeeper-backup.json";
        try {
            OutputStream output;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GymKeeper");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("Не удалось создать файл");
                output = getContentResolver().openOutputStream(uri);
            } else {
                File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "GymKeeper");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Не удалось создать папку");
                output = new FileOutputStream(new File(dir, safeName));
            }
            if (output == null) throw new IllegalStateException("Не удалось открыть файл");
            try (OutputStream out = output) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(this, "Копия сохранена: Загрузки/GymKeeper", Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            Toast.makeText(this, "Ошибка сохранения: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DEVICE_AUTH_REQUEST) {
            authenticationInProgress = false;
            if (resultCode == RESULT_OK) lastAuthenticatedAt = SystemClock.elapsedRealtime();
            else finish();
            return;
        }
        if (requestCode != IMPORT_BACKUP_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Uri uri = data.getData();
            InputStream stream = getContentResolver().openInputStream(uri);
            if (stream == null) throw new IllegalStateException("Файл недоступен");
            StringBuilder text = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) text.append(line).append('\n');
            }
            String encoded = android.util.Base64.encodeToString(
                    text.toString().getBytes(StandardCharsets.UTF_8),
                    android.util.Base64.NO_WRAP
            );
            webView.evaluateJavascript("window.GymKeeper.receiveImportBase64('" + encoded + "')", null);
        } catch (Exception error) {
            Toast.makeText(this, "Ошибка чтения: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST && connectHeartRateAfterPermission) {
            connectHeartRateAfterPermission = false;
            if (heartRateBle.hasPermissions()) heartRateBle.start();
            else emitHeartRateToJs("error", null, null, "Без разрешения Bluetooth пульс недоступен");
        }
    }

    private void requestDeviceAuthentication() {
        if (!getPreferences(MODE_PRIVATE).getBoolean("app_lock_enabled", true)) return;
        if (authenticationInProgress || (lastAuthenticatedAt > 0L && SystemClock.elapsedRealtime() - lastAuthenticatedAt < 300000L)) return;
        KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        Intent intent = keyguard == null ? null : keyguard.createConfirmDeviceCredentialIntent(
                "GymKeeper",
                "Подтвердите личность для доступа к тренировочным данным"
        );
        if (intent != null) {
            authenticationInProgress = true;
            startActivityForResult(intent, DEVICE_AUTH_REQUEST);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mainHandler.postDelayed(this::requestDeviceAuthentication, 600L);
    }

    @Override
    public void onBackPressed() {
        if (webView != null) webView.evaluateJavascript("window.appBack && window.appBack()", null);
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (heartRateBle != null) heartRateBle.stop();
        if (webView != null) {
            webView.setKeepScreenOn(false);
            webView.destroy();
        }
        super.onDestroy();
    }
}
