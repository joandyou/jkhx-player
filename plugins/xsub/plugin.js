// plugins/xsub/plugin.js
// SexSub Provider migrated from lib/providers/xsub.ts

const BASE_URL = "https://sexsub.moe";

function cleanText(text) {
  return (text || "").replace(/<[^>]+>/g, "").replace(/\s+/g, " ").trim();
}

function parseMovieList(html) {
  const items = [];
  const seen = new Set();

  const itemRegex = /<div[^>]*class=["'][^"']*post-item[^"']*["'][^>]*>([\s\S]*?)<\/div>\s*<\/div>/gi;
  let match;
  while ((match = itemRegex.exec(html)) !== null) {
    const block = match[1];

    const linkMatch = block.match(/<a[^>]*href=["']([^"']+)["'][^>]*>/i);
    const titleMatch = block.match(/<h[23][^>]*>([\s\S]*?)<\/h[23]>/i) || block.match(/title=["']([^"']+)["']/i);
    const imgMatch = block.match(/(?:data-src|data-original|src)=["']([^"']+)["']/i);

    if (linkMatch) {
      let rawUrl = linkMatch[1];
      let slug = rawUrl.replace(BASE_URL, "").replace(/^\/+|\/+$/g, "").replace(/\//g, "---");
      let title = cleanText(titleMatch ? (titleMatch[1] || "") : slug);
      let thumb = imgMatch ? imgMatch[1] : "";
      if (thumb && !thumb.startsWith("http")) thumb = `${BASE_URL}${thumb.startsWith("/") ? "" : "/"}${thumb}`;

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
          providerId: "xsub"
        });
      }
    }
  }

  return items;
}

export default {
  id: "xsub",
  name: "SexSub",
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
            title: "Phim Sex Vietsub Mới",
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
      const items = parseMovieList(html);

      return {
        items,
        page,
        hasNext: items.length >= 10
      };
    } catch (e) {
      return { items: [], page, hasNext: false };
    }
  },

  async getDetail(slug) {
    const rawPath = slug.replace(/---/g, "/").replace(/^\/+|\/+$/g, "");
    const detailUrl = `${BASE_URL}/${rawPath}/`;

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

    const descMatch = html.match(/id=["']postContent["'][^>]*>([\s\S]*?)<\/div>/i);
    const description = descMatch ? cleanText(descMatch[1]) : "";

    const serverBtns = [];
    const btnRegex = /onclick=["']changeServer\(['"]([^'"]+)['"]\)[^>]*>([\s\S]*?)<\/(?:button|a)>/gi;
    let btnMatch;
    while ((btnMatch = btnRegex.exec(html)) !== null) {
      const svLink = btnMatch[1];
      const svName = cleanText(btnMatch[2]) || `Server #${serverBtns.length + 1}`;
      serverBtns.push({ svLink, svName });
    }

    if (serverBtns.length === 0) {
      serverBtns.push({ svLink: "", svName: "Server Mặc Định" });
    }

    const episodes = serverBtns.map((btn, idx) => ({
      id: `s${idx + 1}`,
      name: btn.svName,
      slug: `s${idx + 1}`,
      number: idx + 1,
      serverName: "SexSub",
      data: {
        svLink: btn.svLink,
        detailUrl
      }
    }));

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
      genres: ["18+", "Vietsub"],
      countries: ["Nhật Bản"],
      episodes: [
        {
          serverName: "SexSub Player",
          episodes
        }
      ]
    };
  },

  async getStream(episodeData) {
    const data = episodeData.data || {};
    const detailUrl = data.detailUrl || BASE_URL;
    let embedUrl = data.svLink || detailUrl;

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

