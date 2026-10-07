# JKHX Player (Multi-stream)

[![Platform](https://img.shields.io/badge/Platform-Android%20Native-brightgreen.svg)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/Language-Kotlin%20%7C%20C99%20%7C%20JavaScript-blue.svg)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20%28Material%203%29-purple.svg)](https://developer.android.com/jetpack/compose)
[![Engine](https://img.shields.io/badge/JS%20Engine-QuickJS--NG-orange.svg)](https://github.com/quickjs-ng/quickjs)
[![Player](https://img.shields.io/badge/Media-AndroidX%20Media3%20ExoPlayer-red.svg)](https://developer.android.com/media/media3)

**JKHX Player (Multi-stream)** is an extensible, native Android media streaming application built with **Jetpack Compose**, **Material 3**, **Media3 / ExoPlayer**, and an embedded **QuickJS-NG** C99 JavaScript runtime.

Inspired by modular client applications like CloudStream and Tachiyomi, JKHX Player contains **zero hardcoded scrapers or proprietary content servers**. All content catalogs, search indices, metadata, and episode streaming links are dynamically resolved by modular JavaScript plugins running inside an isolated native sandbox.

---

## 🌟 Key Features

- **Native Android Architecture**: 100% Kotlin + Jetpack Compose + Navigation Compose with modern Material 3 dark aesthetics.
- **Embedded QuickJS-NG Sandbox**: Fast, modern ES2023 execution engine compiled directly via NDK CMake. No heavy WebView, no V8 footprint.
- **Strict Detail / Stream Separation**:
  - `getDetail(slug)` fetches metadata and catalog directories only (no stream URLs).
  - `getStream(episodeData)` resolves HLS/MP4 streams on-demand when the user presses play.
- **Advanced Media3 / ExoPlayer Engine**:
  - Custom `OkHttpDataSource.Factory` supporting per-stream HTTP headers (`Referer`, `Origin`, `User-Agent`).
  - Native HLS (m3u8), DASH, and MP4 playback.
  - Multi-track audio and external subtitles (`.vtt`, `.srt`).
  - Embed / WebView fallback resolver for non-direct video hosts.
- **Aggregated Home & Parallel Search**: Aggregates catalogs across all active providers; search runs concurrently with individual error isolation.
- **Local Persistence via Room**: Complete local storage for Watch History, Resume Progress, Favorites, and Plugin registries.
- **Safe Mode**: Toggle to filter and isolate 18+ adult providers.

---

## 🏗️ Architecture Overview

```
                         JKHX PLAYER
                              │
             ┌────────────────┼────────────────┐
             │                │                │
            UI          PLUGIN SYSTEM        PLAYER
             │                │                │
             │         Plugin Manager          │
             │                │                │
             │         Plugin Loader           │
             │                │                │
             │         Plugin Runtime          │
             │                │                │
             │           QuickJS-NG            │
             │                │                │
             │            plugin.js            │
             │                │                │
             │       ┌────────┼────────┐       │
             │       │        │        │       │
             │    getHome  search  getDetail   │
             │                         │       │
             │                      episodes   │
             │                         │       │
             │                    getStream()  │
             │                         │       │
             │                   EpisodeStream │
             │                         │       │
             └─────────────────────────┼───────┘
                                       │
                                    Player
                                       │
                                Media3/ExoPlayer
```

---

## 📦 Bundled Plugins (Migrated from TypeScript)

All scrapers from the legacy Next.js codebase were converted to zero-dependency modern JavaScript plugins:

| Plugin ID | Name | Source | Categories | Status |
|---|---|---|---|---|
| `mock` | Mock Provider | Built-in | Movies, TV | Active |
| `kkphim` | KKPhim | phimapi.com | Movies, TV | Active |
| `nguonc` | NguonC | phim.nguonc.com | Movies, TV | Active |
| `vsmov` | VSMov | vsmov.com | Movies, TV | Active |
| `vsnguon` | VSNguon | nguon.vsphim.com | Movies, TV | Active |
| `missav` | MissAV | missav.media | 18+ Adult, JAV | Active |
| `vlxx` | VLXX | vlxx.phd | 18+ Adult | Active |
| `xsub` | SexSub | sexsub.moe | 18+ Adult | Active |
| `st1work` | Sextop1 Land | sextop1.land | 18+ Adult | Active |
| `vnst1com` | VNSextop1 | vnsextop1.com | 18+ Adult | Active |
| `clbpx` | CLB Phim Xưa | clbphimxua.com | Movies, Vintage | Active |
| `ophim` | OPhim | ophim1.com | Movies, TV | Inactive Host (Handled) |
| `bunchatv` | BunCha TV | bunchatv4.net | TV, Live | Inactive Host (Handled) |

---

## 🚀 Quick Start

### 1. Repository Layout
- `android/`: Native Android Studio project with modular Gradle setup.
- `plugins/`: JavaScript provider plugins and master `manifest.json`.
- `test-runner/`: Automated test suite for verifying plugins in Node.js.
- `docs/`: Technical specifications, architecture docs, and development guides.

### 2. Testing Plugins
```bash
# Verify a specific plugin
node test-runner/test-plugin.js plugins/kkphim

# Verify all plugins
for p in plugins/*/; do
  [ -f "$p/manifest.json" ] && node test-runner/test-plugin.js "$p"
done
```

### 3. Building the Android App
Open `android/` in Android Studio or build via command line:
```bash
cd android
./gradlew assembleDebug
```
The resulting APK is created at `android/app/build/outputs/apk/debug/app-debug.apk`.

### 4. Deploying to Local Mint Workstation
To sync this repository to your Linux Mint path (`/media/jtmy/JDataL/J-Bot-Code/mint-project/jkhx-player`):
```bash
rsync -avz --exclude '.gradle' --exclude 'build' \
  /home/jtmy/jkhx-player/ /media/jtmy/JDataL/J-Bot-Code/mint-project/jkhx-player/
```

---

## 📖 Documentation Directory

- [Architecture Specification](docs/architecture.md)
- [Plugin API Reference](docs/plugin-api.md)
- [Plugin Development Guide](docs/plugin-development.md)
- [Scraper Migration Guide](docs/plugin-migration.md)
- [Provider Migration Audit Report](docs/provider-migration.md)
- [Security & Sandbox Model](docs/security.md)
- [Building & Deployment Guide](docs/building.md)

---

## 📄 License
This project is for educational and personal research purposes. All media content and streams are owned by their respective third-party providers.
