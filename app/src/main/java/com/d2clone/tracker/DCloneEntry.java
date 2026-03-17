package com.d2clone.tracker;

public class DCloneEntry {
    public final int progress;
    public final int region;
    public final int ladder;
    public final int hc;
    public final int ver;      // 1 = LoD, 2 = RotW
    public final long timestamp;

    public DCloneEntry(int progress, int region, int ladder, int hc, int ver, long timestamp) {
        this.progress = progress;
        this.region = region;
        this.ladder = ladder;
        this.hc = hc;
        this.ver = ver;
        this.timestamp = timestamp;
    }

    public String getRegionName() {
        switch (region) {
            case 1: return "Americas";
            case 2: return "Europe";
            case 3: return "Asia";
            default: return "Unknown";
        }
    }

    public String getLadderName() {
        return ladder == 1 ? "Ladder" : "Non-Ladder";
    }

    public String getHcName() {
        return hc == 1 ? "Hardcore" : "Softcore";
    }

    public String getVerName() {
        return ver == 2 ? "RotW" : "LoD";
    }

    public String getModeLabel() {
        return "[" + getVerName() + "] " + getRegionName() + " · " + getLadderName() + " · " + getHcName();
    }

    public boolean isAlert() {
        return progress >= 5;
    }

    public boolean isWalking() {
        return progress == 6;
    }
}
