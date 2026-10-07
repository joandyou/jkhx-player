# Guide: Migrating Scrapers from TypeScript / Next.js to Pure JavaScript

This document outlines the systematic process of converting scrapers from a Node.js / Next.js TypeScript environment to standalone QuickJS-NG JavaScript plugins for JKHX Player.

---

## 1. Key Migration Differences

| Feature | Legacy Next.js / TypeScript | JKHX Player (QuickJS-NG) |
|---|---|---|
| **Language** | TypeScript (`.ts`) | Pure Modern JavaScript (ES2023 / `.js`) |
| **Module System** | Node.js CommonJS or TS ESM | Standard ES Modules (`export default { ... }`) |
| **Stream Resolution** | Performed inside `getDetail()` | Strictly deferred to `getStream(episodeData)` |
| **HTTP Proxy** | Internal `/api/proxy` (CORS bypass) | Direct `fetch()` via OkHttp (no CORS restrictions on Android) |
| **HTML Parser** | `npm install cheerio` | Zero-dependency Cheerio micro-library (`plugins/common/cheerio.js`) |
| **Node.js Built-ins** | `Buffer`, `crypto`, `fs`, `path` | Native JS (`atob`, `btoa`, `crypto.getRandomValues`) or pure JS polyfills |
| **Stream Headers** | Proxied in Next.js API route | Returned directly in `EpisodeStream.headers` for ExoPlayer |

---

## 2. Step-by-Step Migration Pattern

### Step 1: Remove TypeScript Syntax
- Strip TypeScript interface declarations (`interface ...`, `type ...`).
- Remove parameter type annotations (`slug: string`, `page: number`).
- Convert type assertions (`as string`, `<T>`) to plain JS variables.

### Step 2: Separate `getDetail()` and `getStream()`

In legacy scrapers, episode streams were resolved immediately during detail parsing:
```typescript
// ❌ BAD (Legacy JFilm pattern)
async getDetail(slug: string) {
  // Scrapes detail...
  // Then immediately fetches iframe or decodes M3U8 for all episodes:
  const stream = await resolveStream(item.url);
  return {
    ...detail,
    episodes: [{ name: "01", streamUrl: stream.url }] // Violates architecture!
  };
}
```

Refactor this into the two-tier pattern:
```javascript
// ✅ GOOD (JKHX Player pattern)
async getDetail(slug) {
  // Only scrape metadata and episode identifiers/payloads
  return {
    ...detail,
    servers: [{
      name: "Server 1",
      episodes: [{
        id: "ep-1",
        name: "Tập 01",
        slug: "tap-01",
        data: {
          embedUrl: item.link_embed,
          m3u8: item.link_m3u8
        }
      }]
    }]
  };
},

async getStream(episodeData) {
  const { data } = episodeData;
  if (data.m3u8) {
    return {
      type: "hls",
      url: data.m3u8,
      headers: { "Referer": "https://provider.com/" }
    };
  }
  // If resolution is needed, perform it now for this single episode:
  const res = await fetch(data.embedUrl);
  const html = await res.text();
  const directM3u8 = extractM3u8FromHtml(html);
  return {
    type: "hls",
    url: directM3u8,
    headers: { "Referer": data.embedUrl }
  };
}
```

### Step 3: Replace Cheerio with Pure JS DOM Parser
Replace `import * as cheerio from 'cheerio'` with:
```javascript
import { load } from '../common/cheerio.js';

const $ = load(htmlString);
const title = $('.movie-title').text();
const poster = $('.poster img').attr('src');
```

The bundled `cheerio.js` micro-parser supports:
- Tag, class, and ID selectors (`div`, `.class-name`, `#id`).
- Traversal (`.find()`, `.first()`, `.last()`, `.eq()`, `.each()`).
- Data access (`.text()`, `.attr(name)`, `.html()`).

### Step 4: Handle JavaScript Obfuscators & Unpackers
Many streaming embeds use Dean Edwards P.A.C.K.E.R. (`eval(function(p,a,c,k,e,d)...`).
In pure JS, use a regex extractor or unpacker function:
```javascript
function unpack(packedCode) {
  // Regex extract p, a, c, k, e, d
  // Deobfuscate dictionary lookups into raw JS string
}
```

### Step 5: Replace Node `Buffer` with Browser `atob` / `btoa`
```javascript
// Replace: Buffer.from(str, 'base64').toString('utf-8')
// With:
const decoded = atob(base64String);

// Replace: Buffer.from(str).toString('base64')
// With:
const encoded = btoa(utf8String);
```

---

## 3. Verifying the Migrated Plugin

Run the automated test suite against the migrated plugin:
```bash
node test-runner/test-plugin.js plugins/<provider-id>
```
Verify that all 4 test stages (`getHome`, `search`, `getDetail`, `getStream`) report `PASS` with zero uncaught exceptions.
