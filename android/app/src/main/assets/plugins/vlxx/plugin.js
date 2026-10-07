// plugins/vlxx/plugin.js
// VLXX Provider migrated from lib/providers/vlxx.ts

const BASE_URL = "https://vlxx.phd";

function cleanText(text) {
  return (text || "").replace(/<[^>]+>/g, "").replace(/\s+/g, " ").trim();
}

function parseMovies(html) {
  const movies = [];
  const seen = new Set();

  const itemRegex = /<div[^>]*class=["'][^"']*video-item[^"']*["'][^>]*>([\s\S]*?)<\/div>/gi;
  let match;
  while ((match = itemRegex.exec(html)) !== null) {
    const block = match[1];

    const linkMatch = block.match(/<a[^>]*href=["']([^"']+)["'][^>]*>/i);
    const titleMatch = block.match(/<a[^>]*title=["']([^"']+)["']/i) || block.match(/<span[^>]*class=["'][^"']*title[^"']*["'][^>]*>([\s\S]*?)<\/span>/i);
    const imgMatch = block.match(/(?:data-src|src)=["']([^"']+)["']/i);

    if (linkMatch) {
      let rawUrl = linkMatch[1];
      let slug = rawUrl.replace(BASE_URL, "").replace(/^\/+|\/+$/g, "");
      const title = cleanText(titleMatch ? (titleMatch[1] || "") : slug);
      const thumb = imgMatch ? imgMatch[1] : "";

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
          providerId: "vlxx"
        });
      }
    }
  }

  return movies;
}

async function resolveAjaxEmbed(videoId, serverId) {
  if (!videoId || !serverId) return "";
  try {
    const res = await fetch(`${BASE_URL}/ajax.php`, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "Referer": `${BASE_URL}/`,
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
      },
      body: `action=server&id=${encodeURIComponent(videoId)}&server=${encodeURIComponent(serverId)}`
    });

    const text = await res.text();
    const embedMatch = text.match(/src=["']([^"']+)["']/i);
    return embedMatch ? embedMatch[1] : "";
  } catch (e) {
    return "";
  }
}

export default {
  id: "vlxx",
  name: "VLXX",
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
      const safeKeyword = encodeURIComponent(keyword.replace(/\s+/g, "-"));
      const url = `${BASE_URL}/search/${safeKeyword}/${page > 1 ? `?page=${page}` : ''}`;
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

    const descMatch = html.match(/<div[^>]*class=["'][^"']*video-description[^"']*["'][^>]*>([\s\S]*?)<\/div>/i);
    const description = descMatch ? cleanText(descMatch[1]) : "";

    let videoId = html.match(/data-id=["'](\d+)["']/i)?.[1] || "";
    if (!videoId) {
      const idMatch = detailUrl.match(/\/(\d+)\/?$/);
      if (idMatch) videoId = idMatch[1];
    }

    const serverRegex = /onclick=["']server\((\d+),\s*(\d+)\)["']/gi;
    let serverMatch;
    const episodes = [];

    while ((serverMatch = serverRegex.exec(html)) !== null) {
      const serverId = serverMatch[1];
      episodes.push({
        id: `s${serverId}`,
        name: `Server #${serverId}`,
        slug: `s${serverId}`,
        number: parseInt(serverId) || 1,
        serverName: "VLXX",
        data: {
          videoId,
          serverId,
          detailUrl
        }
      });
    }

    if (episodes.length === 0) {
      episodes.push({
        id: "s1",
        name: "Server #1",
        slug: "s1",
        number: 1,
        serverName: "VLXX",
        data: {
          videoId,
          serverId: "1",
          detailUrl
        }
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
      language: "Tiếng Việt",
      description: description,
      actors: [],
      directors: [],
      genres: ["18+"],
      countries: ["Việt Nam"],
      episodes: [
        {
          serverName: "VLXX Player",
          episodes
        }
      ]
    };
  },

  async getStream(episodeData) {
    const data = episodeData.data || {};
    let embedUrl = "";

    if (data.videoId && data.serverId) {
      embedUrl = await resolveAjaxEmbed(data.videoId, data.serverId);
    }

    if (!embedUrl) embedUrl = data.detailUrl || BASE_URL;

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
