package com.d2clone.tracker;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TrackerService extends Service {

    private static final String TAG = "TrackerService";
    private static final String TZ_IMAGE_URL = "https://api.d2tz.info/public/tz_image?t=loot";
    
    private static final String[] CURRENT_KEYWORDS = {"current", "now", "active", "live", "terrorized"};
    private static final String[] NEXT_KEYWORDS = {"next", "soon", "coming", "displaying", "upcoming"};

    private PowerManager.WakeLock wakeLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, MainActivity.TRACKER_SERVICE_CHANNEL_ID)
                .setContentTitle("D2 Clone Tracker")
                .setContentText("Checking for updates...")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .build();

        startForeground(999, notification);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "D2Tracker:WakeLock");
        wakeLock.acquire(10 * 60 * 1000L /*10 minutes max*/);

        new Thread(() -> {
            try {
                performFullUpdate();
            } finally {
                scheduleNext();
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void performFullUpdate() {
        Context ctx = getApplicationContext();
        SharedPreferences prefs = ctx.getSharedPreferences("settings", MODE_PRIVATE);

        // Always check DClone once
        checkDClone(ctx, prefs);

        // Check Terror Zones with retry logic for the "Next" zone
        int retryCount = 0;
        boolean nextFound = false;
        while (retryCount < 6) { // Retry up to 6 times (3 minutes total)
            Log.d(TAG, "Checking Terror Zones (Attempt " + (retryCount + 1) + ")...");
            nextFound = checkTerrorZones(ctx, prefs);
            
            // If next found or we are not even watching for next, we can stop
            if (nextFound || !prefs.getBoolean("tz_alert_next", true)) {
                break;
            }

            retryCount++;
            if (retryCount < 6) {
                try {
                    Log.d(TAG, "Next TZ unknown, retrying in 30s...");
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void checkDClone(Context ctx, SharedPreferences prefs) {
        try {
            String region = prefs.getString("region", "all");
            String ladder = prefs.getString("ladder", "all");
            String hc     = prefs.getString("hc", "all");
            String ver    = prefs.getString("ver", "all");

            List<String> urlsToFetch = new ArrayList<>();
            if (ver.equals("all")) {
                urlsToFetch.add(buildUrl(region, ladder, hc, "1"));
                urlsToFetch.add(buildUrl(region, ladder, hc, "2"));
            } else {
                urlsToFetch.add(buildUrl(region, ladder, hc, ver));
            }

            for (String urlStr : urlsToFetch) {
                fetchAndProcessDClone(ctx, prefs, urlStr);
            }
        } catch (Exception e) {
            Log.e(TAG, "DClone check failed", e);
        }
    }

    private String buildUrl(String region, String ladder, String hc, String ver) {
        StringBuilder urlBuilder = new StringBuilder("https://diablo2.io/dclone_api.php?");
        if (!region.equals("all")) urlBuilder.append("region=").append(region).append("&");
        if (!ladder.equals("all")) urlBuilder.append("ladder=").append(ladder).append("&");
        if (!hc.equals("all"))     urlBuilder.append("hc=").append(hc).append("&");
        if (!ver.equals("all"))    urlBuilder.append("ver=").append(ver).append("&");
        urlBuilder.append("sk=p&sd=d");
        return urlBuilder.toString();
    }

    private void fetchAndProcessDClone(Context ctx, SharedPreferences prefs, String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(15000);
        if (conn.getResponseCode() == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONArray array = new JSONArray(sb.toString());
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                int stage = obj.getInt("progress");
                long ts = obj.getLong("timestamped");
                
                DCloneEntry entry = new DCloneEntry(
                        stage,
                        obj.getInt("region"),
                        obj.getInt("ladder"),
                        obj.getInt("hc"),
                        obj.optInt("ver", 1),
                        ts
                );

                String serverKey = entry.region + "_" + entry.ladder + "_" + entry.hc + "_" + entry.ver;
                if (prefs.getBoolean("notify_stage_" + stage, false)) {
                    String notifyKey = "notified_" + serverKey + "_s" + stage;
                    if (ts > prefs.getLong(notifyKey, 0)) {
                        sendDCloneNotification(ctx, entry);
                        prefs.edit().putLong(notifyKey, ts).apply();
                    }
                }
            }
        }
    }

    /**
     * @return true if the Next zone was successfully identified
     */
    private boolean checkTerrorZones(Context ctx, SharedPreferences prefs) {
        try {
            Set<String> watchedGroups = prefs.getStringSet("tz_watched_groups", null);
            if (watchedGroups == null || watchedGroups.isEmpty()) return false;

            URL url = new URL(TZ_IMAGE_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(15000);
            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) return false;

            InputImage image = InputImage.fromBitmap(bitmap, 0);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            Text result = Tasks.await(recognizer.process(image));

            List<Text.Line> allLines = new ArrayList<>();
            for (Text.TextBlock block : result.getTextBlocks()) allLines.addAll(block.getLines());
            Collections.sort(allLines, (l1, l2) -> Integer.compare(l1.getBoundingBox().top, l2.getBoundingBox().top));

            Set<String> curDetectedZones = new HashSet<>();
            Set<String> nxtDetectedZones = new HashSet<>();
            int cat = 0;
            for (Text.Line line : allLines) {
                String txt = line.getText().toLowerCase();
                Log.d(TAG, "OCR Line: " + txt);
                for (String kw : CURRENT_KEYWORDS) if (txt.contains(kw)) cat = 1;
                for (String kw : NEXT_KEYWORDS) if (txt.contains(kw)) cat = 2;
                
                List<String> found = TerrorZone.findZonesInText(txt);
                if (cat == 1) curDetectedZones.addAll(found);
                else if (cat == 2) nxtDetectedZones.addAll(found);
            }

            TerrorZone.Group currentGroup = TerrorZone.findBestGroup(curDetectedZones);
            TerrorZone.Group nextGroup = TerrorZone.findBestGroup(nxtDetectedZones);

            // Use 30-minute buckets to distinguish between :00 and :30 checks
            long bucket = System.currentTimeMillis() / (30 * 60 * 1000);
            
            if (currentGroup != null && prefs.getBoolean("tz_alert_current", true)) {
                checkAndNotify(ctx, prefs, watchedGroups, currentGroup.name, bucket, true);
            }
            if (nextGroup != null && prefs.getBoolean("tz_alert_next", true)) {
                checkAndNotify(ctx, prefs, watchedGroups, nextGroup.name, bucket, false);
            }

            return nextGroup != null; // Return true if we actually found a zone for Next

        } catch (Exception e) {
            Log.e(TAG, "TZ check failed", e);
            return false;
        }
    }

    private void checkAndNotify(Context ctx, SharedPreferences prefs, Set<String> watched, String groupName, long ts, boolean isCur) {
        if (watched.contains(groupName)) {
            String key = "tz_notif_" + (isCur ? "c" : "n") + "_" + groupName.replace(" ", "_");
            if (ts > prefs.getLong(key, 0)) {
                sendTZNotification(ctx, groupName, isCur);
                prefs.edit().putLong(key, ts).apply();
            }
        }
    }

    private void scheduleNext() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Calculate next :00 or :30 window (at 25 second mark)
        long now = System.currentTimeMillis();
        long thirtyMinutesMillis = 30 * 60 * 1000;
        long nextTriggerTime = (now / thirtyMinutesMillis + 1) * thirtyMinutesMillis + 25000;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerTime, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerTime, pi);
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerTime, pi);
        }
        Log.d(TAG, "Scheduled next alarm for: " + nextTriggerTime);
    }

    private void sendDCloneNotification(Context ctx, DCloneEntry entry) {
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = "🔥 DClone Update: Stage " + entry.progress;
        String message = entry.getModeLabel();

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = new NotificationCompat.Builder(ctx, MainActivity.DCLONE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        
        String serverKey = entry.region + "_" + entry.ladder + "_" + entry.hc + "_" + entry.ver;
        nm.notify(("dclone_" + serverKey).hashCode(), notification);
    }

    private void sendTZNotification(Context ctx, String group, boolean isCur) {
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = new NotificationCompat.Builder(ctx, MainActivity.TZ_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(isCur ? "🔥 Terror Zone NOW" : "⏰ Next Terror Zone")
                .setContentText(group + (isCur ? " is terrorized!" : " is next."))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        
        // Use unique IDs for Current (1xxx) and Next (20xxx) to avoid overwriting each other
        int notifId = (isCur ? 10000 : 20000) + Math.abs(group.hashCode() % 10000);
        nm.notify(notifId, notification);
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
