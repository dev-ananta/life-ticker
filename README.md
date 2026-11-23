# Life Tracker — Stock Market Life Tracker (Android)

Track the things in your life like stocks. Create a stock (e.g. **NINA**), put it in a list
(e.g. **Romance**), log how it's going each day from **−10 to +10**, and watch the chart build.

## Features
- **Stocks**: ticker + optional full name; edit or delete any time.
- **Lists**: Romance, Career, Health… A stock can live in several lists. Manage lists from the
  list icon (top right) or from a stock's edit dialog.
- **Daily entries**: −10…+10 slider, date & time pickers (backfill past days), optional note.
  Log several times a day if you want intraday "fluctuations". Tap any history row to edit/delete.
- **Charts**: 1D, 5D, 1M, 6M, 1Y, 5Y, YTD. Drag across the chart to scrub — the header shows the
  value, date and note at that point. Open/High/Low/Average stats per range.
- **Design**: dark mode, **green for up-trends, red for down-trends** (the chart, range tabs and the
  value badges all follow the trend). The home screen shows a sparkline per stock and a
  "Life Index" (average of all stocks).
- Like a real ticker, each range starts from the last value *before* the range ("previous close"),
  so **1D** shows how today moved versus yesterday.
- Data is stored privately on the phone in one JSON file (`files/lifeticker.json`).
- "Try it with sample data" on the empty screen creates NINA/WORK/GYM with made-up history so you
  can see the charts immediately (delete them whenever).

## Build & install
1. Install **Android Studio** (any recent version).
2. *File → Open* → choose this `LifeTicker` folder. Let Gradle sync (it downloads dependencies).
   If Studio offers to upgrade the Android Gradle Plugin, accepting is fine.
3. Plug in your phone (USB debugging on) and press **Run ▶**.
   Or build an APK: *Build → Build App Bundle(s)/APK(s) → Build APK(s)*, or `./gradlew assembleDebug`.

Requires Android 8.0+ (API 26). Kotlin + Jetpack Compose, no third-party libraries.

## Project layout
```
app/src/main/java/com/lifeticker/
  MainActivity.kt          navigation between Home / Stock / Lists
  TrackerViewModel.kt      all create/edit/delete actions, auto-saves
  data/Models.kt           Stock, StockList, Entry, time ranges, chart-series math
  data/Repository.kt       JSON persistence
  ui/HomeScreen.kt         lists, sparklines, Life Index
  ui/StockDetailScreen.kt  big chart, range tabs, stats, history
  ui/Chart.kt              custom Canvas chart + sparkline
  ui/Dialogs.kt            new/edit stock, log entry, list dialogs
  ui/Theme.kt              dark theme + green/red palette
```
