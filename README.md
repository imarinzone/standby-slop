# 🌌 Standby: Landscape Ambient Smart Display

Welcome to **Standby**, a highly aesthetic, premium, and fully-customizable landscape ambient smart display application for Android. Inspired by modern smart-clocks and elegant dock systems, Standby transforms your Android device into a gorgeous bedside or desk-hub display when placed in landscape mode.

This repository is now fully open-source! We welcome developers, designers, and contributors to help build the ultimate ambient screen experience.

---

## ✨ Features & Display Pages

Standby features beautiful, screen-optimized pages designed for peak visibility from across the room.

### 1. 🕒 Interactive Clock Faces
Indulge in 11 magnificent, fluid clock renderers featuring rich Material 3 themes:
*   **Neon Digital:** Radiant retro-futuristic glowing digits.
*   **Retro Flip:** Classic tactile split-flap calendar clock.
*   **Matrix Binary:** Sci-fi binary code style with cascade rain aesthetics.
*   **Diagonal Split:** Bold contrast duotone split-color faces.
*   **Bubble Pastel:** Playfully rounded pastel digits with interactive organic bounds.
*   **Ambient Gradient:** Slow-evolving color-field gradients perfect for low-light environments.
*   **Large Custom Sidebar:** Integrated sidebar containing current date, status metrics, and pending alarm indicators.
*   **Analog Dashboard:** Traditional sweep-tick chronometers renderered on customized Compose vectors.

### 2. ⏰ Dynamic Alarm Panel
*   Synchronize, configure, and monitor ongoing system alarms with simplified toggle interfaces.
*   Sleek grid schedules optimized for landscape orientations.

### 3. 📅 Interactive Calendar Grid
*   Two-way sync concepts matching your current system date structure.
*   Accentuated highlighted markers showing active events.

### 4. 🎵 Music Controller & Playback Monitor
*   Real-time monitoring of media playback activity across Spotify, YouTube, and other Android media controllers.
*   Interactive media control (play, pause, skip, backward) with fluid ripple interactions.

### 5. ⏱️ Focus Timer & Stopwatch Hub (New Swipable UI!)
*   **Seamless Draggable Gestures:** Switch seamlessly between **Pomodoro Focus Timer** and the **High-Tech Stopwatch** by simple vertical swiping (swapping up/down) instead of distracting tabs.
*   **Focus Ring Tracker:** High-contrast radial progress tracker built using customizable color brushes.
*   **High Precision Stopwatch:** Millisecond precision metrics framed on crisp, readable typography.

### 6. 🎛️ Duo Widgets Screen
*   Split-screen dashboard displaying two compact widgets of your choice side-by-side (e.g., dual Clock + Alarm, Calendar + Music, Focus + Calendar).
*   Independent vertical swiping selectors on each side to create your perfect productivity duo.

---

## 🎨 Studio Hub Customization Dialog (Material Express UI)

We have completely redesigned the customization menu into a stunning, responsive, fullscreen **Studio Hub Preferences** panel utilizing custom Material Expressive layouts:
*   **Dynamic Visual Sidebar:** Select individual tabs (`Clock Face`, `Alarm System`, `Calendar Grid`, `Music Controller`, etc.) on a sleek, high-contrast left sidebar showing live previews of their active themes.
*   **Typography & Accents Deck:** Real-time color wheel swaps and font-family selections that propagate instantly across the entire application.
*   **Ambient Control Toggles:** Enable or disable distraction-free settings (e.g., hiding sweeping seconds trackers or slow animations to minimize distraction in dark bedrooms).
*   **Adaptive Layout Support:** Smooth scaling slider options to enlarge or compress clock scales for any tablet, desktop, or mobile landscape layout.

---

## 🛠️ Technology Stack

Standby is engineered using modern, industry-standard Android architectures:
*   **Language:** Kotlin 100%
*   **UI Framework:** Jetpack Compose (Material Design 3 Expressive UI Specifications)
*   **State Architecture:** MVVM (Model-View-ViewModel) backed by unidirectional Kotlin StateFlows
*   **Asynchronous Processing:** Kotlin Coroutines & Flow
*   **Layout Adaptability:** Built-in edge-to-edge support (`enableEdgeToEdge`), deep-inset padding calculations, and full-screen landscape response grids.

---

## 🚀 Getting Started & Build Instructions

### Prerequisites
*   Android Studio (Ladybug or newer)
*   JDK 17 or higher
*   Android device running Android 8.0+ (API 26) with landscape dynamic lock capabilities

### Commands to Run the Project Locally
Clone the repository:
```bash
git clone https://github.com/yourusername/standby-ambient.git
cd standby-ambient
```

To run build tasks and compile:
```bash
gradle assembleDebug
```

To run standard unit tests & visual integration snapshots:
```bash
gradle :app:testDebugUnitTest
```

---

## 🤝 Contributing

We welcome open-source contributions! Whether you want to design a new clock face, improve the media controls, or fix a layout issue, follow these steps:

1.  **Fork** the repository.
2.  **Create** a feature branch (`git checkout -b feature/amazing-clock`).
3.  **Implement** your changes following our [Kotlin Code Style Guidelines]().
4.  **Add/Verify** tests for any new interactive modules.
5.  **Commit** your changes with clear descriptions (`git commit -m 'Added gorgeous Neon Star clock face'`).
6.  **Push** to your fork and open a **Pull Request**.

### Designing New Clock Faces
To add a custom clock face layout to the rendering pipeline:
1.  Open `/app/src/main/java/com/example/ui/standby/ClockFaces.kt`
2.  Design a new `@Composable` function utilizing proper `scale` modifiers.
3.  Register your design inside the `ClockFaceRenderer` switch-deck!

---

## 📄 License

This project is licensed under the Apache License 2.0. Feel free to use, modify, and distribute it in your personal and commercial projects. Ambient design belongs to everyone!

---

*Made with 🌌 by the Standby Open Source community.*
