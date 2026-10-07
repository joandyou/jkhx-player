# Migrated Providers Audit Report

This report summarizes the migration of all content providers from the legacy `jfilm` TypeScript codebase (`/home/jtmy/jfilm/lib/providers/*.ts`) to JKHX Player's standalone QuickJS-NG JavaScript plugins.

---

## 1. Migration Summary Matrix

| ID | Name | Source Site | Types | Adult | Legacy File | Status | Live Stream Verified |
|---|---|---|---|---|---|---|---|
| `mock` | Mock Provider | Built-in Mock | Movies, TV | No | N/A | **ACTIVE** | Yes (Big Buck Bunny HLS) |
| `kkphim` | KKPhim | `phimapi.com` | Movies, TV | No | `kkphim.ts` | **ACTIVE** | Yes (Live M3U8 resolved) |
| `nguonc` | NguonC | `phim.nguonc.com` | Movies, TV | No | `nguonc.ts` | **ACTIVE** | Yes (Live M3U8 resolved) |
| `vsmov` | VSMov | `vsmov.com` | Movies, TV | No | `vsmov.ts` | **ACTIVE** | Yes (Live M3U8 resolved) |
| `vsnguon` | VSNguon | `nguon.vsphim.com` | Movies, TV | No | `vsnguon.ts` | **ACTIVE** | Yes (Live M3U8 resolved) |
| `missav` | MissAV | `missav.media` | Adult, JAV | Yes | `missav.ts` | **ACTIVE** | Yes (Dean Edwards deobfuscated live HLS) |
| `vlxx` | VLXX | `vlxx.phd` | Adult | Yes | `vlxx.ts` | **ACTIVE** | Yes (Live M3U8/Embed) |
| `xsub` | SexSub | `sexsub.moe` | Adult | Yes | `xsub.ts` | **ACTIVE** | Yes (Live M3U8/Embed) |
| `st1work` | Sextop1 Land | `sextop1.land` | Adult | Yes | `st1work.ts` | **ACTIVE** | Yes (Live M3U8/Embed) |
| `vnst1com` | VNSextop1 | `vnsextop1.com` | Adult | Yes | `vnst1com.ts` | **ACTIVE** | Yes (Live M3U8/Embed) |
| `clbpx` | CLB Phim Xưa | `clbphimxua.com` | Movies, TV | No | `clbpx.ts` | **ACTIVE** | Yes (Embed/Drive) |
| `ophim` | OPhim | `ophim1.com` | Movies, TV | No | `ophim.ts` | **INACTIVE (HOST)** | Handled (Host returned 404) |
| `bunchatv` | BunCha TV | `bunchatv4.net` | TV, Live | No | `bunchatv.ts` | **INACTIVE (HOST)** | Handled (DNS unresolvable) |

---

## 2. Detailed Provider Audits

### 2.1. KKPhim (`kkphim`)
- **API Endpoint**: `https://phimapi.com`
- **Data Format**: JSON REST API.
- **Migration Details**:
  - `getHome()`: Fetches `/danh-sach/phim-moi-cap-nhat?page=X`.
  - `search(query)`: Fetches `/v1/api/tim-kiem?keyword=...`.
  - `getDetail(slug)`: Fetches `/phim/${slug}`, categorizes episodes by server name (e.g. "#Hà Nội", "#Vietsub").
  - `getStream(episodeData)`: Extracts `link_m3u8` from payload. Returns `type: "hls"` with clean User-Agent.

### 2.2. NguonC (`nguonc`)
- **API Endpoint**: `https://phim.nguonc.com/api/films`
- **Data Format**: JSON REST API.
- **Migration Details**:
  - `getHome()`: Fetches `/phim-moi-cap-nhat?page=X`.
  - `search(query)`: Fetches `/search?keyword=...`.
  - `getDetail(slug)`: Fetches `/film/${slug}`. Extracts servers (`Server #1`, `Server #2`).
  - `getStream(episodeData)`: Extracts `m3u8` or `embed` from `episodeData.data`.

### 2.3. VSMov (`vsmov`)
- **API Endpoint**: `https://vsmov.com/api/v1`
- **Data Format**: JSON REST API.
- **Migration Details**:
  - `getHome()`: Fetches `/home?page=X`.
  - `search(query)`: Fetches `/search?keyword=...`.
  - `getDetail(slug)`: Fetches `/movie/${slug}`, maps episode items.
  - `getStream(episodeData)`: Fetches episode stream info using `vsmov.com` secret tokens; includes required `Referer: https://vsmov.com/` in returned headers.

### 2.4. VSNguon (`vsnguon`)
- **API Endpoint**: `https://nguon.vsphim.com/api/v1`
- **Data Format**: JSON REST API.
- **Migration Details**:
  - Similar structure to VSMov, targeting `nguon.vsphim.com` source feeds.
  - Direct HLS stream extraction verified.

### 2.5. MissAV (`missav`)
- **Source**: `https://missav.media`
- **Data Format**: HTML Scraping + Obfuscated Dean Edwards JavaScript.
- **Migration Details**:
  - `getHome()`: Scrapes homepage for new releases using regex/cheerio.
  - `search(query)`: Scrapes `/search/${query}`.
  - `getDetail(slug)`: Extracts title, duration, video metadata, and embeds episode payload.
  - `getStream(episodeData)`: Scrapes video page HTML, isolates `eval(function(p,a,c,k,e,d)...)` script, unpacks the base-62 dictionary, and extracts the direct `https://surrit.mrstcdn.store/.../playlist.m3u8` master manifest. Verified working with live playback!

### 2.6. VLXX (`vlxx`)
- **Source**: `https://vlxx.phd`
- **Data Format**: HTML Scraping.
- **Migration Details**:
  - Scrapes video grid and tags.
  - `getStream(episodeData)`: Parses player script or iframe to extract direct HLS or high-resolution MP4 stream.

### 2.7. SexSub (`xsub`)
- **Source**: `https://sexsub.moe`
- **Data Format**: HTML Scraping.
- **Migration Details**:
  - Scrapes featured and latest posts.
  - `getStream(episodeData)`: Resolves streaming embeds and returns stream with necessary spoofed referrers.

### 2.8. Sextop1 Land (`st1work`) & VNSextop1 (`vnst1com`)
- **Source**: `https://sextop1.land` & `https://vnsextop1.com`
- **Data Format**: HTML Scraping.
- **Migration Details**:
  - Scrapes article lists, pagination, and player container tags.
  - Converts video embed frames into playable streams or standard embeds.

### 2.9. CLB Phim Xưa (`clbpx`)
- **Source**: `https://clbphimxua.com`
- **Data Format**: HTML / WordPress feeds.
- **Migration Details**:
  - Scrapes vintage classic movie archives.
  - `getStream()` returns Google Drive or embed links.

### 2.10. OPhim (`ophim`) & BunCha TV (`bunchatv`)
- **Source**: `https://ophim1.com` & `https://bunchatv4.net`
- **Audit Findings**:
  - In legacy `jfilm/lib/providers/index.ts`, both providers were already commented out or inactive due to upstream domain expiration (`ophim1.com` returns 404, `bunchatv4.net` DNS NXDOMAIN).
  - Both plugins were migrated with resilient try/catch error handling. When active, they comply with the standard API contract without throwing fatal exceptions that would crash the host app.
