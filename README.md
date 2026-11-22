# Mah Reader 📖

**Mah Reader** is a high-performance, full-screen **Light Novel & PDF True Dark Mode Reader** built for Android with **Kotlin** and **Jetpack Compose**. It features an intelligent selective color preservation engine that transforms light pages and dark text into eye-comfort dark modes while keeping all **color illustrations, anime character art, inserts, and covers in their natural, untouched colors**.

---

## ✨ Features

- **True Dark Mode Without Inverted Images**:
  - Unlike naive PDF inverters that turn photos into creepy negative x-rays, Mah Reader's selective pixel engine isolates text from chromatic elements.
  - White page backgrounds turn pitch black.
  - Black text turns crisp white.
  - **All illustrations, character sketches, and color spreads remain 100% in their natural, vivid colors.**

- **Full-Screen Immersive Reading**:
  - Completely hides system status bars and navigation bars (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`).
  - Distraction-free: The novel fills the entire screen edge-to-edge.
  - 1-tap anywhere on the screen toggles the reading HUD (page scrubber, chapter jump, reading theme).

- **Eye Strain Mode ("Warm Amber Candle-Light")**:
  - **AMOLED Dark:** Pure pitch black (`#000000`) background + crisp white (`#FFFFFF`) text for maximum battery savings and contrast.
  - **Eye Strain Amber:** Deep warm midnight (`#0D0A07`) background + soft warm golden amber (`#E8C58C`) text. Completely eliminates blue light frequencies to protect your eyes when reading in bed.
  - **Soft Charcoal:** Muted dark gray (`#161619`) background + soft ivory (`#D6D6DC`) text.
  - **Original Light:** Unaltered page rendering for reading outdoors in sunlight.

- **Reading Convenience for Light Novels**:
  - **Scroll Mode (Webtoon-style):** Seamless vertical continuous scrolling.
  - **Flip Mode (E-Reader style):** Horizontal page-by-page swipe/tap.
  - **Auto-Bookmark Progress:** Automatically tracks your exact page number and percentage for every opened novel.
  - **Bookshelf Library:** Fast access to recently opened PDFs with progress bars and instant resume.

---

## 🛠️ Tech Stack

- **Language**: Kotlin 2.0
- **UI Framework**: Jetpack Compose (Material 3)
- **PDF Engine**: Android Hardware-Accelerated `PdfRenderer` + Custom Selective Chroma Filter
- **Database**: Room Database (SQLite) for Bookshelf and Reading State
- **Minimum SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 15 (API 35)

---

## 🚀 Building & Installing

1. Clone or open the project in **Android Studio Ladybug** (or later).
2. Build the release APK via terminal:
   ```bash
   ./gradlew assembleRelease
   ```
3. Or build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📄 License

Part of the **TW1ST** mobile suite.
