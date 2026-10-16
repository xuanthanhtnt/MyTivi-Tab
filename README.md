# MyTivi Tab – trình phát IPTV cho máy tính bảng Android

Bản chuyển thể của **MyTivi (Android TV box)** sang điều khiển **cảm ứng**, dành cho Samsung Galaxy Tab
và máy tính bảng Android của các hãng khác. Kotlin + Jetpack Compose + Media3 (ExoPlayer).
Phần lõi (đọc playlist M3U, EPG XMLTV, cache, tự làm mới, tự cập nhật APK) giữ nguyên như bản TV.

## Khác gì so với bản TV
| | Bản TV box | Bản tablet |
|---|---|---|
| Điều khiển | Remote (▲▼◄► OK) | Chạm, vuốt, chụm; vẫn dùng được bàn phím (bao da bàn phím, DeX) |
| Màn hình chính | Một bố cục 16:9 | Tự đổi bố cục theo chiều xoay và kích thước (xem dưới) |
| Nhóm kênh | Ngăn ẩn, bấm ◄ để mở | Ngăn luôn tự ẩn, chạm nút **◄ <tên nhóm>** ở đầu bảng lịch (hoặc ☰) để hiện; dựng dọc có thêm hàng chip nhóm |
| Xem trước → toàn màn hình | Tải lại luồng | **Dùng chung 1 trình phát: phóng to ngay, không tải lại** |
| Toàn màn hình | ▲▼ đổi kênh, OK danh sách | Vuốt trái mở danh sách kênh, vuốt dọc độ sáng/âm lượng, chụm để zoom, nút ⏮ ⏯ ⏭ |
| Cửa sổ nổi (PiP) | – | Có (Android 8+), kèm nút kênh trước / phát-dừng / kênh sau |
| Mở app | Vào thẳng toàn màn hình | Mở màn hình lịch và phát luôn kênh gần nhất ở khung xem trước (có thể bật lại kiểu TV trong Cài đặt) |
| Làm mới | Nút Làm mới | Nút Làm mới + **kéo xuống** ở đầu danh sách kênh |
| Mã ứng dụng | `com.mytivi.app` | `com.mytivi.tab` (cài song song được, cập nhật riêng) |
| Tên dưới biểu tượng | MyTivi | **MyTivi Tab** |
| Giới thiệu | – | Màn hình Giới thiệu: phiên bản, nhà phát triển, liên hệ, thiết bị, thư viện, điều khoản |

## Bố cục tự đổi theo màn hình
- **Nằm ngang, màn hình rộng (≥ 840dp)** – Galaxy Tab S7…S10 / S FE, Tab A9+ 11", Xiaomi/Redmi Pad, Lenovo Tab P11/P12…:
  khung xem trước + thông tin chương trình ở trên · thanh thời gian (kèm tên nhóm đang xem) · bảng kênh/chương trình như bản TV.
  Ngăn nhóm kênh **luôn tự ẩn**: chạm nút **◄ <tên nhóm>** ở đầu bảng lịch để hiện. Ngăn có 2 cấp:
  cấp 1 là ô tìm kiếm + danh sách nhóm (chiếm gần hết chiều cao); nút **⚙** ở đầu ngăn mở cấp 2 **Tùy chọn**
  (Làm mới, Cài đặt, Kiểm tra cập nhật, Giới thiệu, số kênh, phiên bản). Chọn nhóm, chạm ra ngoài, vuốt ngăn sang trái,
  bấm Quay lại hoặc để yên 10 giây là ngăn tự ẩn.
- **Dựng dọc, tablet nhỏ, chia đôi màn hình, cửa sổ pop-up** – Tab A9 8.7", Tab S nằm dọc…:
  thanh trên cùng (☰ nhóm kênh, 🔍 tìm, ⟳ làm mới, ⚙ cài đặt) · khung xem trước rộng hết chiều ngang · dải thông tin gọn
  với nút ★ và ⛶ · hàng chip nhóm kênh cuộn ngang · bảng lịch bên dưới.
- Xoay máy, kéo thanh chia đôi màn hình, chuyển sang DeX: **video không bị ngắt**, vẫn giữ nhóm/tìm kiếm/vị trí cuộn.

## Thao tác
**Màn hình chính**
- Chạm **◄ <tên nhóm>** ở đầu bảng lịch: hiện ngăn nhóm kênh · chọn nhóm / chạm ra ngoài / vuốt sang trái / Quay lại /
  để yên 10 giây: ẩn. (Không dùng vuốt từ mép trái vì đó là cử chỉ "Quay lại" của Android.)
- Chạm tên kênh: xem trước (có tiếng). Chạm tên kênh **lần nữa**, hoặc nút *Toàn màn hình*: xem toàn màn hình.
- Khung xem trước: chạm 1 lần hiện nút ⏯ phát/tạm dừng và 🔇 tắt/bật tiếng (app nhớ lựa chọn tắt tiếng);
  **chạm đúp** hoặc nút ⛶ ở góc phải để xem toàn màn hình.
- Chạm ô chương trình sắp chiếu: hiện thông tin chương trình đó. Chạm ô đang phát của kênh đang xem: toàn màn hình.
- Kéo ngang bảng lịch (hoặc thanh giờ): xem trước 12 giờ; nút **◀ Bây giờ** để quay về giờ hiện tại.
- Kéo xuống ở đầu danh sách: làm mới playlist + EPG.
- Chạm ☆ hoặc **giữ lâu** tên kênh: thêm/bỏ yêu thích.

**Xem toàn màn hình**
- Chạm: hiện/ẩn bảng điều khiển ⏮ ⏯ ⏭ (tự ẩn sau ~4 giây).
- **Vuốt sang trái**: mở danh sách kênh (trượt ra từ bên phải) · vuốt sang phải hoặc chạm ra ngoài: đóng.
  Vuốt bắt đầu sát mép phải cũng được (app chặn cử chỉ "Quay lại" của Android ở đoạn giữa mép phải).
- Vuốt dọc ở nửa phải: **âm lượng** · nửa trái: độ sáng.
- Mở 2 ngón: lấp đầy màn hình · chụm 2 ngón: vừa khung. Nút ⛶ đổi 3 kiểu (vừa khung / lấp đầy / kéo giãn), app nhớ lựa chọn.
- Nút ☰: danh sách kênh · ⧉: cửa sổ nổi · ★: yêu thích · ⓘ: xem lại mẹo cử chỉ (tự hiện ở lần đầu mở toàn màn hình).
- Bấm/vuốt **Home** khi đang xem: tự thu nhỏ thành cửa sổ nổi; trong cửa sổ có nút kênh trước / phát-dừng / kênh sau.
- Bàn phím: ▲▼ hoặc PageUp/PageDown đổi kênh, Enter mở danh sách kênh, Space tạm dừng, Esc quay lại.
- Thanh thông tin: số kênh, logo, chương trình đang phát + tiến độ, chương trình tiếp theo, đồng hồ, thông số luồng
  (SD/HD/FHD/4K, độ phân giải, fps, codec, âm thanh, bitrate) – giống bản TV.

## Giới thiệu & thông tin nhà phát triển
Mở từ ngăn nhóm kênh > ⚙ Tùy chọn > **Giới thiệu**, hoặc Cài đặt > **Giới thiệu**. Gồm: phiên bản, số bản dựng, ngày dựng + mã commit,
nhà phát triển, email liên hệ (mở ứng dụng thư với tiêu đề có sẵn số phiên bản), trang mã nguồn, trang báo lỗi,
thông tin thiết bị (nút *Sao chép* để dán vào email báo lỗi), thư viện mã nguồn mở + giấy phép, điều khoản & quyền riêng tư.
Muốn đổi tên/email/năm bản quyền: sửa object `AppInfo` ở đầu `AboutScreen.kt`.

## Thiết bị hỗ trợ
- **Android 6.0 trở lên** (cửa sổ nổi cần Android 8.0+).
- **Samsung**: chia đôi màn hình, pop-up view, **DeX** (không khởi động lại app khi chuyển màn hình; dùng chuột/bàn phím),
  S Pen dùng như ngón tay.
- **Hãng khác**: Xiaomi/Redmi Pad, Lenovo Tab, Huawei MatePad, Oppo/Realme/OnePlus Pad, Teclast/Alldocube…, Amazon Fire (Fire OS 6+).
  App **không cần Google Play Services**, cài APK là chạy.
- Tablet chip giá rẻ: nếu bộ giải mã phần cứng lỗi, app tự chuyển sang bộ giải mã khác thay vì báo lỗi kênh.
- Rút tai nghe / ngắt loa Bluetooth: tự tạm dừng. Có cuộc gọi: tự nhường tiếng.

## Build APK (GitHub Actions – giống bản TV)
> ⚠️ Repo `MyTivi-Tab` phải chứa **mã của thư mục MyTivi-Tab này**, không phải thư mục MyTivi (bản TV).
> Kiểm tra nhanh: trong repo phải có `app/src/main/java/com/mytivi/app/Playback.kt` và `Guide.kt`,
> và `app/build.gradle.kts` có dòng `applicationId = "com.mytivi.tab"`. Release đúng sẽ có file **`MyTivi-Tab.apk`**.

1. Tạo repository mới, ví dụ `MyTivi-Tab` (để **Public** thì app mới đọc được bản phát hành để tự cập nhật).
2. Tải **toàn bộ nội dung thư mục MyTivi-Tab** lên repo (nhớ cả thư mục ẩn `.github`).
3. Thêm 2 secret ở *Settings > Secrets and variables > Actions*: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`.
   Dùng lại **đúng khóa của bản TV** cũng được (mã ứng dụng khác nhau nên không đụng nhau), alias vẫn là `mytivi`.
4. *Actions > Build APK > Run workflow* (hoặc tự chạy khi push lên `main`/`master`). Khoảng 5–8 phút sau có
   Release `v<số lượt build>` kèm **`MyTivi-Tab.apk`**.
5. Nếu build lỗi: mở lượt chạy trên tab Actions, phần **Annotations** có sẵn danh sách lỗi biên dịch (bước *Report build errors*).

Build bằng Android Studio (Koala 2024.1+): mở thư mục, đợi Gradle sync, chạy trên **Tablet emulator**
(Device Manager > Create Device > Tablet, ví dụ *Pixel Tablet* hoặc *Medium Tablet*) hoặc tablet thật.

## Cài lên tablet
- Nếu tablet đang có app **MyTivi** (bản TV) cài từ repo này hoặc repo TV: nên gỡ đi rồi cài **MyTivi Tab** mới
  (hai app khác mã ứng dụng nên không cập nhật đè lên nhau được).
- Mở trên tablet: `https://github.com/<tài-khoản>/MyTivi-Tab/releases/latest/download/MyTivi-Tab.apk`
  (Samsung Internet / Chrome), tải về rồi chạm để cài; cho phép trình duyệt "cài ứng dụng không rõ nguồn gốc".
- Samsung One UI 6 trở lên: nếu đang bật **Auto Blocker** (Cài đặt > Bảo mật và quyền riêng tư > Auto Blocker) thì phải tắt
  mới cài được APK ngoài Galaxy Store / Play Store.
- Google Play Protect có thể cảnh báo ứng dụng lạ: chọn *Chi tiết > Vẫn cài đặt*.
- Hoặc qua máy tính: `adb install MyTivi-Tab.apk`.
- Từ lần sau: ◄ ngăn nhóm kênh > ⚙ Tùy chọn > **Kiểm tra cập nhật** > *Cập nhật ngay* (nút ⚙ có chấm xanh khi có bản mới).
  App tự kiểm tra ngầm mỗi lần mở.

## Cấu trúc mã
| File | Việc | So với bản TV |
|---|---|---|
| `M3uParser.kt`, `EpgParser.kt`, `Http.kt`, `Models.kt`, `GuideModel.kt` | Đọc playlist / EPG, tải mạng, chia ô lịch | Giữ nguyên |
| `MainViewModel.kt`, `Prefs.kt` | Kênh, yêu thích, cache, tự làm mới 3h/6h | Giữ nguyên + 2 tùy chọn mới |
| `Updater.kt`, `UpdateViewModel.kt` | Tự cập nhật từ GitHub Releases | Giữ nguyên |
| `StreamInfo.kt` | Thông số luồng | Nhãn tự xuống dòng khi màn hình hẹp |
| `Playback.kt` | **Một trình phát dùng chung** xem trước + toàn màn hình, tự thử lại khi mất luồng | Mới |
| `PlayerFactory.kt` | Tạo ExoPlayer, dừng khi xuống nền | + dự phòng bộ giải mã, tự dừng khi rút tai nghe |
| `BrowseScreen.kt` | Màn hình chính, bố cục thích ứng, ngăn/chip nhóm, xem trước, thông tin | Viết lại |
| `Guide.kt` | Thanh thời gian + bảng lịch cảm ứng, kéo để làm mới, nút "Bây giờ" | Viết lại |
| `PlayerScreen.kt` | Toàn màn hình: điều khiển, danh sách kênh, độ sáng/âm lượng, kiểu hình | Viết lại |
| `VideoGestures.kt` | Nhận diện chạm / vuốt / chụm trên video | Mới |
| `Pip.kt` | Cửa sổ nổi + nút điều khiển trong cửa sổ | Mới |
| `SetupScreen.kt`, `UpdateDialog.kt` | Cài đặt (có nút Dán), hộp thoại cập nhật | Viết lại cho cảm ứng |
| `AboutScreen.kt` | Màn hình Giới thiệu; `AppInfo` = tên, email nhà phát triển | Mới |
| `Components.kt`, `MainActivity.kt` | Màu, icon, nút tròn; tràn viền, đa cửa sổ | Viết lại |

## Chỉnh nhanh
- Kích thước bảng lịch (độ rộng cột kênh, chiều cao hàng, độ rộng theo phút): hàm `guideDims()` trong `Guide.kt`.
- Ngưỡng chuyển sang bố cục ngăn cố định: `840.dp` trong `BrowseScreen.kt`; độ rộng ngăn: `PANE_W`.
- Chất lượng khung xem trước (mặc định tối đa 720p): `Playback.setFullQuality()`.
- Tần suất tự làm mới: đầu file `MainViewModel.kt` (như bản TV).

## Hạn chế / việc nên làm tiếp
- EPG ghép theo `tvg-id`; kênh không có tvg-id sẽ không hiện lịch.
- Chưa có: nhập số kênh, ghi hình, catch-up, multiview, khóa phụ huynh, Xtream Codes, nhiều playlist, Chromecast,
  link kiểu `url|User-Agent=...`.

Chỉ dùng với nội dung bạn có quyền xem.
