# D2 Clone Tracker — Android App

A Diablo II Resurrected Diablo Clone (Uber Diablo) tracker for Android that sends push notifications when the progress reaches 5/6 or 6/6.

> **Data courtesy of [diablo2.io](https://diablo2.io)**

---

## Features

- 📊 **Live DClone status** — shows all regions, ladder modes, and HC/SC types
- 🔔 **Push notifications** — alerts at 5/6 (imminent) and 6/6 (walking!)
- 🌙 **Background polling** — uses WorkManager to check every 15 minutes even when app is closed
- 🎨 **Dark Diablo-themed UI** — color-coded progress (green / orange / red)
- ⚙️ **Filters** — filter by region, ladder, and HC/SC
- 🔄 **Smart deduplication** — won't notify twice for the same event

---

## How to Build

### Requirements
- Android Studio (Hedgehog or newer)
- JDK 17
- Android SDK 34

### Steps

1. Open Android Studio
2. **File → Open** → select the `D2CloneTracker` folder
3. Wait for Gradle sync to complete
4. Click **▶ Run** (or `Shift+F10`) to install on a connected device/emulator
5. For a release APK: **Build → Build Bundle(s)/APK(s) → Build APK(s)**

---

## How It Works

### API
The app polls the public [diablo2.io DClone API](https://diablo2.io/dclone_api.php):

```
GET https://diablo2.io/dclone_api.php?region=1&ladder=2&hc=2
```

Response example:
```json
[
  {"progress":"5","region":"1","ladder":"2","hc":"2","timestamped":"1650861924"}
]
```

- `progress`: 1–6 (6 = Diablo Clone is walking!)
- `region`: 1=Americas, 2=Europe, 3=Asia
- `ladder`: 1=Ladder, 2=Non-Ladder  
- `hc`: 1=Hardcore, 2=Softcore

### Background Work
- Uses Android **WorkManager** with a 15-minute minimum interval (Android OS limit)
- Survives device reboots via `BootReceiver`
- Won't send duplicate notifications for the same event

### Notification Logic
| Progress | Notification |
|----------|-------------|
| 5/6 | ⚠️ Orange alert — "Almost time!" |
| 6/6 | 🔴 Max priority — "WALKING! Get in a Hell game!" |

---

## Project Structure

```
app/src/main/java/com/d2clone/tracker/
├── MainActivity.java       — Main screen with live data
├── SettingsActivity.java   — Filter & notification settings
├── DCloneAdapter.java      — RecyclerView adapter
├── DCloneEntry.java        — Data model
├── DCloneWorker.java       — Background WorkManager task
└── BootReceiver.java       — Restart polling after reboot
```

---

## Permissions Required

- `INTERNET` — to fetch API data
- `POST_NOTIFICATIONS` — for push notifications (Android 13+)
- `RECEIVE_BOOT_COMPLETED` — to restart background polling after reboot
- `FOREGROUND_SERVICE` — for WorkManager

---

## Fair Use Note

Per diablo2.io's API policy, this app:
- Credits **diablo2.io** prominently in the UI
- Queries no more than once per minute
- Extends the tracker's functionality (mobile push notifications) rather than duplicating it
