# JKHX Player 🎬📖

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20Material%203-blue.svg)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Playback-AndroidX%20Media3%20(ExoPlayer)-red.svg)](https://developer.android.com/media/media3)
[![QuickJS-NG](https://img.shields.io/badge/Runtime-QuickJS--NG%20NDK-orange.svg)](https://github.com/quickjs-ng/quickjs)
[![Stremio](https://img.shields.io/badge/Addon%20Protocol-Stremio%20%2F%20Nuvio%20v3-cyan.svg)](https://stremio.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](LICENSE)

🌐 **Language / Ngôn ngữ:** [Tiếng Việt](README.md) | **English**

---

**JKHX Player** is a modern, modular, open-source media player designed for the Android operating system. The application combines the high-performance playback engine of **AndroidX Media3 (ExoPlayer)**, the fluid and reactive UI of **Jetpack Compose (Material 3)**, comprehensive support for the **Stremio / Nuvio Addon v3 protocol**, and an extensible architecture powered by an isolated **QuickJS-NG NDK C++** sandbox.

---

## 🌟 Key Features

### 1. High-Performance Playback (ExoPlayer Native + Hybrid Fallback)
* **Multi-Format Streaming:** Seamless playback across standard streaming protocols: HLS (`.m3u8`), MP4, MKV, and DASH.
* **Hybrid Player Engine:** Hardware-accelerated native playback via AndroidX Media3 ExoPlayer; automatic fallback to an integrated headless/fullscreen WebView Player for protected web embedded streams.
* **Intuitive Gesture Controls:** Swipe the left edge for brightness, swipe the right edge for volume, and swipe horizontally for high-precision seeking with clean HUD indicators.
* **Picture-in-Picture (PiP):** Continue watching your favorite streams in a compact overlay window while multitasking across other apps.

### 2. Full Compatibility with Stremio & Nuvio Addon v3 Protocol
* **Open Protocol Support:** Connect any Stremio Addon easily via its remote `manifest.json` URL.
* **Content Discovery (Catalogs):** Browse movies, series, and live streaming TV channels served from the decentralized addon ecosystem.
* **Stream Aggregator:** Automatically parses and organizes streaming links from installed addons, presenting badges for resolution (4K, 1080p, 720p), audio standards (Dolby Digital, 5.1 Surround, HDR), and translation sources.
* **Addon Subtitle Integration:** Synchronize external subtitle tracks directly from addons into the native playback engine.

### 3. Isolated Sandbox Plugin Architecture (QuickJS-NG Engine)
* **Zero Bloat in Core:** The application core contains zero hardcoded vendor scrapers or proprietary third-party parsers.
* **Memory Safety & High Speed:** Addons and scraper plugins execute as standard ES Modules within an isolated QuickJS-NG C++/JNI sandbox, ensuring near-instant parsing and robust memory safety.
* **Dynamic Extensibility:** Add custom remote repositories effortlessly through JSON manifests.

### 4. Open Subtitles Ecosystem & Gemini AI Translation
* **Online Subtitle Repositories:** Direct native integration with **OpenSubtitles.com**, **SubDL.com**, and **Subsource.net**.
* **AI-Powered Translation (Google Gemini AI):** Real-time translation of foreign subtitles into accurate localized text while preserving 100% of the original timecodes.
* **Text-to-Speech (TTS Engine):** Automatic narration of subtitle tracks using on-device Google TTS or Cloud Gemini TTS, paired with **Audio Ducking** that smoothly lowers video volume during speech.
* **Customizable Typography:** Fine-tune subtitle size, text colors, font weight, background opacity, and millisecond-level time offset tuning.

### 5. Automated In-App Updater
* **Update Detection:** Directly queries the GitHub Releases API for new published versions.
* **One-Click Upgrade:** Downloads the APK package in-app with a visual progress bar and triggers the system package installer securely via `FileProvider`.

### 6. Cloud Synchronization & Vertical Comic Reader
* **Google Drive Sync:** Privately sync watch history, bookmarks, and plugin preferences across all your Android devices using personal Google Drive storage.
* **Vertical Comic Reader:** Optimized reading experience with seamless vertical scrolling, pinch-to-zoom, chapter navigation, and automatic reading progress tracking.

---

## 🏗️ System Architecture

Built following **Clean Architecture** principles and split into decoupled Gradle modules:

```
jkhx-player/
├── android/
│   ├── app/                # Jetpack Compose UI (HomeScreen, PlayerScreen, SettingsScreen...)
│   │   ├── sync/           # Google Drive cloud data synchronization
│   │   └── update/         # Automated In-App Updater via GitHub Releases API
│   ├── core/
│   │   ├── player/         # Media3 ExoPlayer management, Subtitle Parser, TTS & Audio Ducking
│   │   ├── network/        # HTTP client (OkHttp 4), Stremio/Addon v3 dispatchers, TMDB client
│   │   ├── database/       # Room SQLite Database (Watch History, Favorites, App Settings)
│   │   └── model/          # Shared domain models (DTOs, Entities, Stremio data structures)
│   └── plugin/
│       ├── api/            # Standard interface contract between Core and JavaScript Plugins
│       ├── runtime/        # QuickJS-NG C++ JNI Bridge and Headless Sniffer
│       └── manager/        # Plugin lifecycle, remote installation, and manifest manager
└── plugins/
    └── template/           # Reference open-source plugin implementation
```

---

## 📥 Download & Install (Release APK)

Pre-built APK releases can be downloaded directly from GitHub:

👉 **[Download Latest JKHX Player APK](https://github.com/joandyou/jkhx-player/releases/latest)**

*(Requires Android 8.0 Oreo - API 26 or higher)*.

---

## 🛠️ Building from Source

### Prerequisites:
* **Operating System:** Linux, macOS, or Windows (WSL2).
* **JDK:** Java Development Kit 17 (OpenJDK 17).
* **Android SDK:** Compile SDK 35, Build-tools 35.0.0.
* **Android NDK:** Version `26.1.10909125` (required for compiling the QuickJS-NG native library).
* **CMake:** Version `3.22.1` or higher.

### Build Commands:
```bash
# 1. Clone the repository
git clone https://github.com/joandyou/jkhx-player.git
cd jkhx-player/android

# 2. Assemble Release APK
JAVA_HOME=/path/to/jdk-17 ./gradlew assembleRelease

# 3. Output APK will be located at:
# app/build/outputs/apk/release/app-release.apk
```

---

## 🧩 Plugin Development

Interested in creating custom data source plugins for JKHX Player?
Explore the documentation and templates:
* [Plugin Architecture Guide](plugins/README.md)
* [Reference Plugin Implementation](plugins/template/plugin.js)
* [Plugin API Specification](docs/plugin-api.md)

---

## ⚖️ Disclaimer

* **JKHX Player** is developed solely for technology research, Android Native architecture exploration, and QuickJS sandboxing experiments.
* The codebase represents a **Clean Media Player** with no pre-bundled, hosted, or distributed copyrighted audio/video content.
* Users bear full responsibility for any plugins, Stremio addons, or content streams configured within the application.

---

## 📄 License

This project is licensed under the [Apache License 2.0](LICENSE).
