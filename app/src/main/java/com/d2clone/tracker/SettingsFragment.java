package com.d2clone.tracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {

    private Spinner spinnerRegion, spinnerLadder, spinnerHC, spinnerVer;
    private CheckBox[] stageCheckboxes = new CheckBox[7]; // index 1-6

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        spinnerRegion    = view.findViewById(R.id.spinnerRegion);
        spinnerLadder    = view.findViewById(R.id.spinnerLadder);
        spinnerHC        = view.findViewById(R.id.spinnerHC);
        spinnerVer       = view.findViewById(R.id.spinnerVer);
        Button btnSave   = view.findViewById(R.id.btnSave);
        TextView tvCredit = view.findViewById(R.id.tvCredit);

        stageCheckboxes[1] = view.findViewById(R.id.cbStage1);
        stageCheckboxes[2] = view.findViewById(R.id.cbStage2);
        stageCheckboxes[3] = view.findViewById(R.id.cbStage3);
        stageCheckboxes[4] = view.findViewById(R.id.cbStage4);
        stageCheckboxes[5] = view.findViewById(R.id.cbStage5);
        stageCheckboxes[6] = view.findViewById(R.id.cbStage6);

        // Setup spinners
        setupSpinner(spinnerRegion, new String[]{"All Regions", "Americas", "Europe", "Asia"});
        setupSpinner(spinnerLadder, new String[]{"All Modes", "Ladder", "Non-Ladder"});
        setupSpinner(spinnerHC,     new String[]{"All Types", "Hardcore", "Softcore"});
        setupSpinner(spinnerVer,    new String[]{"Both (LoD + RotW)", "LoD only", "RotW only"});

        // Load saved prefs
        SharedPreferences prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
        spinnerRegion.setSelection(regionToIndex(prefs.getString("region", "all")));
        spinnerLadder.setSelection(ladderToIndex(prefs.getString("ladder", "all")));
        spinnerHC.setSelection(hcToIndex(prefs.getString("hc", "all")));
        spinnerVer.setSelection(verToIndex(prefs.getString("ver", "all")));

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

            Toast.makeText(getContext(), "Settings saved!", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupSpinner(Spinner spinner, String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private int regionToIndex(String val) {
        switch (val) { case "1": return 1; case "2": return 2; case "3": return 3; }
        return 0;
    }
    private String indexToRegion(int i) {
        switch (i) { case 1: return "1"; case 2: return "2"; case 3: return "3"; }
        return "all";
    }

    private int ladderToIndex(String val) {
        if ("1".equals(val)) return 1; if ("2".equals(val)) return 2; return 0;
    }
    private String indexToLadder(int i) {
        if (i == 1) return "1"; if (i == 2) return "2"; return "all";
    }

    private int hcToIndex(String val) {
        if ("1".equals(val)) return 1; if ("2".equals(val)) return 2; return 0;
    }
    private String indexToHC(int i) {
        if (i == 1) return "1"; if (i == 2) return "2"; return "all";
    }

    private int verToIndex(String val) {
        if ("1".equals(val)) return 1; if ("2".equals(val)) return 2; return 0;
    }
    private String indexToVer(int i) {
        if (i == 1) return "1"; if (i == 2) return "2"; return "all";
    }
}
