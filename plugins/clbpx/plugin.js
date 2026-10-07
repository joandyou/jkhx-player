// plugins/clbpx/plugin.js
// CLB Phim Xưa Provider migrated from lib/providers/clbpx.ts

const BASE_URL = "https://clbphimxua.com";

function cleanText(text) {
  return (text || "").replace(/<[^>]+>/g, "").replace(/&#8211;/g, '-').replace(/&#8217;/g, "'").replace(/\s+/g, " ").trim();
}

function parseMovieList(html) {
  const items = [];
  const seen = new Set();

  const articleRegex = /<article[^>]*>([\s\S]*?)<\/article>/gi;
  let match;
  while ((match = articleRegex.exec(html)) !== null) {
    const block = match[1];

    const linkMatch = block.match(/<a[^>]*href=["']([^"']+)["'][^>]*>/i);
    const titleMatch = block.match(/<h[23][^>]*>([\s\S]*?)<\/h[23]>/i) || block.match(/title=["']([^"']+)["']/i);
    const imgMatch = block.match(/<img[^>]*src=["']([^"']+)["']/i);

    if (linkMatch) {
      let rawUrl = linkMatch[1];
      const slugMatch = rawUrl.match(/clbphimxua\.com\/([^\/]+)\/?/);
      const slug = slugMatch ? slugMatch[1] : rawUrl.replace(BASE_URL, "").replace(/^\/+|\/+$/g, "");
      const title = cleanText(titleMatch ? (titleMatch[1] || "") : slug);
      const thumb = imgMatch ? imgMatch[1] : "";

      let year = null;
      const yearMatch = title.match(/(19\d{2}|20\d{2})/);
      if (yearMatch) year = parseInt(yearMatch[1], 10);

      if (slug && !seen.has(slug)) {
        seen.add(slug);
        items.push({
          slug,
          name: title || slug,
          originName: title || slug,
          thumbUrl: thumb,
          posterUrl: thumb,
          year,
          type: "single",
          quality: "HD",
          language: "Việt Nam",
          providerId: "clbpx"
        });
      }
    }
  }

  return items;
}

export default {
  id: "clbpx",
  name: "CLB Phim Xưa",
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
            title: "Phim Kinh Điển Mới Cập Nhật",
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
    const detailUrl = `${BASE_URL}/${slug.replace(/^\/+|\/+$/g, '')}/`;
    const res = await fetch(detailUrl, {
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": `${BASE_URL}/`
      }
    });
    const html = await res.text();

    const titleMatch = html.match(/<h1[^>]*class=["'][^"']*single-title[^"']*["'][^>]*>([\s\S]*?)<\/h1>/i) || html.match(/<h1[^>]*>([\s\S]*?)<\/h1>/i);
    const title = titleMatch ? cleanText(titleMatch[1]) : slug;

    const ogImg = html.match(/<meta\s+property=["']og:image["']\s+content=["']([^"']+)["']/i) || html.match(/class=["'][^"']*wp-post-image[^"']*["'][^>]*src=["']([^"']+)["']/i);
    const thumb = ogImg ? ogImg[1] : "";

    const descMatch = html.match(/class=["'][^"']*sigle-post-content-area[^"']*["'][^>]*>([\s\S]*?)<\/div>/i);
    const description = descMatch ? cleanText(descMatch[1]) : "";

    let year = null;
    const yearMatch = title.match(/(19\d{2}|20\d{2})/);
    if (yearMatch) year = parseInt(yearMatch[1], 10);

    const epRegex = /<a[^>]*href=["']([^"']*clbpx\.html\?v=[^"']*)["'][^>]*>([\s\S]*?)<\/a>/gi;
    let epMatch;
    const episodesList = [];
    while ((epMatch = epRegex.exec(html)) !== null) {
      const epUrl = epMatch[1];
      const epName = cleanText(epMatch[2]) || `Tập ${episodesList.length + 1}`;
      episodesList.push({ epUrl, epName });
    }

    if (episodesList.length === 0) {
      episodesList.push({ epUrl: detailUrl, epName: "Full" });
    }

    const episodes = episodesList.map((ep, idx) => ({
      id: `ep-${idx + 1}`,
      name: ep.epName,
      slug: `ep-${idx + 1}`,
      number: idx + 1,
      serverName: "CLB Phim Xưa",
      data: {
        epUrl: ep.epUrl,
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
      year,
      type: "single",
      status: "completed",
      quality: "HD",
      language: "Tiếng Việt",
      description,
      actors: [],
      directors: [],
      genres: ["Kinh Điển"],
      countries: ["Việt Nam"],
      episodes: [
        {
          serverName: "CLB Phim Xưa",
          episodes
        }
      ]
    };
  },

  async getStream(episodeData) {
    const data = episodeData.data || {};
    let realEmbed = data.epUrl || data.detailUrl || BASE_URL;

    if (data.epUrl && data.epUrl.includes("clbpx.html?v=")) {
      try {
        const epRes = await fetch(data.epUrl, {
          headers: {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Referer": `${BASE_URL}/`
          }
        });
        if (epRes.ok) {
          const epHtml = await epRes.text();
          const iframeMatch = epHtml.match(/<iframe[^>]*src=["']([^"']+)["']/i);
          if (iframeMatch && iframeMatch[1].startsWith('http')) {
            const iframeSrc = iframeMatch[1];
            if (!iframeSrc.includes('abysscdn.com') && !iframeSrc.includes('hydrax')) {
              realEmbed = iframeSrc;
            }
          }
        }
      } catch (e) {}
    }

    return {
      type: "embed",
      url: realEmbed,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": `${BASE_URL}/`
      },
      subtitles: []
    };
  }
};
