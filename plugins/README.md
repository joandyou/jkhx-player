# JKHX Player Plugin System

Hệ thống tiện ích mở rộng (Plugin System) của **JKHX Player** được xây dựng trên nền tảng **QuickJS-NG WebAssembly / NDK Engine**.

---

## 1. Cấu Trúc Thư Mục Một Plugin

Mỗi plugin là một module độc lập gồm 2 file tối thiểu:

```
plugins/
└── <tên-plugin>/
    ├── manifest.json   # Khai báo thông tin mô tả, tác giả, loại nội dung
    └── plugin.js       # Code ES Module triển khai 6 hàm chuẩn interface
```

---

## 2. Interface Chuẩn Của Plugin

Mỗi `plugin.js` export một object mặc định chứa 6 phương thức:

| Phương thức | Tham số | Mô tả |
|---|---|---|
| `getHome()` | Không | Cung cấp danh sách các hàng phim / danh mục cho màn hình chính |
| `getMenu()` | Không | Cung cấp các mục menu điều hướng trên cùng |
| `getList(type, slug, page, filters)` | `type`, `slug`, `page`, `filters` | Trả về danh sách phim theo danh mục có phân trang |
| `search(keyword, page)` | `keyword`, `page` | Tìm kiếm nội dung theo từ khóa |
| `getDetail(slug)` | `slug` | Trả về thông tin chi tiết phim, danh sách server và tập |
| `getStream(episodeId, serverId)` | `episodeId`, `serverId` | Bóc tách và trả về luồng phát video (`hls`, `mp4`) |

---

## 3. Xem Mẫu Triển Khai Thực Tế

Xem mã nguồn mẫu hoàn chỉnh và chú thích chi tiết tại thư mục:
- [plugins/template/manifest.json](template/manifest.json)
- [plugins/template/plugin.js](template/plugin.js)

