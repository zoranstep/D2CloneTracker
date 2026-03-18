# D2 Clone & Terror Zone Tracker — Android App

A real-time Diablo II Resurrected companion app that tracks Diablo Clone (Uber Diablo) progress and current/upcoming Terror Zones.

> **Data courtesy of [diablo2.io](https://diablo2.io) and [d2tz.info](https://d2tz.info)**

---

## Features

- 📊 **Live DClone status** — monitors all regions, ladder modes, and HC/SC types.
- 🔥 **Terror Zone (TZ) Tracking** — identifies the current and next Terrorized zones using real-time image OCR.
- 🔔 **Push notifications** — 
    - Alerts for DClone at user-specified stages (1-6).
    - Immediate alerts for "Current" and "Next" Terror Zones based on your custom watchlist.
- 🌙 **Background tracking** — Uses a foreground service with exact alarms to update every 30 minutes (:00:25 and :30:25 marks).
- ⚙️ **Custom Filters** — Filter by region, ladder, and version (LoD / Resurrected).
- 🎨 **Dark Diablo-themed UI** — Visual cues and color-coding for status updates.

---

## How to Build

### Requirements
- Android Studio (Hedgehog or newer)
- JDK 17
- Android SDK 35

### Steps

1. Open Android Studio
2. **File → Open** → select the `D2CloneTracker` folder
3. Wait for Gradle sync to complete
4. Click **▶ Run** (or `Shift+F10`) to install on a connected device/emulator
5. For a release AAB/APK: **Build → Generate Signed Bundle / APK**

---

## How It Works

### DClone API
The app polls the public [diablo2.io DClone API](https://diablo2.io/dclone_api.php):
- `progress`: 1–6 (6 = Diablo Clone is walking!)
- `region`: 1=Americas, 2=Europe, 3=Asia
- `ladder`: 1=Ladder, 2=Non-Ladder  
- `hc`: 1=Hardcore, 2=Softcore

### Terror Zone Tracking (OCR)
Since there is no official API for Terror Zones, the app:
1. Fetches a real-time status image from `d2tz.info`.
2. Processes the image using **Google ML Kit (Text Recognition)**.
3. Uses **fuzzy matching** logic in `TerrorZone.java` to handle the stylized "Exocet" font.
4. Identifies zones and matches them against your "Watched Groups" in settings.

### Background Reliability
- **Exact Alarms:** Schedules tasks to run exactly 25 seconds after the hour/half-hour mark.
- **WakeLock:** Ensures the CPU stays awake during the network fetch and OCR process.
- **Foreground Service:** Keeps the task alive even during system battery optimization.

---

## Project Structure

```
app/src/main/java/com/d2clone/tracker/
├── MainActivity.java       — Tabbed layout container
├── TrackerService.java     — Core background monitoring service
├── TerrorZone.java         — OCR logic and zone definitions
├── DCloneWorker.java       — (Legacy) API polling logic
├── AlarmReceiver.java      — Wakes the app for periodic updates
└── BootReceiver.java       — Restores tracking after device reboot
```

---

## Permissions Required

- `INTERNET` — to fetch API and image data.
- `POST_NOTIFICATIONS` — for push alerts (Android 13+).
- `SCHEDULE_EXACT_ALARM` — for precise timing of zone changes.
- `FOREGROUND_SERVICE` — to run the tracker reliably.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — to prevent the app from being "killed" by the system.

---

## Fair Use & Credits

Per the respective data providers' policies:
- Credits **diablo2.io** and **d2tz.info** prominently in the UI.
- Adheres to standard polling limits (updates every 30 minutes).
- Extends the community trackers' functionality by providing mobile-native notifications.
