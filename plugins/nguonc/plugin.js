// plugins/nguonc/plugin.js
// NguonC Provider migrated from lib/providers/nguonc.ts

const BASE_URL = "https://phim.nguonc.com/api";

function buildImageUrl(path, cdnDomain = "https://phim.nguonc.com") {
  if (!path) return "";
  if (typeof path === 'object') {
    path = path.url || path.path || path.src || "";
  }
  const strPath = String(path || "").trim();
  if (!strPath || strPath === "[object Object]") return "";
  if (strPath.startsWith("http")) return strPath;
  const cleanPath = strPath.replace(/^\/+/, '');
  const cleanCdn = String(cdnDomain).replace(/\/+$/, '');
  return `${cleanCdn}/${cleanPath}`;
}

function extractYear(dateRaw) {
  if (!dateRaw) return null;
  const match = String(dateRaw).match(/\b(19|20)\d{2}\b/);
  return match ? parseInt(match[0]) : null;
}

function parseMovieItem(item) {
  let categories = [];
  if (item.category) {
    if (typeof item.category === 'object' && !Array.isArray(item.category)) {
      if (item.category['2']?.list) categories = item.category['2'].list.map(c => c.name);
    } else if (Array.isArray(item.category)) {
      categories = item.category.map(c => c.name || c);
    }
  }

  const totalEps = parseInt(item.total_episodes || item.episode_total || "1");
  const currentEp = String(item.current_episode || item.episode_current || "").toLowerCase();
  const movieType = (totalEps > 1 || currentEp.includes("tập") || currentEp.includes("tap")) ? "series" : "single";

  return {
    slug: item.slug,
    name: item.name,
    originName: item.original_name || item.name,
    thumbUrl: buildImageUrl(item.thumb_url),
    posterUrl: buildImageUrl(item.poster_url || item.thumb_url),
    year: extractYear(item.created || item.modified || item.year),
    type: movieType,
    quality: item.quality || "HD",
    language: item.language || item.lang || "Vietsub",
    providerId: "nguonc"
  };
}

async function fetchSection(slug, page = 1) {
  try {
    const url = `${BASE_URL}/films/${slug}?page=${page}`;
    const res = await fetch(url);
    const text = await res.text();
    if (text.trim().startsWith('<')) return [];

    const json = JSON.parse(text);
    const items = json.items || json.data?.items || [];
    return items.map(parseMovieItem);
  } catch (e) {
    return [];
  }
}

export default {
  id: "nguonc",
  name: "NguonC",
  version: "1.0.0",

  async getHome() {
    const homeSectionsConfig = [
      { title: "Phim Mới Cập Nhật", slug: "phim-moi-cap-nhat" },
      { title: "Phim Bộ Mới", slug: "phim-bo" },
      { title: "Phim Lẻ Chọn Lọc", slug: "phim-le" },
      { title: "Phim Hoạt Hình", slug: "hoat-hinh" },
      { title: "Phim Đang Chiếu", slug: "dang-chieu" }
    ];

    const sections = await Promise.all(
      homeSectionsConfig.map(async config => {
        const items = await fetchSection(config.slug, 1);
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
      const url = `${BASE_URL}/films/search?keyword=${encodeURIComponent(keyword)}&page=${page}`;
      const res = await fetch(url);
      const text = await res.text();
      if (text.trim().startsWith('<')) return { items: [], page, hasNext: false };

      const json = JSON.parse(text);
      const items = json.items || json.data?.items || [];
      const movieItems = items.map(parseMovieItem);

      const paginate = json.paginate || {};
      const totalPages = paginate.total_page || 1;

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
    const url = `${BASE_URL}/film/${slug}`;
    const res = await fetch(url);
    const json = await res.json();

    if (json.status !== "success" || !json.movie) {
      throw new Error(`NguonC: Không tìm thấy dữ liệu cho phim ${slug}`);
    }

    const movie = json.movie;
    const year = extractYear(movie.created || movie.modified || movie.year);

    const actors = typeof movie.casts === 'string'
      ? movie.casts.split(',').map(a => a.trim()).filter(Boolean)
      : (Array.isArray(movie.casts) ? movie.casts : []);

    const directors = typeof movie.director === 'string'
      ? movie.director.split(',').map(d => d.trim()).filter(Boolean)
      : (Array.isArray(movie.director) ? movie.director : []);

    const categories = [];
    if (movie.category) {
      if (typeof movie.category === 'object' && !Array.isArray(movie.category)) {
        if (movie.category['2']?.list) categories.push(...movie.category['2'].list.map(c => c.name));
      } else if (Array.isArray(movie.category)) {
        categories.push(...movie.category.map(c => c.name || c));
      }
    }

    const countries = [];
    if (movie.category && movie.category['4']?.list) {
      countries.push(...movie.category['4'].list.map(c => c.name));
    }

    // CHUẨN KIẾN TRÚC: Lưu m3u8 và embed vào data của từng EpisodeData,
    // tuyệt đối không resolve luồng phát tại đây!
    const episodeServers = (movie.episodes || []).map((server, sIdx) => {
      const serverLabel = (server.server_name || `Server ${sIdx + 1}`).replace(/\s+/g, ' ').trim();
      const items = server.items || server.server_data || [];

      return {
        serverName: serverLabel,
        episodes: items.map((item, eIdx) => ({
          id: item.slug || `ep-${eIdx + 1}`,
          name: item.name || `Tập ${eIdx + 1}`,
          slug: item.slug || `tap-${eIdx + 1}`,
          number: eIdx + 1,
          serverName: serverLabel,
          data: {
            m3u8: item.m3u8 || item.link_m3u8 || "",
            embed: item.embed || item.link_embed || ""
          }
        }))
      };
    });

    const totalEps = parseInt(movie.total_episodes || "1");
    const currentEp = String(movie.current_episode || "").toLowerCase();
    const movieType = (totalEps > 1 || currentEp.includes("tập") || currentEp.includes("tap")) ? "series" : "single";

    return {
      slug: movie.slug,
      name: movie.name,
      originName: movie.original_name || movie.name,
      thumbUrl: buildImageUrl(movie.thumb_url),
      posterUrl: buildImageUrl(movie.poster_url || movie.thumb_url),
      backdropUrl: buildImageUrl(movie.poster_url || movie.thumb_url),
      year: year,
      type: movieType,
      status: movie.status || "ongoing",
      quality: movie.quality || "HD",
      language: movie.language || "Vietsub",
      duration: movie.time || "",
      currentEpisode: movie.current_episode || "",
      totalEpisodes: String(movie.total_episodes || ""),
      description: movie.description || movie.content || "",
      actors: actors,
      directors: directors,
      genres: categories,
      countries: countries,
      tmdbId: movie.tmdb_id ? String(movie.tmdb_id) : null,
      imdbId: movie.imdb_id ? String(movie.imdb_id) : null,
      episodes: episodeServers
    };
  },

  async getStream(episodeData) {
    const data = episodeData.data || {};

    // 1. Nếu có M3U8 trực tiếp
    if (data.m3u8) {
      return {
        type: "m3u8",
        url: data.m3u8,
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
          "Referer": "https://phim.nguonc.com/"
        },
        subtitles: []
      };
    }

    // 2. Nếu có Embed link
    if (data.embed) {
      return {
        type: "embed",
        url: data.embed,
        headers: {
          "Referer": "https://phim.nguonc.com/"
        },
        subtitles: []
      };
    }

    throw new Error(`NguonC: Không tìm thấy luồng phát cho tập ${episodeData.name}`);
  }
};

