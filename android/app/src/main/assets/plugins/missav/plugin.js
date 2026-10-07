// plugins/missav/plugin.js
// MissAV Provider migrated from lib/providers/missav.ts

const BASE_URL = "https://missav.media";
const FAKE_FAST_REFERER = "https://missav.ws/";

function cleanText(text) {
  return (text || "").replace(/\s+/g, " ").trim();
}

function parseMovies(html) {
  const movies = [];
  const seen = new Set();
  const langCodes = ["vi", "en", "ja", "cn", "kr", "th", "tw", "zh", "new", "release", "actresses", "genres", "makers"];

  // Fast & resilient regex parser for MissAV video cards
  const itemRegex = /<a\s+href=["'](https:\/\/missav\.media\/[a-zA-Z0-9_-]+)["'][^>]*alt=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi;
  let match;
  while ((match = itemRegex.exec(html)) !== null) {
    const rawUrl = match[1];
    const title = cleanText(match[2]);
    const inner = match[3];

    const slug = rawUrl.replace("https://missav.media/", "").replace(/^\/+|\/+$/g, "");
    if (!slug || langCodes.includes(slug.toLowerCase())) continue;

    if (!seen.has(slug)) {
      seen.add(slug);
      let thumb = "";
      const imgMatch = inner.match(/(?:data-src|src)=["']([^"']+)["']/i);
      if (imgMatch) thumb = imgMatch[1];

      movies.push({
        slug: slug,
        name: title || slug,
        originName: title || slug,
        thumbUrl: thumb,
        posterUrl: thumb,
        year: null,
        type: "single",
        quality: "FHD",
        language: "Phụ Đề",
        providerId: "missav"
      });
    }
  }

  return movies;
}

function unpackDeanEdwards(p, a, c, k) {
  const getWord = (num) => {
    return (num < a ? '' : getWord(Math.floor(num / a))) +
      ((num = num % a) > 35 ? String.fromCharCode(num + 29) : num.toString(36));
  };
  const dict = {};
  while (c--) {
    dict[getWord(c)] = k[c] || getWord(c);
  }
  return p.replace(/\b\w+\b/g, (w) => dict[w] || w);
}

function extractStreamUuid(html) {
  let rawM3u8 = "";
  let uuid = "";

  const packerRegex = /eval\(function\(p,a,c,k,e,d\)[\s\S]*?\}\s*\(\s*(['"][\s\S]*?['"])\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(['"][\s\S]*?['"])\.split\('\|'\)/i;
  const match = html.match(packerRegex);

  if (match) {
    try {
      const p = match[1].slice(1, -1).replace(/\\'/g, "'").replace(/\\"/g, '"');
      const a = parseInt(match[2], 10);
      const c = parseInt(match[3], 10);
      const k = match[4].slice(1, -1).split('|');

      const unpacked = unpackDeanEdwards(p, a, c, k);
      const m3u8Match = unpacked.match(/https?:\/\/[^\s'"]+playlist\.m3u8/i) ||
                        unpacked.match(/https?:\/\/[^\s'"]+\.m3u8/i);
      if (m3u8Match) {
        rawM3u8 = m3u8Match[0].replace(/\\/g, "");
      }
    } catch (e) {}
  }

  if (rawM3u8 && !uuid) {
    const extractUuid = rawM3u8.match(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i);
    if (extractUuid) uuid = extractUuid[0];
  }

  if (!uuid) {
    const spriteMatch = html.match(/(?:nineyu|fourhoi)\.com\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/i);
    if (spriteMatch) uuid = spriteMatch[1];
  }
  if (!uuid) {
    const directUuidMatch = html.match(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i);
    if (directUuidMatch) uuid = directUuidMatch[0];
  }

  return uuid;
}

export default {
  id: "missav",
  name: "MissAV",
  version: "1.0.0",

  async getHome() {
    try {
      const url = `${BASE_URL}/new`;
      const res = await fetch(url, {
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
          "Referer": FAKE_FAST_REFERER
        }
      });
      const html = await res.text();
      const items = parseMovies(html);

      return {
        sections: [
          {
            title: "Phim Mới Cập Nhật",
            items: items.slice(0, 24)
          }
        ]
      };
    } catch (e) {
      return { sections: [] };
    }
  },

  async search(keyword, page = 1) {
    try {
      const url = `${BASE_URL}/search/${encodeURIComponent(keyword)}?page=${page}`;
      const res = await fetch(url, {
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
          "Referer": FAKE_FAST_REFERER
        }
      });
      const html = await res.text();
      const items = parseMovies(html);

      return {
        items,
        page,
        hasNext: items.length >= 12
      };
    } catch (e) {
      return { items: [], page, hasNext: false };
    }
  },

  async getDetail(slug) {
    const detailUrl = `${BASE_URL}/${slug}`;
    const res = await fetch(detailUrl, {
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": FAKE_FAST_REFERER
      }
    });
    const html = await res.text();

    const titleMatch = html.match(/<h1[^>]*>([\s\S]*?)<\/h1>/i);
    const title = titleMatch ? cleanText(titleMatch[1].replace(/<[^>]+>/g, '')) : slug;

    const ogImg = html.match(/<meta\s+property=["']og:image["']\s+content=["']([^"']+)["']/i);
    const thumb = ogImg ? ogImg[1] : "";

    const descMatch = html.match(/<meta\s+name=["']description["']\s+content=["']([^"']+)["']/i);
    const description = descMatch ? descMatch[1] : "";

    const uuid = extractStreamUuid(html);

    return {
      slug: slug,
      name: title,
      originName: title,
      thumbUrl: thumb,
      posterUrl: thumb,
      backdropUrl: thumb,
      year: null,
      type: "single",
      status: "completed",
      quality: "FHD",
      language: "Phụ Đề",
      description: description,
      actors: [],
      directors: [],
      genres: ["Adult", "18+"],
      countries: ["Nhật Bản"],
      episodes: [
        {
          serverName: "Surrit HLS",
          episodes: [
            {
              id: "full-auto",
              name: "Auto Quality (HLS)",
              slug: "full-auto",
              number: 1,
              serverName: "Surrit HLS",
              data: {
                uuid: uuid,
                quality: "auto",
                detailUrl: detailUrl
              }
            },
            {
              id: "full-720p",
              name: "720p HD",
              slug: "full-720p",
              number: 2,
              serverName: "Surrit HLS",
              data: {
                uuid: uuid,
                quality: "720p",
                detailUrl: detailUrl
              }
            }
          ]
        }
      ]
    };
  },

  async getStream(episodeData) {
    const data = episodeData.data || {};
    let uuid = data.uuid;

    if (!uuid && data.detailUrl) {
      const res = await fetch(data.detailUrl, {
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
          "Referer": FAKE_FAST_REFERER
        }
      });
      const html = await res.text();
      uuid = extractStreamUuid(html);
    }

    if (!uuid) {
      throw new Error(`MissAV: Không thể bóc tách Stream UUID cho tập ${episodeData.name}`);
    }

    const streamUrl = data.quality === "720p"
      ? `https://surrit.mrstcdn.store/${uuid}/720p/video.m3u8`
      : `https://surrit.mrstcdn.store/${uuid}/playlist.m3u8`;

    return {
      type: "m3u8",
      url: streamUrl,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": FAKE_FAST_REFERER,
        "Origin": "https://missav.ws"
      },
      subtitles: []
    };
  }
};
