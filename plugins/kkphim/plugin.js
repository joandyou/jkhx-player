// plugins/kkphim/plugin.js
// KKPhim Provider migrated from lib/providers/kkphim.ts

const BASE_URL = "https://phimapi.com";

function buildImageUrl(path, cdnDomain = "https://phimimg.com") {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  const cleanPath = path.replace(/^\/+/, '');
  const cleanCdn = cdnDomain.replace(/\/+$/, '');
  return `${cleanCdn}/${cleanPath}`;
}

async function getMovieImages(slug, fallbackThumb, fallbackPoster) {
  if (!slug) return { thumb_url: fallbackThumb, poster_url: fallbackPoster };
  try {
    const res = await fetch(`${BASE_URL}/v1/api/phim/${slug}/images`);
    const json = await res.json();
    if (json.status_code === 200 && json.data && json.data.images) {
      const images = json.data.images;
      const sizes = json.data.image_sizes;
      const backdrop = images.find(i => i.type === 'backdrop');
      const poster = images.find(i => i.type === 'poster');

      let thumb_url = fallbackThumb;
      let poster_url = fallbackPoster;

      if (backdrop && sizes?.backdrop?.original) {
        thumb_url = `${sizes.backdrop.original}${backdrop.file_path}`;
      }
      if (poster && sizes?.poster?.original) {
        poster_url = `${sizes.poster.original}${poster.file_path}`;
      } else if (backdrop && sizes?.backdrop?.original) {
        poster_url = thumb_url;
      }
      return { thumb_url, poster_url };
    }
  } catch (e) {
    // fallback
  }
  return { thumb_url: fallbackThumb, poster_url: fallbackPoster };
}

async function fetchSection(type, slug, page = 1) {
  try {
    let url = "";
    if (type === 'films' || slug.includes('phim-moi-cap-nhat-v3')) {
      url = `${BASE_URL}/danh-sach/${slug}?page=${page}`;
    } else if (slug === 'phim-moi') {
      url = `${BASE_URL}/v1/api/danh-sach?page=${page}&limit=24`;
    } else {
      const apiPrefix = type === 'danh-sach' ? 'danh-sach' : type;
      url = `${BASE_URL}/v1/api/${apiPrefix}/${slug}?page=${page}&limit=24`;
    }

    const res = await fetch(url);
    const text = await res.text();
    if (text.trim().startsWith('<')) return [];

    const data = JSON.parse(text);
    const items = data.data?.items || data.items || [];
    const cdnDomain = data.data?.APP_DOMAIN_CDN_IMAGE || data.data?.pathImage || data.pathImage || "https://phimimg.com";

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
      providerId: "kkphim"
    }));
  } catch (e) {
    return [];
  }
}

export default {
  id: "kkphim",
  name: "KKPhim",
  version: "1.0.0",

  async getHome() {
    const homeSectionsConfig = [
      { title: "Phim Mới Cập Nhật", type: "danh-sach", slug: "phim-moi" },
      { title: "Phim Hàn Quốc", type: "quoc-gia", slug: "han-quoc" },
      { title: "Phim Chiếu Rạp", type: "danh-sach", slug: "phim-chieu-rap" },
      { title: "Phim Kinh Dị", type: "the-loai", slug: "kinh-di" },
      { title: "Phim Bộ", type: "danh-sach", slug: "phim-bo" },
      { title: "Phim Lẻ", type: "danh-sach", slug: "phim-le" }
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
      if (text.trim().startsWith('<')) return { items: [], page, hasNext: false };

      const data = JSON.parse(text);
      const items = data.data?.items || [];
      const cdnDomain = data.data?.APP_DOMAIN_CDN_IMAGE || data.data?.pathImage || data.pathImage || "https://phimimg.com";

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
        providerId: "kkphim"
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
    const data = await res.json();

    if (!data.status || !data.movie) {
      throw new Error(`KKPhim: Không tìm thấy dữ liệu cho phim ${slug}`);
    }

    const movie = data.movie;
    const cdnDomain = data.pathImage || "https://phimimg.com";
    const oldThumb = buildImageUrl(movie.thumb_url, cdnDomain);
    const oldPoster = buildImageUrl(movie.poster_url || movie.thumb_url, cdnDomain);
    const images = await getMovieImages(slug, oldThumb, oldPoster);

    // CHUẨN KIẾN TRÚC: KHÔNG nhồi playable stream vào đây.
    // Lưu link_m3u8 và link_embed vào EpisodeData.data để getStream(episodeData) xử lý!
    const episodeServers = (data.episodes || []).map((server, sIdx) => ({
      serverName: server.server_name || `Server ${sIdx + 1}`,
      episodes: (server.server_data || []).map((item, eIdx) => ({
        id: item.slug || `ep-${eIdx + 1}`,
        name: item.name || `Tập ${eIdx + 1}`,
        slug: item.slug || `tap-${eIdx + 1}`,
        number: eIdx + 1,
        serverName: server.server_name || "Vietsub",
        data: {
          m3u8: item.link_m3u8 || "",
          embed: item.link_embed || ""
        }
      }))
    }));

    return {
      slug: movie.slug,
      name: movie.name,
      originName: movie.origin_name || movie.name,
      thumbUrl: images.thumb_url,
      posterUrl: images.poster_url,
      backdropUrl: images.thumb_url,
      year: movie.year ? parseInt(movie.year) : null,
      type: movie.type || "series",
      status: movie.status || "ongoing",
      quality: movie.quality || "HD",
      language: movie.lang || "Vietsub",
      duration: movie.time || "",
      currentEpisode: movie.episode_current || "",
      totalEpisodes: movie.episode_total || "",
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
          "Referer": "https://phimapi.com/"
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

    throw new Error(`KKPhim: Không tìm thấy luồng phát cho tập ${episodeData.name}`);
  }
};
