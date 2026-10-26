# JKHX Player 🎬📖

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20Material%203-blue.svg)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Playback-AndroidX%20Media3%20(ExoPlayer)-red.svg)](https://developer.android.com/media/media3)
[![QuickJS-NG](https://img.shields.io/badge/Runtime-QuickJS--NG%20NDK-orange.svg)](https://github.com/quickjs-ng/quickjs)
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](LICENSE)

**JKHX Player** là ứng dụng trình phát đa phương tiện mã nguồn mở (Modular Media Player) hiện đại dành cho hệ điều hành Android. Ứng dụng kết hợp sức mạnh phát video hiệu năng cao của **AndroidX Media3 (ExoPlayer)**, giao diện mượt mà của **Jetpack Compose (Material 3)**, và kiến trúc mở rộng bằng tiện ích độc lập (Plugins) chạy trên môi trường sandbox **QuickJS-NG NDK C++**.

---

## 🌟 Tính Năng Nổi Bật

### 1. Trình Phát Đa Phương Tiện Hiệu Năng Cao (ExoPlayer Native + Hybrid Fallback)
* **Đa định dạng:** Hỗ trợ mượt mà các giao thức phát luồng phổ biến: HLS (`.m3u8`), MP4, MKV, DASH.
* **Hybrid Player:** Ưu tiên phát native qua Media3 ExoPlayer với giải mã phần cứng (Hardware Acceleration); tự động kích hoạt chế độ WebView Player dự phòng khi gặp các máy chủ luồng nhúng đặc thù.
* **Cử chỉ thông minh (Gesture Controls):** Vuốt cạnh trái để chỉnh độ sáng màn hình, vuốt cạnh phải để chỉnh âm lượng, vuốt ngang để tua nhanh/lùi thời gian với bộ hiển thị HUD trực quan.
* **Chế độ cửa sổ nhỏ (Picture-in-Picture - PiP):** Tiếp tục theo dõi nội dung trong khi chuyển đổi sang các ứng dụng khác.

### 2. Kiến Trúc Plugin Sandbox Độc Lập (QuickJS-NG Engine)
* **Tách bạch hoàn toàn:** Mã nguồn Core Player của ứng dụng không chứa logic hay mã nguồn độc quyền của bất kỳ bên thứ ba nào.
* **An toàn & Cách ly:** Toàn bộ plugin được thực thi dưới dạng ES Modules độc lập trong sandbox QuickJS-NG qua cầu nối C++/JNI, đảm bảo tốc độ phân tích cú pháp cực nhanh và an toàn bộ nhớ.
* **Mở rộng linh hoạt:** Thêm kho nguồn (Repositories) từ xa đơn giản thông qua file `manifest.json`.

### 3. Hệ Thống Phụ Đề & Thuyết Minh Thông Minh
* **Bộ nạp phụ đề chuyên sâu:** Hỗ trợ đồng bộ phụ đề nhúng sẵn trong luồng video và nạp phụ đề rời (`.srt`, `.vtt`).
* **Tùy biến hiển thị phụ đề:** Dễ dàng thay đổi kích thước chữ, màu sắc, phông chữ đậm/nhạt, viền chữ nổi (Outline), độ trong suốt và vị trí căn lề dưới.
* **Tính năng đồng bộ âm thanh:** Tích hợp bộ chỉnh độ trễ thời gian phụ đề (Subtitle Offset Tuning) theo từng mili-giây.

### 4. Chế Độ Đọc Truyện Tranh Dọc (Vertical Comic Reader)
* Trình đọc truyện tối ưu cho màn hình cảm ứng: cuộn dọc liên tục mượt mà, hỗ trợ zoom đa điểm (Pinch-to-Zoom), chuyển chương tức thì và tự động ghi nhớ số trang đang đọc dở.

---

## 🏗️ Kiến Trúc Hệ Thống (Architecture)

Dự án được xây dựng theo chuẩn **Clean Architecture** và chia tách thành các module Gradle độc lập:

```
jkhx-player/
├── android/
│   ├── app/                # Giao diện Jetpack Compose (HomeScreen, PlayerScreen, ReaderScreen...)
│   ├── core/
│   │   ├── player/         # Quản lý phát Media3 ExoPlayer, Subtitle Parser và Audio Focus
│   │   ├── network/        # Khách kết nối HTTP (OkHttp 4), bộ điều phối Stremio/Addon
│   │   ├── database/       # Lưu trữ cục bộ Room Database (Lịch sử xem, Yêu thích, Cài đặt)
│   │   └── model/          # Định nghĩa cấu trúc dữ liệu chung (Models, DTOs, Entities)
│   └── plugin/
│       ├── api/            # Hợp đồng interface chuẩn giữa Core App và Plugin JavaScript
│       ├── runtime/        # QuickJS-NG C++ JNI Bridge và Headless Sniffer
│       └── manager/        # Quản lý vòng đời, cài đặt và tải plugin từ manifest.json
└── plugins/
    └── template/           # Mẫu plugin tham khảo (Reference Implementation)
```

---

## 📥 Tải Về & Cài Đặt (Download APK)

Bạn có thể tải file APK cài đặt bản mới nhất đã được biên dịch sẵn tại trang **Releases**:

👉 **[Tải JKHX Player APK Mới Nhất Tại Đây](../../releases/latest)**

*(Yêu cầu thiết bị chạy Android 8.0 Oreo - API 26 trở lên)*.

---

## 🛠️ Hướng Dẫn Tự Biên Dịch Từ Mã Nguồn (Build from Source)

### Yêu cầu môi trường:
* **Hệ điều hành:** Linux, macOS hoặc Windows (WSL2).
* **JDK:** Java Development Kit 17 (OpenJDK 17).
* **Android SDK:** Compile SDK 35, Build-tools 35.0.0.
* **Android NDK:** Phiên bản `26.1.10909125` (dùng để build QuickJS-NG native library).
* **CMake:** Phiên bản `3.22.1` trở lên.

### Lệnh biên dịch:
```bash
# 1. Clone repository
git clone https://github.com/your-username/jkhx-player.git
cd jkhx-player/android

# 2. Biên dịch bản Release APK
JAVA_HOME=/path/to/jdk-17 ./gradlew assembleRelease

# 3. File APK xuất xưởng sẽ nằm tại:
# app/build/outputs/apk/release/app-release.apk
```

---

## 🧩 Phát Triển Plugin Cho JKHX Player

Bạn muốn viết thêm plugin nguồn dữ liệu riêng? 
Hãy xem tài liệu hướng dẫn và mã nguồn mẫu tại:
* [Hướng dẫn kiến trúc Plugin](plugins/README.md)
* [Plugin Mẫu Chuẩn (Template)](plugins/template/plugin.js)
* [Tài liệu Plugin API Specification](docs/plugin-api.md)

---

## ⚖️ Tuyên Bố Miễn Trừ Trách Nhiệm (Disclaimer)

* Ứng dụng **JKHX Player** được phát triển hoàn toàn vì mục đích nghiên cứu công nghệ, học tập kỹ thuật lập trình Android Native và giải pháp sandbox QuickJS.
* Mã nguồn của ứng dụng là một **trình phát đa phương tiện thuần túy (Clean Media Player)**, không chứa sẵn, không lưu trữ và không phân phối bất kỳ nội dung âm thanh, video hay ấn phẩm có bản quyền nào.
* Người dùng tự chịu hoàn toàn trách nhiệm đối với các tiện ích mở rộng (Plugins) và nguồn dữ liệu mà mình tự cấu hình và tải vào ứng dụng.

---

## 📄 Bản Quyền (License)

Mã nguồn được phân phối dưới giấy phép [Apache License 2.0](LICENSE).
