package com.d2clone.tracker;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TZWorker extends Worker {

    public static final String TZ_CHANNEL_ID = "tz_alerts";
    private static final String TZ_IMAGE_URL = "https://api.d2tz.info/public/tz_image?t=loot";

    private static final String[] CURRENT_KEYWORDS = {"current", "now", "active", "live"};
    private static final String[] NEXT_KEYWORDS = {"next", "soon", "coming", "displaying"};

    public TZWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Context ctx = getApplicationContext();
            SharedPreferences prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);

            Set<String> watchedGroups = prefs.getStringSet("tz_watched_groups", null);
            if (watchedGroups == null || watchedGroups.isEmpty()) return Result.success();

            URL url = new URL(TZ_IMAGE_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            InputStream input = conn.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) return Result.retry();

            InputImage image = InputImage.fromBitmap(bitmap, 0);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            Text result = Tasks.await(recognizer.process(image));

            List<Text.Line> lines = new ArrayList<>();
            for (Text.TextBlock b : result.getTextBlocks()) lines.addAll(b.getLines());
            Collections.sort(lines, (l1, l2) -> Integer.compare(l1.getBoundingBox().top, l2.getBoundingBox().top));

            Set<String> detectedZonesCurrent = new HashSet<>();
            Set<String> detectedZonesNext = new HashSet<>();
            int state = 0; // 0=none, 1=current, 2=next

            for (Text.Line line : lines) {
                String txt = line.getText().toLowerCase();
                for (String kw : CURRENT_KEYWORDS) if (txt.contains(kw)) state = 1;
                for (String kw : NEXT_KEYWORDS) if (txt.contains(kw)) state = 2;

                List<String> zones = findZonesFuzzy(txt);
                if (state == 1) detectedZonesCurrent.addAll(zones);
                else if (state == 2) detectedZonesNext.addAll(zones);
            }

            long hourlyBucket = System.currentTimeMillis() / 3600000;
            boolean alertCur = prefs.getBoolean("tz_alert_current", true);
            boolean alertNxt = prefs.getBoolean("tz_alert_next", true);

            if (alertCur) checkGroupsAndNotify(ctx, prefs, watchedGroups, detectedZonesCurrent, hourlyBucket, true);
            if (alertNxt) checkGroupsAndNotify(ctx, prefs, watchedGroups, detectedZonesNext, hourlyBucket, false);

            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private void checkGroupsAndNotify(Context ctx, SharedPreferences prefs, Set<String> watchedGroups, Set<String> detectedZones, long ts, boolean isCur) {
        for (String groupName : watchedGroups) {
            TerrorZone.Group group = findGroupByName(groupName);
            if (group == null) continue;

            boolean groupIsTerrorized = false;
            for (String z : group.zones) {
                if (detectedZones.contains(z)) {
                    groupIsTerrorized = true;
                    break;
                }
            }

            if (groupIsTerrorized) {
                String key = "tz_group_notified_" + (isCur ? "cur" : "nxt") + "_" + groupName.replace(" ", "_");
                if (ts > prefs.getLong(key, 0)) {
                    prefs.edit().putLong(key, ts).apply();
                    sendTZNotification(ctx, groupName, isCur);
                }
            }
        }
    }

    private TerrorZone.Group findGroupByName(String name) {
        for (TerrorZone.Group g : TerrorZone.GROUPS) if (g.name.equals(name)) return g;
        return null;
    }

    private List<String> findZonesFuzzy(String text) {
        String haystack = text.toLowerCase().replaceAll("[^a-z0-9]", "");
        List<Match> matches = new ArrayList<>();
        for (String zone : TerrorZone.ALL_ZONES) {
            String needle = zone.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (needle.length() < 3) continue;
            int p = haystack.indexOf(needle);
            while (p != -1) {
                matches.add(new Match(zone, p));
                p = haystack.indexOf(needle, p + 1);
            }
        }
        Collections.sort(matches);
        List<Match> filtered = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            Match m1 = matches.get(i);
            boolean isSub = false;
            for (int j = 0; j < matches.size(); j++) {
                if (i == j) continue;
                Match m2 = matches.get(j);
                String n1 = m1.name.toLowerCase().replaceAll("[^a-z0-9]", "");
                String n2 = m2.name.toLowerCase().replaceAll("[^a-z0-9]", "");
                if (m1.pos >= m2.pos && (m1.pos + n1.length()) <= (m2.pos + n2.length())) {
                    if (n2.length() > n1.length()) { isSub = true; break; }
                }
            }
            if (!isSub) {
                boolean dup = false;
                for (Match f : filtered) if (f.name.equals(m1.name)) dup = true;
                if (!dup) filtered.add(m1);
            }
        }
        List<String> results = new ArrayList<>();
        for (Match m : filtered) results.add(m.name);
        return results;
    }

    private void sendTZNotification(Context ctx, String groupName, boolean isCurrent) {
        String title = isCurrent ? "🔥 Terror Zone NOW" : "⏰ Next Terror Zone";
        String message = groupName + (isCurrent ? " is terrorized!" : " is next.");
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, TZ_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setAutoCancel(true);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(groupName.hashCode(), builder.build());
    }

    private static class Match implements Comparable<Match> {
        String name; int pos;
        Match(String n, int p) { name = n; pos = p; }
        @Override public int compareTo(Match o) { return Integer.compare(this.pos, o.pos); }
    }
}
