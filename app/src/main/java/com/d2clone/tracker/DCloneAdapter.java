package com.d2clone.tracker;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DCloneAdapter extends RecyclerView.Adapter<DCloneAdapter.ViewHolder> {

    private List<DCloneEntry> data = new ArrayList<>();

    public void setData(List<DCloneEntry> newData) {
        this.data = newData;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dclone, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DCloneEntry entry = data.get(position);
        holder.bind(entry);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMode, tvProgress, tvTimestamp, tvProgressBar;
        View cardBg;

        ViewHolder(View v) {
            super(v);
            tvMode = v.findViewById(R.id.tvMode);
            tvProgress = v.findViewById(R.id.tvProgress);
            tvTimestamp = v.findViewById(R.id.tvTimestamp);
            tvProgressBar = v.findViewById(R.id.tvProgressBar);
            cardBg = v.findViewById(R.id.cardBg);
        }

        void bind(DCloneEntry entry) {
            tvMode.setText(entry.getModeLabel());

            // Color coding
            if (entry.isWalking()) {
                tvProgress.setTextColor(Color.parseColor("#FF4444")); // Red - walking!
                cardBg.setBackgroundColor(Color.parseColor("#3D1010"));
                tvProgress.setText("⚠ " + entry.progress + "/6 - WALKING!");
            } else if (entry.isAlert()) {
                tvProgress.setTextColor(Color.parseColor("#FFAA00")); // Orange - alert
                cardBg.setBackgroundColor(Color.parseColor("#3D2E00"));
                tvProgress.setText("! " + entry.progress + "/6");
            } else {
                tvProgress.setTextColor(Color.parseColor("#4CAF50")); // Green - safe
                cardBg.setBackgroundColor(Color.parseColor("#1A1A2E"));
                tvProgress.setText(entry.progress + "/6");
            }

            // Progress bar visual using unicode blocks
            StringBuilder bar = new StringBuilder("[");
            for (int i = 1; i <= 6; i++) {
                bar.append(i <= entry.progress ? "█" : "░");
            }
            bar.append("]");
            tvProgressBar.setText(bar.toString());

            // Timestamp
            long ms = entry.timestamp * 1000L;
            String time = new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                    .format(new Date(ms));
            tvTimestamp.setText("Last report: " + time);
        }
    }
}
