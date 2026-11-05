# Codebase Cleanup & Comprehensive Unit/Robolectric Test Suite

A targeted cleanup of unused template files, redundant configurations, unused dependencies, and dead code snippets across the Standby Desk Clock & Productivity Hub, paired with a full local JVM unit and Robolectric UI test suite verifying core business logic, persistence, and interactive Compose screens.

## User Review & Critical Decisions

> [!IMPORTANT]
> All functional features, clock faces, and custom UI themes remain 100% preserved. Only dead code with zero active call sites, unused template files, and unused library declarations will be removed.

- **Confirmed Decision 1 — Cleanup Scope**: Remove boilerplate template test files (`ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt` and the `androidTest` source tree), unused root configuration files (`firebase-applet-config.json`), unused Gradle dependencies in `app/build.gradle.kts`, and dead code snippets in `StandbyViewModel.kt`, `CalendarViewModel.kt`, and `MainActivity.kt`.
- **Confirmed Decision 2 — Test Coverage Level**: Build a full local JVM unit and Robolectric Compose UI test suite (`gradle :app:testDebugUnitTest`) capable of running fast and deterministically without an external emulator.
- **Confirmed Decision 3 — Prioritized Feature Suites**:
  1. **Alarm CRUD, Deduplication & Scrollable Dial**: Room DAO & ViewModel deduplication for identical `hour:minute` alarms, alarm toggling/deletion, and Compose UI verification of the scrollable wheel picker dial (`alarm_hour_wheel`, `alarm_minute_wheel`) and alarm dialog interactions.
  2. **Pre-Alarm Gradual Brightness Dimming**: Pure curve calculations (`computeBrightnessForDiffMillis`), active pre-alarm state transitions (`isPreAlarmDimmingActive`, `screenBrightness`), and HUD indicator (`pre_alarm_dim_chip`) visibility.
  3. **Settings Persistence & Per-Page Customization**: `SharedPreferences` round-trip persistence across ViewModel instances for per-page colors (`colorPage0..4`), fonts (`fontPage0..4`), scales, text effects (glow, outline, gradient), time format toggles, and Studio Settings dialog interactions.
  4. **Stopwatch Lap Timings & Pomodoro Timer**: Pomodoro session selection, custom duration adjustments, timer countdown controls, and Stopwatch lap recording, stopped lap summary badge (`stopwatch_lap_pill`), and Lap Timings modal dialog (`lap_timings_dialog`).

---

## 1. Overview & Cleanup Inventory

### Unwanted Files to Remove
- **`app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt` & `app/src/androidTest/`**: Template instrumented test directory (unsupported in headless CI/cloud container environments; replaced by Robolectric tests in `src/test`).
- **`app/src/test/java/com/example/ExampleUnitTest.kt`**: Boilerplate `2 + 2 == 4` template test file.
- **`firebase-applet-config.json`**: Unused web Firebase configuration stub (the app uses local Room SQLite + `SharedPreferences` persistence).

### Unused Dependencies in `app/build.gradle.kts` to Clean Up
- Comment out unused library dependencies to reduce APK size and speed up Gradle builds:
  - `implementation(platform(libs.firebase.bom))`
  - `implementation(libs.androidx.datastore.preferences)`
  - `implementation(libs.androidx.navigation.compose)`
  - `implementation(libs.logging.interceptor)`
  - `"ksp"(libs.moshi.kotlin.codegen)` (Moshi uses reflection `KotlinJsonAdapterFactory` rather than codegen annotations)
  - Unused `androidTestImplementation(...)` entries.

### Dead Code Snippets to Remove
- **`StandbyViewModel.kt`**: Remove deprecated single-page state flows (`selectedColorIndex`, `selectedFontIndex`, `showWeather`, `clockScale`) and their legacy wrapper setters (`setColorIndex`, `setFontIndex`, `setShowWeather`, `setClockScale`) that were superseded by per-page customization flows (`colorPage0..4`, `fontPage0..4`, `showWeatherPage0..4`, `scalePage0..4`) and have zero external references.
- **`CalendarViewModel.kt`**: Remove the unused `companion object { private fun getRelativeTimeOffset(...) }` leftover helper and unused local variable `val now = Calendar.getInstance()` inside `calculateCategory`.
- **`MainActivity.kt`**: Remove unused imports (`WindowCompat`, `WindowInsetsCompat`, `WindowInsetsControllerCompat`, `PowerConnectionReceiver`), duplicate uncaught exception handler registration (already initialized in `StandbyApplication.kt`), and no-op lifecycle log overrides (`onStart`, `onStop`, `onDestroy`).

---

## 2. Test Suite Architecture & Coverage Plan

```
app/src/test/java/com/example/
├── AlarmAndBrightnessTest.kt       # Alarm DAO deduplication, CRUD, Pre-Alarm dimming curve & HUD
├── SettingsPersistenceTest.kt      # SharedPreferences persistence & per-page ViewModel states
└── TimerAndStopwatchUiTest.kt      # Pomodoro timer controls, Stopwatch laps, pill & modal dialog
```

### Suite 1: `AlarmAndBrightnessTest.kt`
- **Alarm Deduplication & CRUD (Room + ViewModel)**:
  - Adding an alarm at `07:30` inserts a record into Room; adding another alarm at `07:30` with a new label/snooze updates the existing `07:30` alarm in-place rather than creating a duplicate entry.
  - Toggling `toggleAlarm(alarm)` flips `isEnabled` and recalculates `updateNextAlarm`.
  - Calling `deleteAlarm(alarm)` removes the entry and clears upcoming alarm state when no enabled alarms remain.
- **Pre-Alarm Gradual Brightness Dimming**:
  - Verifies `computeBrightnessForDiffMillis` across all boundary conditions: negative diff (`1.0f`), outside 30-minute window (`1.0f`), start of 30-minute window (`1.0f`), midpoint 15 minutes before alarm (`0.575f`), and imminent alarm `0ms` (`0.15f`).
  - Verifies `updateNextAlarm` sets `isPreAlarmDimmingActive = true` and lowers `screenBrightness` when an enabled alarm is within 30 minutes, and resets to `1.0f` when disabled.
- **Alarm Page UI & Scrollable Wheel Dial (Robolectric Compose)**:
  - Renders `AlarmPage`, opens the New Alarm dialog via `add_alarm_fab`, verifies the scrollable hour/minute wheels (`alarm_hour_wheel`, `alarm_minute_wheel`), snooze chips, and confirms saving an alarm adds a single card to the list.

### Suite 2: `SettingsPersistenceTest.kt`
- **Per-Page Customization & Round-Trip Persistence**:
  - Tests `setPageColor`, `setPageFont`, `setPageScale`, `setPageWeather`, `setTextGlow`, `setTextOutline`, `setTextGradient`, `setUse24HourFormat`, `setShowAmPm`, `setShowSeconds`, `setPomodoroFocusMinutes`, and `setTimerModeIndex`.
  - Instantiates a second `StandbyViewModel` against the same `Application` context to verify all customized values are accurately restored from `SharedPreferences`.
- **Full-Screen Launch & Pre-Alarm Dimming Indicator UI**:
  - Verifies `StandbyScreen` launches cleanly without any intrusive "full screen" toast/banner and displays the `pre_alarm_dim_chip` badge when an alarm is scheduled within the 30-minute dimming window.

### Suite 3: `TimerAndStopwatchUiTest.kt`
- **Pomodoro Focus Timer UI & Custom Duration**:
  - Renders `TimerPage` in Focus Timer mode, switches between Focus (`25m`), Short Break (`5m`), and Long Break (`15m`), adjusts focus duration via `+5m` / `-5m` buttons (`focus_plus_5`, `focus_minus_5`), and toggles start/pause and reset.
- **Stopwatch Lap Recording, Stopped Lap Pill & Modal Dialog**:
  - Switches `TimerPage` to Stopwatch mode (`stopwatch_mode_tab`), starts the stopwatch (`stopwatch_start_pause_btn`), records multiple laps (`stopwatch_lap_btn`), and stops the stopwatch.
  - Verifies that once stopped with recorded laps, the interactive `stopwatch_lap_pill` ("VIEW LAPS") appears, and clicking it opens the `lap_timings_dialog` displaying individual lap splits and cumulative times.
