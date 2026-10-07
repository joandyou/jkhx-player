# JKHX Player Architecture Specification

## 1. System Overview

**JKHX Player (Multi-stream)** is an extensible, high-performance Android native media application built with Jetpack Compose, Media3/ExoPlayer, and an embedded QuickJS-NG JavaScript engine. It operates on a **zero-built-in scraper architecture**: the core application host is completely decoupled from content scrapers and providers. All movie/TV data, catalog feeds, search queries, episode listings, and streaming links are provided through dynamically loaded JavaScript plugins.

```
                         ┌─────────────────────────────────────────┐
                         │               JKHX PLAYER               │
                         │             Android Native              │
                         │            Kotlin + Compose             │
                         └────────────────────┬────────────────────┘
                                              │
                     ┌────────────────────────┼────────────────────────┐
                     │                        │                        │
                     ▼                        ▼                        ▼
              ┌─────────────┐          ┌──────────────┐         ┌──────────────┐
              │  UI Layer   │          │Plugin System │         │ Player Layer │
              │             │          │              │         │              │
              │ Home        │          │ PluginManager│         │ Media3       │
              │ Search      │          │ Loader       │         │ ExoPlayer    │
              │ Detail      │          │ QuickJS-NG   │         │ HLS / M3U8   │
              │ Settings    │          │ Host Bridge  │         │ Subtitles    │
              └──────┬──────┘          └──────┬───────┘         └──────┬───────┘
                     │                        │                        │
                     ▼                        ▼                        │
              ┌─────────────┐          ┌──────────────┐                │
              │ Core Data   │          │  plugin.js   │                │
              │             │          │              │                │
              │ Room DB     │          │ Provider     │                │
              │ History     │          │ Logic        │                │
              │ Favorites   │          │              │                │
              └─────────────┘          └──────┬───────┘                │
                                              │                        │
                                              ▼                        │
                                      ┌───────────────┐                │
                                      │ EpisodeStream │────────────────┘
                                      │ url + headers │
                                      └───────────────┘
```

---

## 2. Core Architectural Separation: `getDetail()` vs `getStream()`

A foundational architectural rule in JKHX Player is the strict separation between detail metadata fetching and episode stream resolution:

```
               User navigates to Movie
                         │
                         ▼
             plugin.getDetail(movieSlug)
                         │
                         ▼
                   MovieDetail
                   ├── title, poster, desc, genres...
                   └── servers: List<EpisodeServer>
                            └── episodes: List<EpisodeData>
                                     ├── id
                                     ├── name
                                     ├── data  <─── Opaque payload (NO playable stream)
                                     └── slug
                         │
                         │ User selects episode to watch
                         ▼
            plugin.getStream(episodeData)
                         │
                         ▼
                   EpisodeStream
                   ├── type: "hls" | "mp4" | "embed"
                   ├── url: "https://.../index.m3u8"
                   ├── headers: { "Referer": "...", "User-Agent": "..." }
                   └── subtitles: [ ... ]
                         │
                         ▼
              ExoPlayer with Custom OkHttpDataSource
```

### Why this separation is critical:
1. **Performance & Bandwidth**: Scraping/resolving 100+ episode video links during detail page load causes massive network latency, high mobile battery drain, and frequent IP bans from target hosting servers.
2. **Stream Expiration**: Video URLs frequently contain short-lived cryptographic tokens or timestamps (TTL often < 30 minutes). Resolving links in `getDetail()` would result in stale, 403 Forbidden playback errors when a user pauses or watches later.
3. **Decoupled Error Isolation**: If an individual episode link resolution fails, the movie details and full episode directory remain visible and accessible.

---

## 3. Modular System Architecture

The Android application is organized into decoupled Gradle subprojects:

```
android/
├── app/                  # Application host, UI screens (Compose), Navigation, Themes
├── core/
│   ├── model/            # Pure Kotlin domain data models, zero Android framework dependencies
│   ├── network/          # OkHttp bridge, interceptors, user-agent spoofing
│   ├── database/         # Room persistence (History, Favorites, Progress, Plugins)
│   └── player/           # Media3 / ExoPlayer wrapper, subtitle handling, embed fallback
└── plugin/
    ├── api/              # Standard interfaces for Plugin, PluginHost, NativeBridge
    ├── runtime/          # QuickJS-NG C99 engine, JNI bindings, JS sandbox execution
    └── manager/          # Plugin lifecycle, file store, repository downloader, multi-provider queries
```

### Architectural Role of Each Module:
- **`:core:model`**: Pure Kotlin models. Completely portable, shared across modules, and designed for easy re-use in a future Kotlin Multiplatform / iOS client.
- **`:core:network`**: Provides centralized network facilities. Handles standard HTTP calls and native request routing for JavaScript `fetch()` calls.
- **`:core:database`**: Persists local app state using Room:
  - `HistoryEntity`: Watched titles with timestamps.
  - `PlaybackProgressEntity`: Position, duration, episode ID, and server ID for resume playback.
  - `FavoriteEntity`: Bookmarked movies with plugin ID and metadata.
  - `PluginEntity`: Installed plugins, enabled/disabled status, and versioning.
- **`:core:player`**: Wraps Media3 `ExoPlayer`. Instantiates `OkHttpDataSource.Factory` configured with per-stream HTTP headers (`Referer`, `Origin`, `User-Agent`), sets up WebVTT/SRT subtitles, and manages aspect ratio/pip/controls.
- **`:plugin:runtime`**: Interfaces with the QuickJS-NG shared library via JNI. Enforces execution memory quotas (64 MB), execution timeouts (15 seconds via interrupt handlers), and promise job loop draining.
- **`:plugin:manager`**: Installs, activates, and discovers plugins from local storage (`filesDir/plugins/`) and remote repositories. Coordinates parallel multi-provider search with graceful failure isolation.
- **`:app`**: Pure Jetpack Compose UI. Uses Single-Activity architecture (`MainActivity`) with Navigation Compose.

---

## 4. JavaScript Runtime: QuickJS-NG

JKHX Player intentionally uses **QuickJS-NG** (Next Generation) rather than WebView, Duktape, or Google V8:

- **QuickJS-NG vs WebView**:
  - Zero UI overhead; no Chromium IPC or memory bloat (~10 MB vs ~150 MB).
  - Can run on background threads without requiring `Looper.getMainLooper()`.
- **QuickJS-NG vs Duktape**:
  - Full ES2023 / modern JavaScript support (Promises, `async`/`await`, Optional Chaining `?.`, Nullish Coalescing `??`, Arrow Functions, `Set`/`Map`).
  - Far superior JIT-less bytecode execution speed.
- **QuickJS-NG vs V8 / JavaScriptCore**:
  - C99 lightweight codebase, compiles in seconds.
  - Minimal shared library footprint (< 2 MB `.so`).
  - Native support across Linux, Android NDK, macOS, and iOS (seamless cross-platform target).

---

## 5. Native Bridge & JavaScript Environment

Every plugin script executes inside an isolated `JSContext`. Before running, the host injects standard Web APIs:

1. **`globalThis.fetch(url, options)`**:
   - Calls into native Kotlin via JNI `dispatchNativeBridge("fetch", jsonArgs)`.
   - Executes asynchronously using OkHttp on a dedicated I/O coroutine dispatcher.
   - Transparently handles custom headers, cookies, redirects, and response bodies.
2. **`globalThis.context.storage`**:
   - Key-value plugin storage scoped per plugin ID.
   - Prevents plugins from modifying or reading data from other plugins.
3. **`globalThis.context.log`**:
   - Outputs log messages to Android Logcat under `JKHX-Plugin-<pluginId>`.
4. **Lightweight HTML Parser (`plugins/common/cheerio.js`)**:
   - Zero-dependency DOM regex parser providing Cheerio-compatible selectors (`$(selector).find().attr().text()`).
   - Bundled directly inside QuickJS contexts for DOM scraper plugins.
