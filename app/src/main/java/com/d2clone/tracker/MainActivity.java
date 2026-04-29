package com.d2clone.tracker;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NotificationManagerCompat;
import androidx.viewpager2.widget.ViewPager2;
import androidx.work.WorkManager;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import androidx.activity.EdgeToEdge;

public class MainActivity extends AppCompatActivity {

    public static final String DCLONE_CHANNEL_ID = "dclone_alerts";
    public static final String TZ_CHANNEL_ID = "tz_alerts";
    public static final String TRACKER_SERVICE_CHANNEL_ID = "tracker_service_channel";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Initialize Mobile Ads SDK
        MobileAds.initialize(this, initializationStatus -> {});

        AdView mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

        createNotificationChannels();

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        ViewPager2 viewPager = findViewById(R.id.viewPager);

        MainPagerAdapter adapter = new MainPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(position == 0 ? "DClone" : "Terror Zones");
        }).attach();

        // Cancel old WorkManager tasks and move to AlarmManager
        WorkManager.getInstance(this).cancelUniqueWork("dclone_monitor");
        WorkManager.getInstance(this).cancelUniqueWork("tz_monitor");
        
        startTrackerAlarm();
        checkNotificationPermission();
        checkBatteryOptimizations();
        checkHuaweiAppLaunch();
    }

    private void checkNotificationPermission() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("Notifications Disabled")
                    .setMessage("You have disabled notifications for this app. You will not receive any alerts for DClone or Terror Zones until you enable them in settings.")
                    .setPositiveButton("Settings", (dialog, which) -> {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                        startActivity(intent);
                    })
                    .setNegativeButton("Ignore", null)
                    .show();
        }
    }

    private void createNotificationChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            // DClone Channel
            NotificationChannel dcloneChannel = new NotificationChannel(
                    DCLONE_CHANNEL_ID,
                    "DClone Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            dcloneChannel.setDescription("Notifications for Diablo Clone progress");
            nm.createNotificationChannel(dcloneChannel);

            // TZ Channel
            NotificationChannel tzChannel = new NotificationChannel(
                    TZ_CHANNEL_ID,
                    "Terror Zone Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            tzChannel.setDescription("Notifications for Terror Zone changes");
            nm.createNotificationChannel(tzChannel);

            // Background Service Channel (Lower importance)
            NotificationChannel serviceChannel = new NotificationChannel(
                    TRACKER_SERVICE_CHANNEL_ID,
                    "Tracker Service Status",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Status of background tracking");
            nm.createNotificationChannel(serviceChannel);
        }
    }

    private void startTrackerAlarm() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Ensure tracking is running by scheduling first check immediately
        long triggerTime = SystemClock.elapsedRealtime() + 1000;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pi);
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pi);
        }
    }

    private void checkBatteryOptimizations() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            new AlertDialog.Builder(this)
                    .setTitle("Reliable Notifications")
                    .setMessage("To receive Terror Zone alerts on time, please disable battery optimization for this app.")
                    .setPositiveButton("Settings", (dialog, which) -> {
                        @SuppressLint("BatteryLife")
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .setNegativeButton("Later", null)
                    .show();
        }
    }

    private void checkHuaweiAppLaunch() {
        String manufacturer = Build.MANUFACTURER.toUpperCase();
        if (!manufacturer.contains("HUAWEI") && !manufacturer.contains("HONOR")) {
            return;
        }

        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        if (prefs.getBoolean("skip_huawei_warning", false)) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Background Stability")
                .setMessage("App launch is managed automatically. In order to make the tracker stable and on time, please turn it off and make sure that 'Run in background' is toggled ON (Settings > Battery > App Launch).")
                .setPositiveButton("Settings", (dialog, which) -> {
                    try {
                        Intent intent = new Intent();
                        intent.setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity");
                        startActivity(intent);
                    } catch (Exception e) {
                        try {
                            Intent intent = new Intent();
                            intent.setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity");
                            startActivity(intent);
                        } catch (Exception e2) {
                            try {
                                startActivity(new Intent(Settings.ACTION_SETTINGS));
                            } catch (Exception e3) {
                                // Fallback failed, nothing more we can do
                            }
                        }
                    }
                })
                .setNeutralButton("Don't show again", (dialog, which) -> prefs.edit().putBoolean("skip_huawei_warning", true).apply())
                .setNegativeButton("Later", null)
                .show();
    }
}
