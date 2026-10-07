// plugins/ophim/plugin.js
// OPhim Provider migrated from lib/providers/ophim.ts
// NOTE: Domain ophim1.com is currently offline / returning 404, kept for migration preservation.

const BASE_URL = "https://ophim1.com";

function buildImageUrl(path, cdnDomain = "https://img.ophim.live/uploads/movies") {
  if (!path) return "";
  if (typeof path === 'object') {
    path = path.url || path.path || path.src || "";
  }
  let strPath = String(path || "").trim();
  if (!strPath || strPath === "[object Object]") return "";

  if (strPath.startsWith("http")) return strPath;
  const cleanPath = strPath.replace(/^\/+/, '');
  if (!cleanPath.includes("uploads/")) {
    return `https://img.ophim.live/uploads/movies/${cleanPath}`;
  }
  const cleanCdn = String(cdnDomain || "https://img.ophim.live").replace(/\/+$/, '');
  return `${cleanCdn}/${cleanPath}`;
}

async function fetchSection(type, slug, page = 1) {
  try {
    let url = "";
    if (slug === 'phim-moi-cap-nhat') {
      url = `${BASE_URL}/danh-sach/${slug}?page=${page}`;
    } else {
      url = `${BASE_URL}/v1/api/danh-sach/${slug}?page=${page}&limit=24`;
    }

    const res = await fetch(url);
    const text = await res.text();
    if (!text || text.trim().startsWith('<')) return [];

    const data = JSON.parse(text);
    const items = data.data?.items || data.items || [];
    const cdnDomain = data.data?.APP_DOMAIN_CDN_IMAGE || data.data?.pathImage || data.pathImage || "https://img.ophim.live/uploads/movies";

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
      providerId: "ophim"
    }));
  } catch (e) {
    return [];
  }
}

export default {
  id: "ophim",
  name: "OPhim",
  version: "1.0.0",

  async getHome() {
    const homeSectionsConfig = [
      { title: "Phim Mới Cập Nhật", type: "danh-sach", slug: "phim-moi-cap-nhat" },
      { title: "Phim Bộ Đang Chiếu", type: "danh-sach", slug: "phim-bo" },
      { title: "Phim Lẻ Chọn Lọc", type: "danh-sach", slug: "phim-le" },
      { title: "Phim Hoạt Hình", type: "danh-sach", slug: "hoat-hinh" }
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
      const url = `${BASE_URL}/v1/api/tim-kiem?keyword=${encodeURIComponent(keyword)}&page=${page}&limit=24`;
      const res = await fetch(url);
      const text = await res.text();
      if (!text || text.trim().startsWith('<')) return { items: [], page, hasNext: false };

      const data = JSON.parse(text);
      const items = data.data?.items || data.items || [];
      const cdnDomain = data.data?.APP_DOMAIN_CDN_IMAGE || data.data?.pathImage || data.pathImage || "https://img.ophim.live/uploads/movies";

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
        providerId: "ophim"
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
    const url = `${BASE_URL}/v1/api/phim/${slug}`;
    const res = await fetch(url);
    const text = await res.text();
    if (!text || text.trim().startsWith('<')) {
      throw new Error(`OPhim: Máy chủ không phản hồi hoặc domain thay đổi (404/HTML) cho slug '${slug}'`);
    }

    const json = JSON.parse(text);
    const data = json.data || json;
    if (!json.status || (!data.item && !data.movie)) {
      throw new Error(`OPhim: Không tìm thấy dữ liệu cho phim ${slug}`);
    }

    const movie = data.item || data.movie;
    const cdnDomain = data.APP_DOMAIN_CDN_IMAGE || data.pathImage || "https://img.ophim.live/uploads/movies";
    const thumbUrl = buildImageUrl(movie.thumb_url, cdnDomain);
    const posterUrl = buildImageUrl(movie.poster_url || movie.thumb_url, cdnDomain);

    const episodeServers = (movie.episodes || data.episodes || []).map((server, sIdx) => ({
      serverName: server.server_name || `Server ${sIdx + 1}`,
      episodes: (server.server_data || server.items || []).map((item, eIdx) => ({
        id: item.slug || `ep-${eIdx + 1}`,
        name: item.name || `Tập ${eIdx + 1}`,
        slug: item.slug || `tap-${eIdx + 1}`,
        number: eIdx + 1,
        serverName: server.server_name || "Vietsub",
        data: {
          m3u8: item.link_m3u8 || item.m3u8 || "",
          embed: item.link_embed || item.embed || ""
        }
      }))
    }));

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
    if (data.m3u8) {
      return {
        type: "m3u8",
        url: data.m3u8,
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
          "Referer": "https://ophim1.com/"
        },
        subtitles: []
      };
    }

    if (data.embed) {
      return {
        type: "embed",
        url: data.embed,
        headers: {},
        subtitles: []
      };
    }

    throw new Error(`OPhim: Không tìm thấy luồng phát cho tập ${episodeData.name}`);
  }
};
