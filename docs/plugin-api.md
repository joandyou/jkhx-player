# JKHX Player Plugin API Specification

## 1. Overview

Each JKHX Player plugin is a self-contained ES Module packaged inside its own directory:

```
plugins/<plugin-id>/
├── manifest.json
└── plugin.js
```

The plugin must export a default object complying with the `MovieProvider` contract.

---

## 2. Plugin Manifest (`manifest.json`)

The manifest defines plugin identity, capabilities, and repository discovery metadata.

### Schema:
```json
{
  "id": "provider-id",
  "name": "Provider Name",
  "version": "1.0.0",
  "description": "Provider description",
  "icon": "https://example.com/icon.png",
  "entry": "plugin.js",
  "author": "Author Name",
  "homepage": "https://example.com",
  "types": ["movies", "tv", "anime"],
  "isAdult": false,
  "status": "active"
}
```

### Fields:
| Field | Type | Required | Description |
|---|---|---|---|
| `id` | `string` | Yes | Unique alphanumeric identifier (lowercase, hyphens permitted). |
| `name` | `string` | Yes | Human-readable provider title displayed in UI. |
| `version` | `string` | Yes | Semantic version string (e.g., `1.0.0`). |
| `description` | `string` | No | Short summary of content catalog. |
| `icon` / `iconUrl` / `logo` | `string` | No | URL ảnh biểu tượng đại diện của plugin (hiển thị trên thẻ plugin và kho nguồn). |
| `defaultDomain` | `string` | No | Tên miền mặc định của nguồn dữ liệu (dùng hiển thị và kiểm tra kết nối máy chủ). |
| `entry` | `string` | Yes | Path to JavaScript entry point, typically `plugin.js`. |
| `types` | `string[]` | Yes | Supported content categories (`movies`, `tv`, `anime`, etc.). |
| `isAdult` | `boolean` | Yes | `true` if provider serves 18+ adult content (hidden if Safe Mode is on). |
| `status` | `string` | Yes | `"active"` or `"broken"`. |

---

## 3. JavaScript Provider Interface (`plugin.js`)

A plugin script must export a default JavaScript object implementing the following asynchronous methods:

```javascript
export default {
  /**
   * Fetch home page catalog sections (Latest, Trending, Popular, etc.)
   * @param {number} [page=1] - Requested page number
   * @returns {Promise<HomeSection[]>}
   */
  async getHome(page = 1) { ... },

  /**
   * Search catalog for matching titles
   * @param {string} query - Search term
   * @param {number} [page=1] - Requested page number
   * @returns {Promise<SearchResult>}
   */
  async search(query, page = 1) { ... },

  /**
   * Retrieve movie details and episode lists.
   * NOTE: DO NOT resolve playable stream URLs here!
   * @param {string} slug - Movie slug or identifier
   * @returns {Promise<MovieDetail>}
   */
  async getDetail(slug) { ... },

  /**
   * Resolve playable stream for a specific episode.
   * @param {EpisodeData} episodeData - The episode selected by the user
   * @returns {Promise<EpisodeStream>}
   */
  async getStream(episodeData) { ... }
};
```

---

## 4. Data Models & Type Definitions

### 4.1. `MovieItem`
Represents a movie card displayed in catalog carousels or search grids.
```typescript
interface MovieItem {
  id: string;               // Unique ID within provider (typically slug)
  title: string;            // Primary title
  poster?: string;          // Direct image URL for poster thumbnail
  backdrop?: string;        // Optional horizontal banner image URL
  year?: number;            // Release year
  quality?: string;         // e.g., "HD", "FHD", "4K", "CAM"
  rating?: number;          // Score (0.0 to 10.0)
  slug: string;             // Slug identifier passed to getDetail()
  providerId?: string;      // Injected automatically by host
  type?: string;            // "movie" | "tv"
}
```

### 4.2. `HomeSection`
Groups movie items into named horizontal rows on the Home screen.
```typescript
interface HomeSection {
  title: string;            // Section title (e.g. "Phim Mới Cập Nhật")
  items: MovieItem[];       // Array of items in this row
}
```

### 4.3. `SearchResult`
Contains search results with pagination.
```typescript
interface SearchResult {
  items: MovieItem[];       // Matching movie items
  hasMore: boolean;         // True if subsequent pages exist
  page: number;             // Current page number
}
```

### 4.4. `MovieDetail`
Complete metadata for a movie or TV show.
```typescript
interface MovieDetail {
  id: string;
  title: string;
  originalTitle?: string;
  poster?: string;
  backdrop?: string;
  description?: string;
  year?: number;
  duration?: string;        // e.g. "120 phút", "45 phút/tập"
  rating?: number;
  genres: string[];         // e.g. ["Hành Động", "Viễn Tưởng"]
  country?: string;
  director?: string;
  cast?: string[];
  type: "movie" | "tv";
  servers: EpisodeServer[]; // Available streaming servers
}
```

### 4.5. `EpisodeServer` & `EpisodeData`
Organizes episodes into servers (e.g., "Server #1 (Vietsub)", "Server #2 (Thuyết Minh)").
```typescript
interface EpisodeServer {
  name: string;             // Server display name
  episodes: EpisodeData[];  // List of episodes hosted on this server
}

interface EpisodeData {
  id: string;               // Unique episode identifier
  name: string;             // Display label (e.g. "Tập 01", "Full")
  data: any;                // Opaque data payload needed by getStream()
                            // (e.g., link_m3u8, embedUrl, slug, or server ID)
  slug?: string;            // Optional slug reference
}
```

### 4.6. `EpisodeStream`
The resolved playable stream returned by `getStream(episodeData)`.
```typescript
interface EpisodeStream {
  type: "hls" | "mp4" | "embed";  // Stream protocol
  url: string;                     // Direct playable video URL or embed page URL
  headers?: Record<string, string>;// Custom HTTP headers required for playback
                                   // (e.g., { "Referer": "https://vsmov.com/" })
  subtitles?: Subtitle[];          // Array of external subtitle tracks
}

interface Subtitle {
  url: string;              // Direct URL to .vtt or .srt file
  lang: string;             // Language label (e.g., "Tiếng Việt", "English")
  format?: "vtt" | "srt";   // Subtitle format
}
```

---

## 5. Host Runtime APIs Available in Plugins

Inside QuickJS-NG, the host injects standard browser-compatible APIs:

### `fetch(url, options)`
Asynchronous HTTP client backed by OkHttp:
```javascript
const res = await fetch("https://api.example.com/items", {
  method: "POST", // or "GET"
  headers: {
    "User-Agent": "Mozilla/5.0 ...",
    "Content-Type": "application/json"
  },
  body: JSON.stringify({ query: "action" })
});

const json = await res.json();
const text = await res.text();
```

### `context.storage`
Key-value storage scoped per plugin:
```javascript
await context.storage.set("token", "xyz");
const token = await context.storage.get("token");
await context.storage.delete("token");
```

### `context.log`
Structured logging to Logcat:
```javascript
context.log.info("Loaded items: " + items.length);
context.log.warn("Warning occurred");
context.log.error("Failed to parse: " + err.message);
```

### `context.ui.showToast(message, isLong)`
Display a native Android Toast notification to the user from JavaScript Sandbox:
```javascript
// Short toast (default)
context.ui?.showToast("SexTop1: Đã kết nối tên miền mới https://sextop1.land");

// Long toast
context.ui?.showToast("Máy chủ đang bảo trì, vui lòng quay lại sau!", true);
```


