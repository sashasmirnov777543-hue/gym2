package ru.gymkeeper.offline;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/** Wakes the process for an exact rest-timer alarm, including in Doze mode. */
public final class RestTimerReceiver extends BroadcastReceiver {
    static final String ACTION_REST_FINISHED = "ru.gymkeeper.offline.REST_FINISHED";
    static final String EXTRA_TOKEN = "rest_token";
    static final String CHANNEL_ID = "gymkeeper_rest_v3";
    private static final String PREFS = "gymkeeper_rest_alarm";
    private static final String CURRENT = "current_token";
    private static final String SENT = "sent_token";
    private static final int BASE_ID = 8300;

    static void arm(Context context, long token) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(CURRENT, token).putLong(SENT, 0L).apply();
    }

    static void cancel(Context context, long token) {
        if (token == 0L) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(SENT, token).apply();
    }

    static synchronized boolean claim(Context context, long token) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (token == 0L || prefs.getLong(CURRENT, 0L) != token || prefs.getLong(SENT, 0L) == token) return false;
        prefs.edit().putLong(SENT, token).commit();
        return true;
    }

    static void ensureChannel(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Окончание отдыха", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Три сигнала после завершения таймера отдыха");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 250});
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_REST_FINISHED.equals(intent.getAction())) return;
        long token = intent.getLongExtra(EXTRA_TOKEN, 0L);
        if (!claim(context, token)) return;
        ensureChannel(context);
        PendingResult result = goAsync();
        Handler handler = new Handler(Looper.getMainLooper());
        for (int i = 0; i < 3; i++) {
            final int signal = i;
            handler.postDelayed(() -> post(context, signal), i * 800L);
        }
        handler.postDelayed(result::finish, 3000L);
    }

    private static void post(Context context, int signal) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        Intent open = new Intent(context, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("Отдых завершён")
                .setContentText("Можно начинать следующий подход")
                .setContentIntent(content)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(false)
                .build();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(BASE_ID + signal, notification);
        if (signal == 2) new Handler(Looper.getMainLooper()).postDelayed(() -> {
            manager.cancel(BASE_ID);
            manager.cancel(BASE_ID + 1);
        }, 5000L);
    }
}
