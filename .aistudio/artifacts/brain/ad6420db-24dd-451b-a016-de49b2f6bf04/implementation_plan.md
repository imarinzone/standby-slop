# Material 3 Expressive Revamp & Tactile Haptic Integration

A complete visual and ergonomic redesign of all non-clock screens, HUD overlays, and the Studio Settings hub using native Android Jetpack Compose Material 3 (`MaterialTheme.colorScheme`, `Typography`, `Shapes`, `NavigationRail`, and tonal surfaces) with subtle tactile haptic feedback, while strictly preserving all 10 existing clock faces unchanged.

## User Review & Critical Decisions

> [!IMPORTANT]
> All 10 existing clock faces (Digital Minimal, Analog Classic, Retro Flip Card, Binary Matrix, Modern Bold, Contrasting Split, Analog Motor Dashboard, Pastel Bubbles, Ambient Horizon Gradient, and High-Tech Nixie Tube) will remain 100% untouched in both layout and rendering logic.

- **Confirmed Decision 1 — Adaptive Material 3 Theme Posture**: The app will use an Adaptive Material 3 color system following system light and dark mode (supporting Android 12+ dynamic wallpaper colors with a refined Material 3 tonal palette fallback) for all non-clock screens, overlays, dialogs, and Studio Settings.
- **Confirmed Decision 2 — Landscape Studio Settings with M3 NavigationRail**: The Studio Settings and secondary screens will use a native Material 3 `NavigationRail` paired with elevated tonal surface cards (`ElevatedCard`, `Surface`, `FilterChip`, `SegmentedButton`, M3 `Slider` and `Switch`) optimized for landscape ergonomics.
- **Confirmed Decision 3 — Subtle Tick Haptic Feedback**: Tactile feedback will focus on subtle tick and selection haptics across sliders, time pickers, pager clock-face switching, and navigation changes using Jetpack Compose `LocalHapticFeedback` and `View.performHapticFeedback`.

---

## 1. Overview & Core Concept

- **What It Does**: Transforms the Standby Desk Clock & Productivity Hub's utility pages (Alarm, Calendar, Music Player, Focus Timer/Stopwatch, Duo Split Widgets) and global chrome (HUD navigation bar, Overview directory, Screen Lock overlay, and Studio Customization Hub) into a cohesive, native Google Material 3 experience.
- **Target Audience / Persona**: Users docking their Android device in landscape mode as a smart bedside or desk display who want expressive clock faces paired with clean, modern, native Android Material 3 utility screens and tactile responsiveness.
- **Key Value**: Replaces bespoke hardcoded dark/neon borders and unstyled containers with genuine Material 3 tonal hierarchy, adaptive light/dark surfaces, accessible touch targets, and crisp haptic ticks when scrubbing sliders, picking alarm times, or switching clock faces.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Standby HUD & Clock Face Switching**: Tapping the screen reveals a floating Material 3 pill-shaped top navigation bar (`Surface` with `tonalElevation` and M3 active indicator pills), a vertical Material 3 indicator dock for the 10 clock faces, and M3 `FilledTonalIconButton` / `FloatingActionButton` controls at the bottom right. Swiping between clock faces or pages triggers a subtle haptic tick.
2. **Studio Customization Hub (M3 NavigationRail)**: Opening Settings launches a full-screen adaptive Material 3 surface featuring a left-docked `NavigationRail` for all 6 screen categories (Clock Face, Alarm, Calendar, Music Player, Timer Focus, Duo Widgets) and a two-column scrollable grid of Material 3 `ElevatedCard` and `OutlinedCard` sections on the right. Adjusting scale sliders, color swatches, or typography chips triggers crisp tick haptics.
3. **Alarm Management**: Uses a Material 3 header with an `ExtendedFloatingActionButton` ("New Alarm"), M3 tonal `ElevatedCard` alarm items with native M3 `Switch` and `IconButton` actions, and a Material 3 `AlertDialog` with tactile spinner/picker haptics and `FilterChip` snooze intervals.
4. **Calendar & Agenda Split View**: Uses a canonical Material 3 two-pane landscape layout—a high-contrast M3 `ElevatedCard` date hero on the left and an agenda pane on the right with M3 `SingleChoiceSegmentedButtonRow` (Daily / Weekly / Monthly) and tonal event cards.
5. **Media Player**: Harmonizes the Material You Expressive Player and app launcher grid with Material 3 `ElevatedCard` containers, `FilledIconButton` transport controls, and tactile seekbar scrubbing haptics while keeping the swipe-up Classic Retro theme option intact.
6. **Focus Timer & Stopwatch**: Refines the Pomodoro Focus Timer and Stopwatch with Material 3 tonal cards, M3 `FilledTonalButton` and `IconButton` controls, M3 `FilterChip` session selectors, and subtle haptic ticks on segment selection and lap splits.
7. **Duo Split-Screen & Overview Directory**: Wraps Duo widgets and the pinch-to-zoom Overview Directory in clean Material 3 `Surface` and `ElevatedCard` containers with adaptive tonal elevation and haptic selection feedback.

### Visual Identity & Theme
- **Aesthetic Direction**: Native Google Material 3 (Material You) Expressive & Adaptive—combining dynamic tonal surface elevation, generous rounded corners (`16.dp`–`28.dp`), and clean typographic hierarchy.
- **Color Palette & Mood**:
  - Dynamic color scheme (`dynamicLightColorScheme` / `dynamicDarkColorScheme` on Android 12+) with a curated Material 3 fallback palette (`primary`, `primaryContainer`, `secondaryContainer`, `surface`, `surfaceContainerLow`, `surfaceContainerHigh`, `onSurface`, `onSurfaceVariant`, `outlineVariant`).
  - Per-page user-selected accent colors remain supported and blend harmoniously with Material 3 container tokens.
- **Typography & Hierarchy**: Material 3 `Typography` scale (`displayLarge`, `headlineMedium`, `titleLarge`, `titleMedium`, `bodyMedium`, `labelLarge`) paired with user-customizable font families where applicable.
- **Interactive Feedback & Motion**:
  - Subtle haptic ticks (`HapticFeedbackConstants.CLOCK_TICK` / `SEGMENT_FREQUENT_TICK` / `TextHandleMove`) when scrolling through the 10 clock faces, dragging scale/seek sliders, incrementing alarm hour/minute pickers, and switching Studio Settings tabs.
  - Spring-based animated transitions (`spring(stiffness = Spring.StiffnessMediumLow)`) for HUD overlays, active navigation pills, and card state changes.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Strictly Isolate Clock Faces from Theme Overrides**
  - *Chosen Approach*: Keep `ClockFaces.kt`, `ClockPage.kt`, and `NixieClockView.java` completely unmodified, while applying the Adaptive Material 3 theme to all surrounding HUD chrome, dialogs, and utility screens.
  - *Why*: Directly honors the user's constraint ("Don't remove any clock face or change them") while giving the rest of the app a cohesive Material 3 overhaul.
  - *Alternatives Considered*: Forcing M3 background tokens inside clock canvases, which would alter the bespoke artistic look of the 10 clock faces.
- **Decision 2: Material 3 NavigationRail for Landscape Studio Settings**
  - *Chosen Approach*: Replace the custom sidebar column in `StandbyCustomizationDialog` with a scrollable Material 3 `NavigationRail` and `NavigationRailItem`s paired with a responsive two-column M3 card layout.
  - *Why*: Matches canonical Android Material 3 landscape guidelines and ensures all 6 screen tabs and their customization cards are immediately accessible and scannable.
  - *Alternatives Considered*: Top tabs, which consume precious vertical height in landscape orientation.
- **Decision 3: Centralized Haptic Feedback Helper**
  - *Chosen Approach*: Create a lightweight Compose haptic utility wrapping `LocalView.current.performHapticFeedback(...)` and `LocalHapticFeedback.current` with dedicated semantic methods (`performTick()`, `performStep()`, `performConfirm()`).
  - *Why*: Guarantees consistent, subtle tactile feedback across pager page changes, slider steps, and time pickers without noisy over-vibration.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                     Adaptive Material 3 Theme (Theme.kt)                     │
│       Dynamic Light/Dark ColorScheme • M3 Shapes • M3 Typography             │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                       StandbyScreen (Root Coordinator)                       │
│  ┌─────────────────────────┐  ┌──────────────────────────┐  ┌──────────────┐ │
│  │  M3 Floating Top Bar &  │  │  Haptic Pager Observer   │  │ M3 Lock &    │ │
│  │  Bottom Tonal Controls  │  │  (Clock & Page Ticks)    │  │ Overview UI  │ │
│  └─────────────────────────┘  └─────────────┬────────────┘  └──────────────┘ │
└─────────────────────────────────────────────┼────────────────────────────────┘
                                              │
        ┌──────────────┬──────────────┬───────┴──────┬──────────────┬──────────┐
        ▼              ▼              ▼              ▼              ▼          ▼
┌──────────────┐ ┌───────────┐ ┌─────────────┐ ┌───────────┐ ┌───────────┐ ┌────────┐
│  ClockPage   │ │ AlarmPage │ │CalendarPage │ │ MediaPage │ │ TimerPage │ │DuoPage │
│ (10 Faces -  │ │ M3 Cards, │ │ M3 Split    │ │ M3 Player │ │ M3 Focus  │ │M3 Dual │
│  Untouched)  │ │ M3 Switch,│ │ Cards &     │ │ Cards &   │ │ Cards &   │ │Vertical│
│              │ │ M3 Dialog │ │ Segmented   │ │ Seekbar   │ │ Stopwatch │ │Pagers  │
│              │ │ + Haptics │ │ Buttons     │ │ + Haptics │ │ + Haptics │ │+Haptics│
└──────────────┘ └───────────┘ └─────────────┘ └───────────┘ └───────────┘ └────────┘
                                              │
                                              ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                 Studio Settings Hub (StandbyCustomizationDialog)             │
│  ┌─────────────────────────┐  ┌────────────────────────────────────────────┐ │
│  │   M3 NavigationRail     │  │  ElevatedCard & OutlinedCard Settings Grid │ │
│  │  (All 6 Screen Tabs)    │  │  • Color Swatches, Font Chips, Scale       │ │
│  │  + Selection Tick       │  │    Sliders (with Haptic Ticks), M3 Switches│ │
│  └─────────────────────────┘  └────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State
- **`StandbyViewModel` & `CalendarViewModel`**: Retains all existing `StateFlow` and `SharedPreferences` / Room persistence (`AlarmDao`) for per-page colors, fonts, scales, weather toggles, text glow/outline/gradient overrides, time format (`use24HourFormat`, `showAmPm`, `showSeconds`), and calendar filter states.
- **Haptic State Observation**: `LaunchedEffect(verticalPagerState.currentPage)` and `LaunchedEffect(horizontalPagerState.currentPage)` trigger a subtle tick haptic whenever the user swipes to a new clock face or utility screen after initial composition.

### Interactive Component & State Mapping
- **Clock Face & Page Pagers**: Swiping `verticalPagerState` (10 clock faces) or `horizontalPagerState` (6 pages) or tapping indicator dots triggers `haptics.performTick()` and smoothly animates to the target page.
- **Studio Settings Hub**:
  - Selecting any of the 6 `NavigationRailItem` tabs updates `activeTab` and fires a tick haptic.
  - Dragging the Scale `Slider` fires stepped tick haptics as the scale value changes by `0.05f` increments.
  - Tapping Color Swatches, Font `FilterChip`s, Gradient chips, or toggling M3 `Switch`es updates `StandbyViewModel` state and fires a subtle tick.
- **Alarm Screen & Picker Dialog**:
  - Incrementing/decrementing Hour and Minute spinners or selecting Snooze `FilterChip`s in the M3 `AlertDialog` fires a subtle picker tick haptic.
  - Toggling an alarm `Switch` or creating/deleting an alarm updates Room persistence via `StandbyViewModel`.
- **Calendar, Media, Timer & Duo Screens**:
  - Switching Daily/Weekly/Monthly views via Material 3 `SingleChoiceSegmentedButtonRow` updates `CalendarViewModel` and fires a tick haptic.
  - Scrubbing the Media seekbar, switching Pomodoro focus segments, or swiping Duo vertical widget pagers triggers subtle haptic ticks.
