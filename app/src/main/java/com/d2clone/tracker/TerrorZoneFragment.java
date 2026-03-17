package com.d2clone.tracker;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
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

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TerrorZoneFragment extends Fragment {

    private TextView tvCurrentTZ, tvNextTZ;
    private TextView tvLastUpdated, tvStatus, tvRawDebug;
    private ProgressBar progressBar;
    private static final String TZ_IMAGE_URL = "https://api.d2tz.info/public/tz_image?t=loot";

    private static final String[] CURRENT_KEYWORDS = {"current", "now", "active", "live", "terrorized"};
    private static final String[] NEXT_KEYWORDS = {"next", "soon", "coming", "displaying", "upcoming"};

    private long lastFetchTime = 0;
    private static final long FETCH_COOLDOWN_MS = 300000; // 5 minutes

    private int debugTapCount = 0;
    private long lastTapTime = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_terror_zone, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvCurrentTZ   = view.findViewById(R.id.tvCurrentTZ);
        tvNextTZ      = view.findViewById(R.id.tvNextTZ);
        tvLastUpdated = view.findViewById(R.id.tvTZLastUpdated);
        tvStatus      = view.findViewById(R.id.tvTZStatus);
        tvRawDebug    = view.findViewById(R.id.tvTZRawDebug);
        progressBar   = view.findViewById(R.id.tzProgressBar);
        TextView tvCredit = view.findViewById(R.id.tvTZCredit);

        setupClickableLink(tvCredit, "Data courtesy of d2tz.info", "d2tz.info", "https://d2tz.info");

        Button btnRefresh = view.findViewById(R.id.btnTZRefresh);
        Button btnAlertSettings = view.findViewById(R.id.btnTZAlertSettings);

        btnRefresh.setOnClickListener(v -> fetchTZData(true));
        btnAlertSettings.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), TZSettingsActivity.class)));

        // Hide debug info by default
        if (tvRawDebug != null) {
            tvRawDebug.setVisibility(View.GONE);
        }

        // Secret trick: Tap the "Updated: ..." text 7 times to toggle debug view
        tvLastUpdated.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastTapTime > 500) {
                debugTapCount = 0;
            }
            debugTapCount++;
            lastTapTime = now;

            if (debugTapCount >= 7) {
                if (tvRawDebug != null) {
                    boolean isVisible = tvRawDebug.getVisibility() == View.VISIBLE;
                    tvRawDebug.setVisibility(isVisible ? View.GONE : View.VISIBLE);
                    Toast.makeText(getContext(), isVisible ? "Debug hidden" : "Debug enabled", Toast.LENGTH_SHORT).show();
                }
                debugTapCount = 0;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchTZData(false);
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
        if (start != -1) {
            ss.setSpan(clickableSpan, start, start + linkPart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(ss);
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    public void fetchTZData(boolean forceRefresh) {
        long currentTime = System.currentTimeMillis();
        if (!forceRefresh && (currentTime - lastFetchTime < FETCH_COOLDOWN_MS)) {
            return;
        }

        if (progressBar == null) return;
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("Syncing with d2tz.info...");

        new Thread(() -> {
            try {
                URL url = new URL(TZ_IMAGE_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                
                InputStream input = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);

                if (bitmap == null) throw new Exception("Image data invalid");

                InputImage image = InputImage.fromBitmap(bitmap, 0);
                TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                Text result = Tasks.await(recognizer.process(image));

                String fullOcrText = result.getText();
                List<Text.Line> allLines = new ArrayList<>();
                for (Text.TextBlock block : result.getTextBlocks()) {
                    allLines.addAll(block.getLines());
                }
                Collections.sort(allLines, (l1, l2) -> Integer.compare(l1.getBoundingBox().top, l2.getBoundingBox().top));

                Set<String> currentDetectedZones = new HashSet<>();
                Set<String> nextDetectedZones = new HashSet<>();
                int currentCategory = 0; // 0=None, 1=Current, 2=Next

                for (Text.Line line : allLines) {
                    String lineText = line.getText().toLowerCase();
                    for (String kw : CURRENT_KEYWORDS) if (lineText.contains(kw)) currentCategory = 1;
                    for (String kw : NEXT_KEYWORDS) if (lineText.contains(kw)) currentCategory = 2;

                    if (currentCategory == 1) {
                        currentDetectedZones.addAll(TerrorZone.findZonesInText(lineText));
                    } else if (currentCategory == 2) {
                        nextDetectedZones.addAll(TerrorZone.findZonesInText(lineText));
                    }
                }

                TerrorZone.Group currentGroup = TerrorZone.findBestGroup(currentDetectedZones);
                TerrorZone.Group nextGroup = TerrorZone.findBestGroup(nextDetectedZones);

                final String finalCur = currentGroup != null ? currentGroup.name : "Unknown";
                String fNext = nextGroup != null ? nextGroup.name : "Unknown";
                
                if (fNext.equals("Unknown")) {
                    String fullTextLower = fullOcrText.toLowerCase();
                    if (fullTextLower.contains("soon") || fullTextLower.contains("disp")) fNext = "Displaying soon";
                }
                final String finalNxt = fNext;

                lastFetchTime = System.currentTimeMillis();

                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        tvCurrentTZ.setText(finalCur);
                        tvNextTZ.setText(finalNxt);
                        if (tvRawDebug != null) {
                            tvRawDebug.setText("DEBUG OCR:\n" + fullOcrText);
                        }
                        tvStatus.setText("");
                        tvLastUpdated.setText("Updated: " + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
                        progressBar.setVisibility(View.GONE);
                    });
                }
            } catch (Exception e) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        tvStatus.setText("Refresh failed. Check connection.");
                        progressBar.setVisibility(View.GONE);
                    });
                }
            }
        }).start();
    }
}
