// plugins/vnst1com/plugin.js
// VNSexTop1 Provider migrated from lib/providers/vnst1com.ts

const BASE_URL = "https://vnsextop1.com";

function cleanText(text) {
  if (!text) return "";
  return text.replace(/<[^>]*>/g, "").replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/\s+/g, " ").trim();
}

function parseMovieList(html) {
  const items = [];
  const seen = new Set();

  const itemRegex = /<div[^>]*class=["'][^"']*item[^"']*["'][^>]*>([\s\S]*?)<\/div>\s*<\/div>/gi;
  let match;
  while ((match = itemRegex.exec(html)) !== null) {
    const block = match[1];

    const linkMatch = block.match(/<a[^>]*href=["']([^"']*\/phim-sex\/[^"']*)["'][^>]*>/i);
    const titleMatch = block.match(/<h4[^>]*>([\s\S]*?)<\/h4>/i) || block.match(/alt=["']([^"']+)["']/i);
    const imgMatch = block.match(/<img[^>]*src=["']([^"']+)["']/i);

    if (linkMatch) {
      const link = linkMatch[1];
      const slug = link.split('/phim-sex/')[1]?.split('?')[0] || link;
      const title = cleanText(titleMatch ? (titleMatch[1] || "") : slug);
      const thumb = imgMatch ? imgMatch[1] : "";

      if (slug && !seen.has(slug)) {
        seen.add(slug);
        items.push({
          slug,
          name: title || slug,
          originName: title || slug,
          thumbUrl: thumb,
          posterUrl: thumb,
          year: null,
          type: "single",
          quality: "HD",
          language: "Vietsub",
          providerId: "vnst1com"
        });
      }
    }
  }

  return items;
}

export default {
  id: "vnst1com",
  name: "VN SexTop1",
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
      const items = parseMovieList(html);

      return {
        sections: [
          {
            title: "Phim Sex Mới Cập Nhật",
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
      const url = `${BASE_URL}/tim-kiem/${encodeURIComponent(keyword)}/${page > 1 ? `?page=${page}` : ''}`;
      const res = await fetch(url, {
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
          "Referer": `${BASE_URL}/`
        }
      });
      const html = await res.text();
      const items = parseMovieList(html);

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
    const detailUrl = `${BASE_URL}/phim-sex/${slug.replace(/^\/+|\/+$/g, '')}`;
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

    const descMatch = html.match(/<article[^>]*>([\s\S]*?)<\/article>/i);
    const description = descMatch ? cleanText(descMatch[1]) : "";

    const sourceRegex = /data-source=["']([^"']+)["']/gi;
    let sMatch;
    const servers = [];
    const seenSources = new Set();

    while ((sMatch = sourceRegex.exec(html)) !== null) {
      const src = sMatch[1];
      if (src.startsWith('http') && !seenSources.has(src)) {
        seenSources.add(src);
        let host = "Embed";
        try { host = new URL(src).hostname.toUpperCase(); } catch (e) {}

        servers.push({
          serverName: host,
          episodes: [
            {
              id: `ep-${servers.length + 1}`,
              name: "Full",
              slug: "full",
              number: 1,
              serverName: host,
              data: {
                embed: src,
                detailUrl
              }
            }
          ]
        });
      }
    }

    if (servers.length === 0) {
      servers.push({
        serverName: "VNST1",
        episodes: [
          {
            id: "ep-1",
            name: "Full",
            slug: "full",
            number: 1,
            serverName: "VNST1",
            data: {
              embed: detailUrl,
              detailUrl
            }
          }
        ]
      });
    }

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
      language: "Vietsub",
      description,
      actors: [],
      directors: [],
      genres: ["18+"],
      countries: ["Việt Nam"],
      episodes: servers
    };
  },

  async getStream(episodeData) {
    const data = episodeData.data || {};
    const embedUrl = data.embed || data.detailUrl || BASE_URL;

    return {
      type: "embed",
      url: embedUrl,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": `${BASE_URL}/`
      },
      subtitles: []
    };
  }
};
