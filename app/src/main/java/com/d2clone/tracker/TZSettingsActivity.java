package com.d2clone.tracker;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.HashSet;
import java.util.Set;

public class TZSettingsActivity extends AppCompatActivity {

    private LinearLayout zoneListContainer;
    private CheckBox cbAlertCurrent, cbAlertNext;
    private final CheckBox[] groupCheckboxes = new CheckBox[TerrorZone.GROUPS.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tz_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Terror Zone Alerts");
        }

        cbAlertCurrent = findViewById(R.id.cbAlertCurrent);
        cbAlertNext = findViewById(R.id.cbAlertNext);
        zoneListContainer = findViewById(R.id.zoneListContainer);
        EditText etSearch = findViewById(R.id.etSearch);
        Button btnSelectAll = findViewById(R.id.btnSelectAll);
        Button btnClearAll = findViewById(R.id.btnClearAll);
        Button btnSave = findViewById(R.id.btnSave);

        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        cbAlertCurrent.setChecked(prefs.getBoolean("tz_alert_current", true));
        cbAlertNext.setChecked(prefs.getBoolean("tz_alert_next", true));
        Set<String> watchedGroups = prefs.getStringSet("tz_watched_groups", new HashSet<>());

        // Build group checkboxes dynamically
        for (int i = 0; i < TerrorZone.GROUPS.length; i++) {
            TerrorZone.Group group = TerrorZone.GROUPS[i];
            CheckBox cb = new CheckBox(this);
            cb.setText(group.name);
            cb.setTextColor(0xFFCCCCEE);
            cb.setTextSize(13f);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF9B2335));
            cb.setPadding(8, 4, 8, 4);
            cb.setChecked(watchedGroups.contains(group.name));
            zoneListContainer.addView(cb);
            groupCheckboxes[i] = cb;
        }

        // Search filter
        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                String q = s.toString().toLowerCase();
                for (int i = 0; i < TerrorZone.GROUPS.length; i++) {
                    boolean visible = q.isEmpty() || TerrorZone.GROUPS[i].name.toLowerCase().contains(q);
                    groupCheckboxes[i].setVisibility(visible ? View.VISIBLE : View.GONE);
                }
            }
            public void afterTextChanged(Editable s) {}
        });

        btnSelectAll.setOnClickListener(v -> {
            for (CheckBox cb : groupCheckboxes)
                if (cb.getVisibility() == View.VISIBLE) cb.setChecked(true);
        });
        btnClearAll.setOnClickListener(v -> {
            for (CheckBox cb : groupCheckboxes)
                if (cb.getVisibility() == View.VISIBLE) cb.setChecked(false);
        });

        btnSave.setOnClickListener(v -> {
            Set<String> selectedGroups = new HashSet<>();
            for (int i = 0; i < TerrorZone.GROUPS.length; i++) {
                if (groupCheckboxes[i].isChecked()) selectedGroups.add(TerrorZone.GROUPS[i].name);
            }
            prefs.edit()
                    .putBoolean("tz_alert_current", cbAlertCurrent.isChecked())
                    .putBoolean("tz_alert_next", cbAlertNext.isChecked())
                    .putStringSet("tz_watched_groups", selectedGroups)
                    .apply();
            Toast.makeText(this, "Saved! Watching " + selectedGroups.size() + " groups.", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
