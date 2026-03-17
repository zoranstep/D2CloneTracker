package com.d2clone.tracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.content.Context.MODE_PRIVATE;

public class DCloneFragment extends Fragment {

    private RecyclerView recyclerView;
    private DCloneAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvLastUpdated, tvStatus;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private long lastFetchTime = 0;
    private static final long FETCH_COOLDOWN_MS = 60000; // 60 seconds

    // Track last used filter settings
    private String lastRegion, lastLadder, lastHC, lastVer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dclone, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView  = view.findViewById(R.id.recyclerView);
        progressBar   = view.findViewById(R.id.progressBar);
        tvLastUpdated = view.findViewById(R.id.tvLastUpdated);
        tvStatus      = view.findViewById(R.id.tvStatus);
        TextView tvCredit = view.findViewById(R.id.tvDCloneCredit);

        setupClickableLink(tvCredit, "Data courtesy of diablo2.io", "diablo2.io", "https://diablo2.io");

        Button btnRefresh = view.findViewById(R.id.btnDCloneRefresh);
        btnRefresh.setOnClickListener(v -> fetchData(true));

        Button btnSettings = view.findViewById(R.id.btnDCloneSettings);
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), SettingsActivity.class));
        });

        adapter = new DCloneAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setupClickableLink(TextView textView, String fullText, String linkPart, String url) {
        SpannableString ss = new SpannableString(fullText);
        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            }
        };
        int start = fullText.indexOf(linkPart);
        int end = start + linkPart.length();
        if (start != -1) {
            ss.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(ss);
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchData(false);
    }

    public void fetchData(boolean isManual) {
        if (!isAdded()) return;
        
        SharedPreferences prefs = requireContext().getSharedPreferences("settings", MODE_PRIVATE);
        String region = prefs.getString("region", "all");
        String ladder = prefs.getString("ladder", "all");
        String hc     = prefs.getString("hc", "all");
        String ver    = prefs.getString("ver", "all");

        // Check if settings have changed since last fetch
        boolean settingsChanged = !Objects.equals(region, lastRegion) ||
                                  !Objects.equals(ladder, lastLadder) ||
                                  !Objects.equals(hc, lastHC) ||
                                  !Objects.equals(ver, lastVer);

        long currentTime = System.currentTimeMillis();
        long diff = currentTime - lastFetchTime;

        // Bypass cooldown only if settings changed
        if (!settingsChanged && diff < FETCH_COOLDOWN_MS) {
            if (isManual) {
                long secondsLeft = (FETCH_COOLDOWN_MS - diff) / 1000;
                Toast.makeText(requireContext(), 
                    "Fair Usage: Please wait " + secondsLeft + "s before refreshing again.", 
                    Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (progressBar == null) return;
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("Fetching data from diablo2.io...");

        executor.execute(() -> {
            try {
                if (!isAdded()) return;

                StringBuilder urlBuilder = new StringBuilder("https://diablo2.io/dclone_api.php?");
                if (!region.equals("all")) urlBuilder.append("region=").append(region).append("&");
                if (!ladder.equals("all")) urlBuilder.append("ladder=").append(ladder).append("&");
                if (!hc.equals("all"))     urlBuilder.append("hc=").append(hc).append("&");
                if (!ver.equals("all"))    urlBuilder.append("ver=").append(ver).append("&");
                urlBuilder.append("sk=p&sd=d");

                URL url = new URL(urlBuilder.toString());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new Exception("Server returned code: " + responseCode);
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONArray array = new JSONArray(sb.toString());
                List<DCloneEntry> entries = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    entries.add(new DCloneEntry(
                            obj.getInt("progress"),
                            obj.getInt("region"),
                            obj.getInt("ladder"),
                            obj.getInt("hc"),
                            obj.optInt("ver", 1),
                            obj.getLong("timestamped")
                    ));
                }

                lastFetchTime = System.currentTimeMillis();
                // Update tracked settings
                lastRegion = region;
                lastLadder = ladder;
                lastHC = hc;
                lastVer = ver;

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    adapter.setData(entries);
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText(entries.size() + " entries loaded (via diablo2.io)");
                    tvLastUpdated.setText("Updated: " + android.text.format.DateFormat
                            .format("HH:mm:ss", new java.util.Date()));
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressBar.setVisibility(View.GONE);
                    String errorMsg = "diablo2.io API is currently unavailable or down.";
                    tvStatus.setText("Error: " + errorMsg);
                    Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
