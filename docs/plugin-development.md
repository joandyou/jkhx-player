# Plugin Development Guide

This guide walks you through building a new custom streaming plugin for JKHX Player from scratch.

---

## 1. Directory Structure

Create a folder for your plugin inside `plugins/<your-plugin-id>`:

```
plugins/my-provider/
├── manifest.json
└── plugin.js
```

---

## 2. Step 1: Create `manifest.json`

Define the metadata for your provider:

```json
{
  "id": "my-provider",
  "name": "My Provider",
  "version": "1.0.0",
  "description": "Stream HD movies from MyProvider.com",
  "icon": "https://myprovider.com/favicon.ico",
  "entry": "plugin.js",
  "author": "Community",
  "homepage": "https://myprovider.com",
  "types": ["movies", "tv"],
  "isAdult": false,
  "status": "active"
}
```

---

## 3. Step 2: Implement `plugin.js`

Here is an annotated template for creating a pure JavaScript provider:

```javascript
/**
 * MyProvider - JKHX Player Plugin
 */

const BASE_URL = "https://api.myprovider.com";

const defaultHeaders = {
  "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36",
  "Accept": "application/json"
};

export default {
  /**
   * 1. GET HOME FEED
   * Returns categorized movie rows for the Home tab.
   */
  async getHome(page = 1) {
    const res = await fetch(`${BASE_URL}/home?page=${page}`, { headers: defaultHeaders });
    const data = await res.json();

    const sections = [];

    if (data.latest && data.latest.length > 0) {
      sections.push({
        title: "Phim Mới Cập Nhật",
        items: data.latest.map(item => ({
          id: String(item.id || item.slug),
          slug: item.slug,
          title: item.title,
          poster: item.poster_url,
          year: item.year,
          quality: item.quality || "HD",
          type: item.is_series ? "tv" : "movie"
        }))
      });
    }

    return sections;
  },

  /**
   * 2. SEARCH
   * Search titles matching the user query.
   */
  async search(query, page = 1) {
    const res = await fetch(`${BASE_URL}/search?q=${encodeURIComponent(query)}&page=${page}`, {
      headers: defaultHeaders
    });
    const data = await res.json();

    const items = (data.results || []).map(item => ({
      id: String(item.id || item.slug),
      slug: item.slug,
      title: item.title,
      poster: item.poster_url,
      year: item.year,
      quality: item.quality || "HD",
      type: item.is_series ? "tv" : "movie"
    }));

    return {
      items,
      hasMore: data.page < data.totalPages,
      page: data.page || page
    };
  },

  /**
   * 3. GET DETAIL
   * Fetch movie metadata and list of episodes.
   * CRITICAL: Do NOT resolve video playback streams here!
   * Put server data or IDs inside `episode.data`.
   */
  async getDetail(slug) {
    const res = await fetch(`${BASE_URL}/movie/${slug}`, { headers: defaultHeaders });
    const data = await res.json();
    const movie = data.movie;

    const servers = [];

    // Group episodes into servers
    if (movie.episodes && movie.episodes.length > 0) {
      for (const server of movie.episodes) {
        servers.push({
          name: server.server_name || "Mặc Định",
          episodes: server.items.map(ep => ({
            id: String(ep.id || ep.slug),
            name: ep.name || "Tập",
            slug: ep.slug,
            // Store raw payload needed for playback:
            data: {
              file_id: ep.file_id,
              embed_url: ep.embed_url,
              stream_url: ep.stream_url
            }
          }))
        });
      }
    }

    return {
      id: String(movie.id || slug),
      title: movie.title,
      originalTitle: movie.origin_name,
      poster: movie.poster_url,
      backdrop: movie.thumb_url,
      description: movie.description,
      year: movie.year,
      duration: movie.duration,
      genres: movie.genres || [],
      country: movie.country,
      type: movie.is_series ? "tv" : "movie",
      servers
    };
  },

  /**
   * 4. GET STREAM
   * Resolves the playable HLS, MP4, or Embed URL for the selected episode.
   */
  async getStream(episodeData) {
    const data = episodeData.data || {};

    // Example A: Direct stream already provided in payload
    if (data.stream_url && data.stream_url.includes(".m3u8")) {
      return {
        type: "hls",
        url: data.stream_url,
        headers: {
          "Referer": BASE_URL + "/",
          "User-Agent": defaultHeaders["User-Agent"]
        }
      };
    }

    // Example B: Need an on-demand API call to get decrypted video URL
    if (data.file_id) {
      const res = await fetch(`${BASE_URL}/api/stream/${data.file_id}`, { headers: defaultHeaders });
      const streamInfo = await res.json();
      return {
        type: streamInfo.url.endsWith(".mp4") ? "mp4" : "hls",
        url: streamInfo.url,
        headers: {
          "Referer": BASE_URL + "/"
        },
        subtitles: (streamInfo.subtitles || []).map(s => ({
          url: s.file,
          lang: s.label,
          format: "vtt"
        }))
      };
    }

    // Example C: Embed iframe fallback
    if (data.embed_url) {
      return {
        type: "embed",
        url: data.embed_url
      };
    }

    throw new Error("Unable to resolve playable stream for episode " + episodeData.name);
  }
};
```

---

## 4. Testing Your Plugin Locally

You can test your plugin before deploying it to an Android device using the built-in Node.js runner:

```bash
# Run test suite against your plugin
node test-runner/test-plugin.js plugins/my-provider
```

The test runner:
1. Validates `manifest.json` schema.
2. Checks that all 4 required methods are exported.
3. Calls `getHome()` and checks `HomeSection[]`.
4. Calls `search("love")` and checks `SearchResult`.
5. Calls `getDetail(slug)` and verifies that `episodes` contain `data` payloads.
6. Calls `getStream(episodeData)` and asserts that an `EpisodeStream` object with valid URL and type is returned.

---

## 5. Installing into JKHX Player

### Method 1: Bundled with the APK
Copy your plugin directory into `android/app/src/main/assets/plugins/<your-plugin-id>/` and register it in `android/app/src/main/assets/plugins/manifest.json`.

### Method 2: Remote Repository
Host your plugin files on any static HTTP host (e.g., GitHub Pages) and register it in a remote `manifest.json`. Add the repository URL under **Settings > Repositories** in the app.
