package com.d2clone.tracker;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {

    private Spinner spinnerRegion, spinnerLadder, spinnerHC, spinnerVer;
    private CheckBox[] stageCheckboxes = new CheckBox[7]; // index 1-6

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }

        spinnerRegion    = findViewById(R.id.spinnerRegion);
        spinnerLadder    = findViewById(R.id.spinnerLadder);
        spinnerHC        = findViewById(R.id.spinnerHC);
        spinnerVer       = findViewById(R.id.spinnerVer);
        Button btnSave   = findViewById(R.id.btnSave);
        TextView tvCredit = findViewById(R.id.tvCredit);

        stageCheckboxes[1] = findViewById(R.id.cbStage1);
        stageCheckboxes[2] = findViewById(R.id.cbStage2);
        stageCheckboxes[3] = findViewById(R.id.cbStage3);
        stageCheckboxes[4] = findViewById(R.id.cbStage4);
        stageCheckboxes[5] = findViewById(R.id.cbStage5);
        stageCheckboxes[6] = findViewById(R.id.cbStage6);

        // Setup spinners
        setupSpinner(spinnerRegion, new String[]{"All Regions", "Americas", "Europe", "Asia"});
        setupSpinner(spinnerLadder, new String[]{"All Modes", "Ladder", "Non-Ladder"});
        setupSpinner(spinnerHC,     new String[]{"All Types", "Hardcore", "Softcore"});
        setupSpinner(spinnerVer,    new String[]{"LoD", "RotW"});

        // Load saved prefs
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        spinnerRegion.setSelection(regionToIndex(prefs.getString("region", "all")));
        spinnerLadder.setSelection(ladderToIndex(prefs.getString("ladder", "all")));
        spinnerHC.setSelection(hcToIndex(prefs.getString("hc", "all")));
        
        String savedVer = prefs.getString("ver", "1");
        if (savedVer.equals("all")) savedVer = "1"; // Default to LoD if "both" was selected previously
        spinnerVer.setSelection(verToIndex(savedVer));

        for (int s = 1; s <= 6; s++) {
            stageCheckboxes[s].setChecked(prefs.getBoolean("notify_stage_" + s, s >= 5));
        }

        tvCredit.setText("Data courtesy of diablo2.io");

        btnSave.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("region", indexToRegion(spinnerRegion.getSelectedItemPosition()));
            editor.putString("ladder", indexToLadder(spinnerLadder.getSelectedItemPosition()));
            editor.putString("hc",     indexToHC(spinnerHC.getSelectedItemPosition()));
            editor.putString("ver",    indexToVer(spinnerVer.getSelectedItemPosition()));
            for (int s = 1; s <= 6; s++) {
                editor.putBoolean("notify_stage_" + s, stageCheckboxes[s].isChecked());
            }
            editor.apply();

            Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void setupSpinner(Spinner spinner, String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    // --- Region ---
    private int regionToIndex(String val) {
        switch (val) { case "1": return 1; case "2": return 2; case "3": return 3; }
        return 0;
    }
    private String indexToRegion(int i) {
        switch (i) { case 1: return "1"; case 2: return "2"; case 3: return "3"; }
        return "all";
    }

    // --- Ladder ---
    private int ladderToIndex(String val) {
        if ("1".equals(val)) return 1; if ("2".equals(val)) return 2; return 0;
    }
    private String indexToLadder(int i) {
        if (i == 1) return "1"; if (i == 2) return "2"; return "all";
    }

    // --- HC ---
    private int hcToIndex(String val) {
        if ("1".equals(val)) return 1; if ("2".equals(val)) return 2; return 0;
    }
    private String indexToHC(int i) {
        if (i == 1) return "1"; if (i == 2) return "2"; return "all";
    }

    // --- Version ---
    private int verToIndex(String val) {
        if ("2".equals(val)) return 1; return 0; // 0=LoD(1), 1=RotW(2)
    }
    private String indexToVer(int i) {
        if (i == 1) return "2"; return "1"; // 0=LoD(1), 1=RotW(2)
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
