// plugins/vsmov/plugin.js
// VSMov Provider migrated from lib/providers/vsmov.ts

const BASE_URL = "https://vsmov.com/api";

function buildImageUrl(path, cdnDomain = "https://vsmov.com") {
  if (!path) return "";
  if (typeof path === 'object') {
    path = path.url || path.path || path.src || "";
  }
  const strPath = String(path || "").trim();
  if (!strPath || strPath === "[object Object]") return "";
  if (strPath.startsWith("http")) return strPath;
  const cleanPath = strPath.replace(/^\/+/, '');
  const cleanCdn = String(cdnDomain || "https://vsmov.com").replace(/\/+$/, '');
  return `${cleanCdn}/${cleanPath}`;
}

async function extractVsmovStream(embedUrl) {
  if (!embedUrl) return { m3u8: "", subtitles: [] };

  if (embedUrl.startsWith("http://")) {
    embedUrl = embedUrl.replace("http://", "https://");
  }

  if (embedUrl.toLowerCase().includes(".m3u8")) {
    return { m3u8: embedUrl, subtitles: [] };
  }

  let origin = "https://vsmov.com";
  let fallbackM3u8 = "";
  let fallbackSub = "";
  try {
    const urlObj = new URL(embedUrl);
    origin = urlObj.origin;
    const hashMatch = urlObj.pathname.match(/\/video\/([a-zA-Z0-9_-]+)/);
    if (hashMatch) {
      if (origin.includes("sv2")) {
        fallbackM3u8 = `${origin}/video/${hashMatch[1]}/master-b2.m3u8`;
        fallbackSub = `${origin}/video/${hashMatch[1]}/subtitle.vtt`;
      } else {
        fallbackM3u8 = `${origin}/stream/${hashMatch[1]}/master.m3u8`;
      }
    }
  } catch (e) {
    // ignore
  }

  try {
    const res = await fetch(embedUrl, {
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer": "https://vsmov.com/"
      }
    });

    if (!res || !res.ok) {
      return {
        m3u8: fallbackM3u8,
        subtitles: fallbackSub ? [{ label: "Vietsub", language: "vi", url: fallbackSub, format: "vtt" }] : []
      };
    }

    const html = await res.text();
    let m3u8Url = "";
    const subtitles = [];

    // 1. Regex tìm link m3u8
    const m3u8Match = html.match(/(?:file|source|src|url)\s*[:=]\s*["']([^"']+\.m3u8[^"']*)["']/i);
    if (m3u8Match) {
      m3u8Url = m3u8Match[1];
    } else {
      const anyM3u8 = html.match(/https?:\/\/[^"'\s<>]+\.m3u8[^"'\s<>]*/i);
      if (anyM3u8) m3u8Url = anyM3u8[0];
    }

    // 2. Regex tìm phụ đề WebVTT
    const subMatches = [...html.matchAll(/(?:file|src|url)\s*[:=]\s*["']([^"']+\.vtt[^"']*)["'](?:\s*,\s*label\s*[:=]\s*["']([^"']+)["'])?/gi)];
    for (const match of subMatches) {
      const subUrl = match[1];
      const subLabel = match[2] || "Vietsub";
      subtitles.push({
        label: subLabel,
        language: "vi",
        url: subUrl.startsWith("http") ? subUrl : `${origin}${subUrl.startsWith("/") ? "" : "/"}${subUrl}`,
        format: "vtt"
      });
    }

    if (!m3u8Url) m3u8Url = fallbackM3u8;

    return { m3u8: m3u8Url, subtitles };
  } catch (e) {
    return {
      m3u8: fallbackM3u8,
      subtitles: fallbackSub ? [{ label: "Vietsub", language: "vi", url: fallbackSub, format: "vtt" }] : []
    };
  }
}

async function fetchSection(type, slug, page = 1) {
  try {
    let url = "";
    if (type === 'films' || slug.includes('phim-moi-cap-nhat')) {
      url = `${BASE_URL}/danh-sach/phim-moi-cap-nhat?page=${page}`;
    } else {
      const apiPrefix = type === 'danh-sach' ? 'danh-sach' : type;
      url = `${BASE_URL}/${apiPrefix}/${slug}?page=${page}&limit=24`;
    }

    const res = await fetch(url);
    const text = await res.text();
    if (text.trim().startsWith('<')) return [];

    const data = JSON.parse(text);
    const items = data.data?.items || data.items || [];
    const cdnDomain = data.data?.APP_DOMAIN_CDN_IMAGE || data.data?.pathImage || data.pathImage || "https://vsmov.com";

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
      providerId: "vsmov"
    }));
  } catch (e) {
    return [];
  }
}

export default {
  id: "vsmov",
  name: "VSMov",
  version: "1.0.0",

  async getHome() {
    const homeSectionsConfig = [
      { title: "Phim Mới Cập Nhật", type: "danh-sach", slug: "phim-moi-cap-nhat" },
      { title: "Phim Bộ Mới", type: "danh-sach", slug: "phim-bo" },
      { title: "Phim Lẻ Chọn Lọc", type: "danh-sach", slug: "phim-le" },
      { title: "Phim Chiếu Rạp", type: "danh-sach", slug: "phim-chieu-rap" }
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
      const cdnDomain = data.data?.APP_DOMAIN_CDN_IMAGE || data.data?.pathImage || data.pathImage || "https://vsmov.com";

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
        providerId: "vsmov"
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
      throw new Error(`VSMov Detail: Trả về HTML thay vì JSON cho slug "${slug}"`);
    }

    const data = JSON.parse(text);
    if (!data.status || !data.movie) {
      throw new Error(`VSMov: Không tìm thấy dữ liệu cho phim ${slug}`);
    }

    const movie = data.movie;
    const cdnDomain = data.pathImage || "https://vsmov.com";
    const thumbUrl = buildImageUrl(movie.thumb_url, cdnDomain);
    const posterUrl = buildImageUrl(movie.poster_url || movie.thumb_url, cdnDomain);

    // CHUẨN KIẾN TRÚC: Lưu embed/m3u8 vào data của từng EpisodeData
    // Không bóc tách luồng phát (asyncPool) trong getDetail nữa!
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
          "Referer": "https://vsmov.com/"
        },
        subtitles: []
      };
    }

    if (data.embed) {
      // Bóc tách M3U8 và Subtitles từ embed
      const extracted = await extractVsmovStream(data.embed);
      if (extracted.m3u8) {
        return {
          type: "m3u8",
          url: extracted.m3u8,
          headers: {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Referer": "https://vsmov.com/"
          },
          subtitles: extracted.subtitles || []
        };
      }

      return {
        type: "embed",
        url: data.embed,
        headers: {},
        subtitles: []
      };
    }

    throw new Error(`VSMov: Không tìm thấy luồng phát cho tập ${episodeData.name}`);
  }
};
