package com.d2clone.tracker;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class DCloneWorker extends Worker {

    public DCloneWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Context ctx = getApplicationContext();
            SharedPreferences prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
            String region = prefs.getString("region", "all");
            String ladder = prefs.getString("ladder", "all");
            String hc = prefs.getString("hc", "all");
            String ver = prefs.getString("ver", "all");

            // Read which stages should trigger notifications (checkboxes 1-6)
            boolean[] notifyStages = new boolean[7]; // index 1-6
            for (int s = 1; s <= 6; s++) {
                notifyStages[s] = prefs.getBoolean("notify_stage_" + s, s >= 5);
            }

            StringBuilder urlBuilder = new StringBuilder("https://diablo2.io/dclone_api.php?");
            if (!region.equals("all")) urlBuilder.append("region=").append(region).append("&");
            if (!ladder.equals("all")) urlBuilder.append("ladder=").append(ladder).append("&");
            if (!hc.equals("all")) urlBuilder.append("hc=").append(hc).append("&");
            if (!ver.equals("all")) urlBuilder.append("ver=").append(ver).append("&");
            urlBuilder.append("sk=p&sd=d");

            URL url = new URL(urlBuilder.toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONArray array = new JSONArray(sb.toString());
            List<DCloneEntry> alertEntries = new ArrayList<>();

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                DCloneEntry entry = new DCloneEntry(
                        obj.getInt("progress"),
                        obj.getInt("region"),
                        obj.getInt("ladder"),
                        obj.getInt("hc"),
                        obj.optInt("ver", 1),
                        obj.getLong("timestamped")
                );

                int stage = entry.progress;
                if (stage >= 1 && stage <= 6 && notifyStages[stage]) {
                    // Key includes stage so each stage change gets its own notification
                    String key = "notified_" + entry.region + "_" + entry.ladder
                            + "_" + entry.hc + "_" + entry.ver + "_stage_" + stage;
                    long lastNotified = prefs.getLong(key, 0);
                    if (entry.timestamp > lastNotified) {
                        alertEntries.add(entry);
                        prefs.edit().putLong(key, entry.timestamp).apply();
                    }
                }
            }

            for (DCloneEntry entry : alertEntries) {
                sendNotification(ctx, entry);
            }

            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private void sendNotification(Context ctx, DCloneEntry entry) {
        String title, message;
        int priority;

        String verLabel = entry.ver == 2 ? "RotW" : "LoD";

        if (entry.isWalking()) {
            title = "🔴 [" + verLabel + "] DIABLO CLONE IS WALKING!";
            message = entry.getModeLabel() + "\nProgress: 6/6 — Get in a Hell game NOW!";
            priority = NotificationCompat.PRIORITY_MAX;
        } else {
            title = "⚠️ [" + verLabel + "] DClone: " + entry.progress + "/6";
            message = entry.getModeLabel() + "\nProgress: " + entry.progress + "/6";
            priority = entry.isAlert() ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT;
        }

        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, MainActivity.DCLONE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(priority)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 500, 200, 500});

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        // Unique ID per server + ver so each gets its own notification slot
        int notifId = entry.region * 1000 + entry.ladder * 100 + entry.hc * 10 + entry.ver;
        nm.notify(notifId, builder.build());
    }
}
