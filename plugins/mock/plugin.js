// plugins/mock/plugin.js
// Mock Plugin demonstrating full contract: getHome, search, getDetail, getStream

const MOCK_MOVIES = [
  {
    slug: "big-buck-bunny",
    name: "Big Buck Bunny",
    originName: "Big Buck Bunny (2008)",
    thumbUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
    posterUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
    backdropUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
    year: 2008,
    type: "single",
    quality: "FHD",
    language: "Tiếng Anh",
    duration: "10 phút",
    description: "Một chú thỏ khổng lồ tốt bụng quyết định trả đũa ba kẻ bắt nạt trong rừng.",
    genres: ["Hoạt hình", "Hài hước"],
    countries: ["Hà Lan"],
    streamUrl: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
  },
  {
    slug: "sintel",
    name: "Sintel",
    originName: "Sintel - The Durian Open Movie",
    thumbUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
    posterUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
    backdropUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
    year: 2010,
    type: "single",
    quality: "FHD",
    language: "Tiếng Anh",
    duration: "15 phút",
    description: "Hành trình cô gái trẻ Sintel tìm kiếm chú rồng nhỏ bị bắt cóc.",
    genres: ["Hoạt hình", "Phiêu lưu"],
    countries: ["Hà Lan"],
    streamUrl: "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"
  },
  {
    slug: "tears-of-steel",
    name: "Tears of Steel",
    originName: "Tears of Steel (2012)",
    thumbUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
    posterUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
    backdropUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
    year: 2012,
    type: "single",
    quality: "4K",
    language: "Tiếng Anh",
    duration: "12 phút",
    description: "Thế giới hậu tận thế nơi con người chiến đấu chống lại robot hủy diệt.",
    genres: ["Khoa học viễn tưởng", "Hành động"],
    countries: ["Hà Lan"],
    streamUrl: "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"
  }
];

export default {
  id: "mock",
  name: "Mock Provider",
  version: "1.0.0",

  async getHome() {
    return {
      sections: [
        {
          title: "Phim Hoạt Hình Kinh Điển",
          items: MOCK_MOVIES.slice(0, 2).map(m => ({
            slug: m.slug,
            name: m.name,
            originName: m.originName,
            thumbUrl: m.thumbUrl,
            posterUrl: m.posterUrl,
            year: m.year,
            type: m.type,
            quality: m.quality,
            language: m.language,
            providerId: "mock"
          }))
        },
        {
          title: "Phim Khoa Học Viễn Tưởng",
          items: [MOCK_MOVIES[2]].map(m => ({
            slug: m.slug,
            name: m.name,
            originName: m.originName,
            thumbUrl: m.thumbUrl,
            posterUrl: m.posterUrl,
            year: m.year,
            type: m.type,
            quality: m.quality,
            language: m.language,
            providerId: "mock"
          }))
        }
      ]
    };
  },

  async search(keyword, page = 1) {
    const q = (keyword || "").toLowerCase();
    const filtered = MOCK_MOVIES.filter(m =>
      m.name.toLowerCase().includes(q) || m.originName.toLowerCase().includes(q)
    );

    return {
      items: filtered.map(m => ({
        slug: m.slug,
        name: m.name,
        originName: m.originName,
        thumbUrl: m.thumbUrl,
        posterUrl: m.posterUrl,
        year: m.year,
        type: m.type,
        quality: m.quality,
        language: m.language,
        providerId: "mock"
      })),
      page: page,
      hasNext: false
    };
  },

  async getDetail(slug) {
    const movie = MOCK_MOVIES.find(m => m.slug === slug) || MOCK_MOVIES[0];

    // QUAN TRỌNG: KHÔNG trả stream url playable trực tiếp ở đây!
    // Trả episodes chứa EpisodeData với data payload.
    return {
      slug: movie.slug,
      name: movie.name,
      originName: movie.originName,
      thumbUrl: movie.thumbUrl,
      posterUrl: movie.posterUrl,
      backdropUrl: movie.backdropUrl,
      year: movie.year,
      type: movie.type,
      status: "completed",
      quality: movie.quality,
      language: movie.language,
      duration: movie.duration,
      description: movie.description,
      actors: ["Open Movie Project"],
      directors: ["Colin Levy"],
      genres: movie.genres,
      countries: movie.countries,
      rating: 8.5,
      episodes: [
        {
          serverName: "Server M3U8 VIP",
          episodes: [
            {
              id: "ep-full",
              name: "Full HD",
              slug: "full",
              number: 1,
              serverName: "Server M3U8 VIP",
              data: {
                streamUrl: movie.streamUrl,
                type: "m3u8"
              }
            }
          ]
        },
        {
          serverName: "Server MP4 Backup",
          episodes: [
            {
              id: "ep-mp4",
              name: "Bản MP4",
              slug: "mp4",
              number: 1,
              serverName: "Server MP4 Backup",
              data: {
                streamUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                type: "mp4"
              }
            }
          ]
        }
      ]
    };
  },

  async getStream(episodeData) {
    // Trích xuất từ episodeData.data
    const data = episodeData.data || {};
    const streamType = data.type || "m3u8";
    const streamUrl = data.streamUrl || "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";

    return {
      type: streamType,
      url: streamUrl,
      headers: {
        "User-Agent": "JKHXPlayer/1.0",
        "Referer": "https://test.jkhx.cc"
      },
      subtitles: [
        {
          label: "Tiếng Anh (Default)",
          language: "en",
          url: "https://bitdash-a.akamaihd.net/content/sintel/subtitles/subtitles_en.vtt",
          format: "vtt"
        }
      ]
    };
  }
};

