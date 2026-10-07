// plugins/buncha/plugin.js
// Buncha TV Live Sports Provider migrated from lib/providers/buncha.ts
// NOTE: Domain bunchatv4.net DNS is currently unavailable, kept for migration preservation.

const BASE_URL = "https://bunchatv4.net";

function cleanText(text) {
  return (text || "").replace(/<[^>]+>/g, "").replace(/\s+/g, " ").trim();
}

function parseMatches(html) {
  const matches = [];
  const seen = new Set();

  const matchRegex = /<a[^>]*class=["'][^"']*(?:link-match-full|box-match-hot|box-live-today)[^"']*["'][^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi;
  let match;
  while ((match = matchRegex.exec(html)) !== null) {
    const rawUrl = match[1];
    const block = match[2];

    const slug = rawUrl.replace(BASE_URL, "").replace(/^\/+|\/+$/g, "");
    if (!slug || seen.has(slug)) continue;
    seen.add(slug);

    const homeTeamMatch = block.match(/class=["'][^"']*name-club[^"']*["'][^>]*>([\s\S]*?)<\/span>/i);
    const title = cleanText(homeTeamMatch ? homeTeamMatch[1] : slug);

    const logoMatch = block.match(/<img[^>]*src=["']([^"']+)["']/i);
    let logo = logoMatch ? logoMatch[1] : "";
    if (logo && !logo.startsWith("http")) logo = `${BASE_URL}${logo.startsWith('/') ? '' : '/'}${logo}`;

    matches.push({
      slug,
      name: title || "Trận Đấu Trực Tiếp",
      originName: "Trực Tiếp Bóng Đá",
      thumbUrl: logo,
      posterUrl: logo,
      year: new Date().getFullYear(),
      type: "live",
      quality: "Live",
      language: "Bình Luận TV",
      providerId: "bunchatv"
    });
  }

  return matches;
}

export default {
  id: "bunchatv",
  name: "Buncha TV",
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
      const items = parseMatches(html);

      return {
        sections: [
          {
            title: "🔥 Trực Tiếp Hôm Nay",
            items: items.slice(0, 10)
          },
          {
            title: "🌟 Trận Cầu Tâm Điểm",
            items: items.slice(10, 20)
          }
        ]
      };
    } catch (e) {
      return { sections: [] };
    }
  },

  async search(keyword, page = 1) {
    try {
      const home = await this.getHome();
      const q = (keyword || "").toLowerCase();
      const all = home.sections.flatMap(s => s.items);
      const filtered = all.filter(m => m.name.toLowerCase().includes(q));

      return {
        items: filtered,
        page: 1,
        hasNext: false
      };
    } catch (e) {
      return { items: [], page: 1, hasNext: false };
    }
  },

  async getDetail(slug) {
    try {
      const detailUrl = `${BASE_URL}/${slug.replace(/^\/+|\/+$/g, '')}`;
      const res = await fetch(detailUrl, {
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
          "Referer": `${BASE_URL}/`
        }
      });
      const html = await res.text();

      const titleMatch = html.match(/<h1[^>]*>([\s\S]*?)<\/h1>/i);
      const title = titleMatch ? cleanText(titleMatch[1]) : "Trực Tiếp Bóng Đá";

      const episodes = [];
      const streamRegex = /data-fileurl=["']([^"']+\.m3u8[^"']*)["'][^>]*>([\s\S]*?)<\/div>/gi;
      let sMatch;
      let idx = 1;
      while ((sMatch = streamRegex.exec(html)) !== null) {
        let fileUrl = sMatch[1];
        if (fileUrl.startsWith('//')) fileUrl = `https:${fileUrl}`;

        const nameMatch = sMatch[2].match(/class=["'][^"']*name-stream[^"']*["'][^>]*>([\s\S]*?)<\/span>/i);
        const sName = cleanText(nameMatch ? nameMatch[1] : `Luồng #${idx}`);

        episodes.push({
          id: `stream-${idx}`,
          name: sName,
          slug: `stream-${idx}`,
          number: idx,
          serverName: "Buncha TV",
          data: {
            m3u8: fileUrl,
            detailUrl
          }
        });
        idx++;
      }

      if (episodes.length === 0) {
        episodes.push({
          id: "live-1",
          name: "Luồng Trực Tiếp",
          slug: "live-1",
          number: 1,
          serverName: "Buncha TV",
          data: {
            m3u8: "",
            embed: detailUrl,
            detailUrl
          }
        });
      }

      return {
        slug,
        name: title,
        originName: "Trực Tiếp Bóng Đá",
        thumbUrl: "",
        posterUrl: "",
        backdropUrl: "",
        year: new Date().getFullYear(),
        type: "live",
        status: "ongoing",
        quality: "Full HD",
        language: "Tiếng Việt",
        description: `Xem trực tiếp ${title} trên Buncha TV.`,
        actors: [],
        directors: [],
        genres: ["Thể Thao", "Trực Tiếp"],
        countries: ["Quốc Tế"],
        episodes: [
          {
            serverName: "Buncha Live Stream",
            episodes
          }
        ]
      };
    } catch (e) {
      throw new Error(`Buncha TV: Máy chủ không phản hồi hoặc tên miền ${BASE_URL} đã hết hạn.`);
    }
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
      url: data.embed || data.detailUrl || BASE_URL,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": `${BASE_URL}/`
      },
      subtitles: []
    };
  }
};
