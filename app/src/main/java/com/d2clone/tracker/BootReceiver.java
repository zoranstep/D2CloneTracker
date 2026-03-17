package com.d2clone.tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        int interval = Math.max(prefs.getInt("interval_minutes", 15), 15);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        WorkManager wm = WorkManager.getInstance(context);

        wm.enqueueUniquePeriodicWork("dclone_monitor", ExistingPeriodicWorkPolicy.UPDATE,
                new PeriodicWorkRequest.Builder(DCloneWorker.class, interval, TimeUnit.MINUTES)
                        .setConstraints(constraints).build());

        wm.enqueueUniquePeriodicWork("tz_monitor", ExistingPeriodicWorkPolicy.UPDATE,
                new PeriodicWorkRequest.Builder(TZWorker.class, interval, TimeUnit.MINUTES)
                        .setConstraints(constraints).build());
    }
}
