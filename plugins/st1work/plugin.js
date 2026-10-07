// plugins/st1work/plugin.js
// SexTop1 Provider migrated from lib/providers/st1work.ts

const BASE_URL = "https://sextop1.land";

function cleanText(text) {
  return (text || "").replace(/<[^>]+>/g, "").replace(/\s+/g, " ").trim();
}

function parseMovies(html) {
  const movies = [];
  const seen = new Set();

  const itemRegex = /<(?:article|div|a)[^>]*class=["'][^"']*(?:dp-item|grid-item|video-item|cat-video-item|item)[^"']*["'][^>]*>([\s\S]*?)<\/(?:article|div|a)>/gi;
  let match;
  while ((match = itemRegex.exec(html)) !== null) {
    const block = match[1];

    const linkMatch = block.match(/<a[^>]*href=["']([^"']+)["'][^>]*>/i);
    const titleMatch = block.match(/<(?:h[23]|span|a)[^>]*class=["'][^"']*(?:title|entry-title)[^"']*["'][^>]*>([\s\S]*?)<\/(?:h[23]|span|a)>/i)
      || block.match(/title=["']([^"']+)["']/i);
    const imgMatch = block.match(/(?:data-src|data-original|src)=["']([^"']+)["']/i);

    if (linkMatch) {
      let rawUrl = linkMatch[1];
      let slug = rawUrl.replace(BASE_URL, "").replace(/^\/+|\/+$/g, "");
      const title = cleanText(titleMatch ? (titleMatch[1] || "") : slug);
      let thumb = imgMatch ? imgMatch[1] : "";
      if (thumb && !thumb.startsWith("http")) thumb = `${BASE_URL}${thumb.startsWith("/") ? "" : "/"}${thumb}`;

      if (slug && !seen.has(slug)) {
        seen.add(slug);
        movies.push({
          slug,
          name: title || slug,
          originName: title || slug,
          thumbUrl: thumb,
          posterUrl: thumb,
          year: null,
          type: "single",
          quality: "HD",
          language: "Việt Nam",
          providerId: "st1work"
        });
      }
    }
  }

  return movies;
}

export default {
  id: "st1work",
  name: "SexTop1 Land",
  version: "1.0.0",

  async getHome() {
    try {
      const url = `${BASE_URL}/`;
      const res = await fetch(url, {
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
          "Referer": `${BASE_URL}/`
        }
      });
      const html = await res.text();
      const items = parseMovies(html);

      return {
        sections: [
          {
            title: "Phim Sex Mới Nhất",
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
      const url = `${BASE_URL}/?s=${encodeURIComponent(keyword)}${page > 1 ? `&paged=${page}` : ''}`;
      const res = await fetch(url, {
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
          "Referer": `${BASE_URL}/`
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
    const detailUrl = `${BASE_URL}/${slug.replace(/^\/+|\/+$/g, '')}/`;
    const res = await fetch(detailUrl, {
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": `${BASE_URL}/`
      }
    });
    const html = await res.text();

    const titleMatch = html.match(/<h1[^>]*>([\s\S]*?)<\/h1>/i);
    const title = titleMatch ? cleanText(titleMatch[1]) : slug;

    const ogImg = html.match(/<meta\s+property=["']og:image["']\s+content=["']([^"']+)["']/i);
    const thumb = ogImg ? ogImg[1] : "";

    const descMatch = html.match(/class=["'][^"']*(?:entry-content|video-description)[^"']*["'][^>]*>([\s\S]*?)<\/div>/i);
    const description = descMatch ? cleanText(descMatch[1]) : "";

    // Tìm link m3u8 hoặc embed trực tiếp trong trang
    const m3u8Match = html.match(/(https?:\/\/[^"'\s<>]+\.m3u8[^"'\s<>]*)/i);
    const iframeMatch = html.match(/<iframe[^>]*src=["']([^"']+)["']/i);

    return {
      slug,
      name: title,
      originName: title,
      thumbUrl: thumb,
      posterUrl: thumb,
      backdropUrl: thumb,
      year: null,
      type: "single",
      status: "completed",
      quality: "HD",
      language: "Tiếng Việt",
      description,
      actors: [],
      directors: [],
      genres: ["18+"],
      countries: ["Việt Nam"],
      episodes: [
        {
          serverName: "SexTop1",
          episodes: [
            {
              id: "full",
              name: "Full HD",
              slug: "full",
              number: 1,
              serverName: "SexTop1",
              data: {
                m3u8: m3u8Match ? m3u8Match[1] : "",
                embed: iframeMatch ? iframeMatch[1] : detailUrl
              }
            }
          ]
        }
      ]
    };
  },

  async getStream(episodeData) {
    const data = episodeData.data || {};
    if (data.m3u8) {
      return {
        type: "m3u8",
        url: data.m3u8,
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
          "Referer": `${BASE_URL}/`
        },
        subtitles: []
      };
    }

    return {
      type: "embed",
      url: data.embed || BASE_URL,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": `${BASE_URL}/`
      },
      subtitles: []
    };
  }
};

