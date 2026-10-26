/**
 * Template Provider - JKHX Player Plugin
 *
 * Mẫu plugin chuẩn mực (Reference Implementation) chạy trên QuickJS-NG Sandbox.
 * Plugin giao tiếp với Native Core thông qua 6 phương thức chuẩn.
 */

const defaultHeaders = {
  "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36",
  "Accept": "application/json, text/plain, */*"
};

export default {
  /**
   * 1. GET HOME FEED
   * Cung cấp các hàng phim phân loại cho màn hình chính (HomeScreen).
   */
  async getHome() {
    return {
      sections: [
        {
          title: "Phim Nguồn Mở Nổi Bật",
          type: "danh-sach",
          slug: "open-source-movies",
          items: [
            {
              slug: "big-buck-bunny",
              name: "Big Buck Bunny (2008)",
              thumbUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
              posterUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
              year: 2008,
              quality: "4K UHD",
              episodeCurrent: "Full",
              type: "single"
            },
            {
              slug: "sintel",
              name: "Sintel (2010)",
              thumbUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
              posterUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
              year: 2010,
              quality: "1080p",
              episodeCurrent: "Full",
              type: "single"
            },
            {
              slug: "tears-of-steel",
              name: "Tears of Steel (2012)",
              thumbUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
              posterUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
              year: 2012,
              quality: "4K UHD",
              episodeCurrent: "Full",
              type: "single"
            }
          ]
        }
      ],
      quickCategories: [
        { title: "Phim Hoạt Hình", type: "the-loai", slug: "animation" },
        { title: "Phim Khoa Học Viễn Tưởng", type: "the-loai", slug: "sci-fi" }
      ]
    };
  },

  /**
   * 2. GET MENU
   * Cung cấp danh mục trên thanh điều hướng đầu trang HomeScreen.
   */
  async getMenu() {
    return [
      {
        title: "Trang Chủ",
        isDropdown: false,
        type: "home",
        slug: "home"
      },
      {
        title: "Phim Hoạt Hình",
        isDropdown: false,
        type: "the-loai",
        slug: "animation"
      },
      {
        title: "Thể Loại Khác",
        isDropdown: true,
        items: [
          { title: "Khoa Học Viễn Tưởng", slug: "sci-fi" },
          { title: "Phiêu Lưu", slug: "adventure" }
        ]
      }
    ];
  },

  /**
   * 3. GET LIST
   * Trả về danh sách phim theo danh mục / thể loại có phân trang cho MovieListScreen.
   */
  async getList(type, slug, page = 1, filters = null) {
    return {
      items: [
        {
          slug: "big-buck-bunny",
          name: "Big Buck Bunny (2008)",
          thumbUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
          posterUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
          year: 2008,
          quality: "4K UHD",
          episodeCurrent: "Full",
          type: "single"
        }
      ],
      page: page,
      hasNext: false
    };
  },

  /**
   * 4. SEARCH
   * Tìm kiếm phim theo từ khóa.
   */
  async search(keyword, page = 1) {
    const q = (keyword || "").toLowerCase();
    const demoItems = [
      {
        slug: "big-buck-bunny",
        name: "Big Buck Bunny (2008)",
        thumbUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
        posterUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
        year: 2008,
        quality: "4K UHD",
        episodeCurrent: "Full",
        type: "single"
      }
    ];
    const filtered = demoItems.filter(item => item.name.toLowerCase().includes(q));
    return {
      items: filtered,
      page: page,
      hasNext: false
    };
  },

  /**
   * 5. GET DETAIL
   * Trả về thông tin chi tiết phim, danh sách server và tập/chương cho DetailScreen.
   */
  async getDetail(slug) {
    return {
      movie: {
        slug: slug,
        name: "Big Buck Bunny (2008)",
        originName: "Big Buck Bunny",
        content: "Một câu chuyện hài hước về chú thỏ khổng lồ tốt bụng và ba sinh vật rừng tinh nghịch. Bộ phim được sản xuất bởi Blender Foundation dưới giấy phép Creative Commons.",
        thumbUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
        posterUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
        year: 2008,
        quality: "4K UHD",
        time: "10 phút",
        director: "Sacha Goedegebure",
        actors: ["Big Buck Bunny", "Frank", "Rinky", "Gamera"],
        categories: ["Hoạt hình", "Hài hước", "Nguồn mở"],
        countries: ["Hà Lan"],
        episodeCurrent: "Full",
        type: "single",
        servers: [
          {
            serverName: "HLS Stream (Tự thích ứng)",
            episodes: [
              {
                id: "bbb-hls-full",
                name: "Full HD",
                slug: "full"
              }
            ]
          },
          {
            serverName: "MP4 Direct (Direct CDN)",
            episodes: [
              {
                id: "bbb-mp4-full",
                name: "Full 1080p",
                slug: "full-mp4"
              }
            ]
          }
        ]
      }
    };
  },

  /**
   * 6. GET STREAM
   * Bóc tách và trả về luồng phát video cho ExoPlayer.
   */
  async getStream(episodeId, serverId) {
    if (episodeId === "bbb-mp4-full") {
      return {
        type: "mp4",
        url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
        headers: defaultHeaders
      };
    }

    // Mặc định trả về luồng phát HLS m3u8
    return {
      type: "hls",
      url: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
      headers: defaultHeaders
    };
  }
};

