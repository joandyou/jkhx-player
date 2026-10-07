// plugins/vsnguon/plugin.js
// VSxF (VSPhim) Provider migrated from lib/providers/vsnguon.ts

const BASE_URL = "https://nguon.vsphim.com/api";

function buildImageUrl(path, cdnDomain = "https://nguon.vsphim.com") {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  const cleanPath = path.replace(/^\/+/, '');
  const cleanCdn = cdnDomain.replace(/\/+$/, '');
  return `${cleanCdn}/${cleanPath}`;
}

async function extractVsphimStream(embedUrl) {
  if (!embedUrl) return { m3u8: "", subtitles: [] };

  if (embedUrl.startsWith("http://")) {
    embedUrl = embedUrl.replace("http://", "https://");
  }

  if (embedUrl.toLowerCase().includes(".m3u8")) {
    return { m3u8: embedUrl, subtitles: [] };
  }

  let origin = "https://nguon.vsphim.com";
  let fallbackM3u8 = "";
  try {
    const urlObj = new URL(embedUrl);
    origin = urlObj.origin;
    const hashMatch = urlObj.pathname.match(/\/video\/([a-zA-Z0-9_-]+)/);
    if (hashMatch) {
      fallbackM3u8 = `${origin}/stream/${hashMatch[1]}/master.m3u8`;
    }
  } catch (e) {
    // ignore
  }

  try {
    const res = await fetch(embedUrl, {
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": "https://nguon.vsphim.com/"
      }
    });

    if (!res || !res.ok) {
      return { m3u8: fallbackM3u8, subtitles: [] };
    }

    const html = await res.text();
    let m3u8Url = "";
    const m3u8Match = html.match(/(?:file|source|src|url)\s*[:=]\s*["']([^"']+\.m3u8[^"']*)["']/i);
    if (m3u8Match) {
      m3u8Url = m3u8Match[1];
    } else {
      const anyM3u8 = html.match(/https?:\/\/[^"'\s<>]+\.m3u8[^"'\s<>]*/i);
      if (anyM3u8) m3u8Url = anyM3u8[0];
    }

    return { m3u8: m3u8Url || fallbackM3u8, subtitles: [] };
  } catch (e) {
    return { m3u8: fallbackM3u8, subtitles: [] };
  }
}

async function fetchSection(type, slug, page = 1) {
  try {
    let url = "";
    if (type === 'films' || slug === 'phim-moi-cap-nhat') {
      url = `${BASE_URL}/danh-sach/phim-moi-cap-nhat?page=${page}&limit=24`;
    } else if (slug === 'phim-bo' || slug === 'phim-le') {
      const filmType = slug === 'phim-bo' ? 'series' : 'single';
      url = `${BASE_URL}/danh-sach?type=${filmType}&page=${page}&limit=24`;
    } else {
      const apiPrefix = type === 'danh-sach' ? 'danh-sach' : type;
      url = `${BASE_URL}/${apiPrefix}/${slug}?page=${page}&limit=24`;
    }

    const res = await fetch(url);
    const text = await res.text();
    if (text.trim().startsWith('<')) return [];

    const data = JSON.parse(text);
    const items = data.data?.items || data.items || [];
    const cdnDomain = data.data?.APP_DOMAIN_CDN_IMAGE || data.data?.pathImage || data.pathImage || "https://nguon.vsphim.com";

    return items.map(item => ({
      slug: item.slug,
      name: item.name,
      originName: item.origin_name,
      thumbUrl: buildImageUrl(item.thumb_url, cdnDomain),
      posterUrl: buildImageUrl(item.poster_url || item.thumb_url, cdnDomain),
      year: item.year ? parseInt(item.year) : null,
      type: item.type || "single",
      quality: item.quality || "HD",
      language: item.lang || "Vietsub",
      providerId: "vsnguon"
    }));
  } catch (e) {
    return [];
  }
}

export default {
  id: "vsnguon",
  name: "VSxF",
  version: "1.0.0",

  async getHome() {
    const homeSectionsConfig = [
      { title: "Phim Mới Cập Nhật", type: "films", slug: "phim-moi-cap-nhat" },
      { title: "Phim Bộ Mới", type: "danh-sach", slug: "phim-bo" },
      { title: "Phim Lẻ Hay", type: "danh-sach", slug: "phim-le" },
      { title: "Phim Nhật Bản", type: "quoc-gia", slug: "nhat-ban" }
    ];

    const sections = await Promise.all(
      homeSectionsConfig.map(async config => {
        const items = await fetchSection(config.type, config.slug, 1);
        return {
          title: config.title,
          items: items
        };
      })
    );

    return {
      sections: sections.filter(s => s.items.length > 0)
    };
  },

  async search(keyword, page = 1) {
    try {
      const url = `${BASE_URL}/tim-kiem?keyword=${encodeURIComponent(keyword)}&page=${page}&limit=24`;
      const res = await fetch(url);
      const text = await res.text();
      if (text.trim().startsWith('<')) return { items: [], page, hasNext: false };

      const data = JSON.parse(text);
      const items = data.data?.items || data.items || [];
      const cdnDomain = data.data?.APP_DOMAIN_CDN_IMAGE || data.data?.pathImage || data.pathImage || "https://nguon.vsphim.com";

      const movieItems = items.map(item => ({
        slug: item.slug,
        name: item.name,
        originName: item.origin_name,
        thumbUrl: buildImageUrl(item.thumb_url, cdnDomain),
        posterUrl: buildImageUrl(item.poster_url || item.thumb_url, cdnDomain),
        year: item.year ? parseInt(item.year) : null,
        type: item.type || "single",
        quality: item.quality || "HD",
        language: item.lang || "Vietsub",
        providerId: "vsnguon"
      }));

      const totalItems = data.data?.params?.pagination?.totalItems || 0;
      const totalPages = Math.ceil(totalItems / 24);

      return {
        items: movieItems,
        page: page,
        hasNext: page < totalPages
      };
    } catch (e) {
      return { items: [], page, hasNext: false };
    }
  },

  async getDetail(slug) {
    const url = `${BASE_URL}/phim/${slug}`;
    const res = await fetch(url);
    const text = await res.text();
    if (text.trim().startsWith('<')) {
      throw new Error(`VSxF Detail: Trả về HTML thay vì JSON cho slug "${slug}"`);
    }

    const data = JSON.parse(text);
    if (!data.status || !data.movie) {
      throw new Error(`VSxF: Không tìm thấy dữ liệu cho phim ${slug}`);
    }

    const movie = data.movie;
    const cdnDomain = data.pathImage || "https://nguon.vsphim.com";
    const thumbUrl = buildImageUrl(movie.thumb_url, cdnDomain);
    const posterUrl = buildImageUrl(movie.poster_url || movie.thumb_url, cdnDomain);

    const episodeServers = (data.episodes || []).map((server, sIdx) => {
      const serverLabel = (server.server_name || `Server ${sIdx + 1}`).replace(/\s+/g, ' ').trim();
      const items = server.server_data || server.items || [];

      return {
        serverName: serverLabel,
        episodes: items.map((item, eIdx) => ({
          id: item.slug || `ep-${eIdx + 1}`,
          name: item.name || `Tập ${eIdx + 1}`,
          slug: item.slug || `tap-${eIdx + 1}`,
          number: eIdx + 1,
          serverName: serverLabel,
          data: {
            embed: item.link_embed || item.embed || "",
            m3u8: item.link_m3u8 || item.m3u8 || ""
          }
        }))
      };
    });

    return {
      slug: movie.slug,
      name: movie.name,
      originName: movie.origin_name || movie.name,
      thumbUrl: thumbUrl,
      posterUrl: posterUrl,
      backdropUrl: thumbUrl,
      year: movie.year ? parseInt(movie.year) : null,
      type: movie.type || "series",
      status: movie.status || "ongoing",
      quality: movie.quality || "HD",
      language: movie.lang || "Vietsub",
      duration: movie.time || "",
      currentEpisode: movie.episode_current || "",
      totalEpisodes: String(movie.episode_total || ""),
      description: movie.content ? movie.content.replace(/<[^>]+>/g, '').trim() : "",
      actors: movie.actor || [],
      directors: movie.director || [],
      genres: (movie.category || []).map(c => c.name),
      countries: (movie.country || []).map(c => c.name),
      rating: movie.tmdb?.vote_average || null,
      tmdbId: movie.tmdb?.id ? String(movie.tmdb.id) : null,
      imdbId: movie.imdb?.id ? String(movie.imdb.id) : null,
      episodes: episodeServers
    };
  },

  async getStream(episodeData) {
    const data = episodeData.data || {};

    if (data.m3u8 && data.m3u8.includes(".m3u8")) {
      return {
        type: "m3u8",
        url: data.m3u8,
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
          "Referer": "https://nguon.vsphim.com/"
        },
        subtitles: []
      };
    }

    if (data.embed) {
      const extracted = await extractVsphimStream(data.embed);
      if (extracted.m3u8) {
        return {
          type: "m3u8",
          url: extracted.m3u8,
          headers: {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Referer": "https://nguon.vsphim.com/"
          },
          subtitles: []
        };
      }

      return {
        type: "embed",
        url: data.embed,
        headers: {},
        subtitles: []
      };
    }

    throw new Error(`VSxF: Không tìm thấy luồng phát cho tập ${episodeData.name}`);
  }
};

