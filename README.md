# MyTivi – trình phát IPTV cho Android TV box

App phát playlist M3U / luồng HLS (.m3u8) với giao diện điều khiển bằng remote, lấy cảm hứng từ TiviMate.
Kotlin + Jetpack Compose + Media3 (ExoPlayer).

## Tính năng
- Thêm playlist bằng link (.m3u danh sách kênh, hoặc 1 link .m3u8 đơn)
- Nhóm kênh (group-title), logo kênh, tìm kiếm, kênh yêu thích
- Xem toàn màn hình: ▲▼ / CH+ CH- đổi kênh, OK mở danh sách kênh, MENU thêm/bỏ yêu thích
- EPG (XMLTV, hỗ trợ .gz): hiện chương trình đang phát + thanh tiến độ + chương trình tiếp theo
- **Giao diện lịch phát sóng kiểu TiviMate**: khung xem trước + thông tin chương trình ở trên;
  bên dưới là thanh thời gian và bảng kênh với các ô chương trình, có vạch "bây giờ"
- Lướt tới kênh/ô nào thì khung xem trước (bo góc mềm, **có tiếng**) tự chạy kênh đó
- **Nhóm kênh là ngăn kéo ẩn bên trái**: ở cột tên kênh bấm **◄** để mở, bấm **►** để đóng;
  di chuyển trong ngăn thì danh sách kênh đổi theo nhóm; ô tìm kiếm ở trên, **Làm mới** và **Cài đặt** nằm ở dưới cùng của ngăn
- Phần thông tin bên phải khung xem trước chỉ gồm: chương trình đang phát, giờ bắt đầu - kết thúc, thời gian còn lại
- Thanh thời gian có mũi tên tam giác đánh dấu giờ hiện tại (thay cho vạch dọc)
- ☆ cạnh tên kênh để thêm/bỏ yêu thích; bấm Home hoặc TV tắt màn hình thì tự dừng tiếng
- **Tự cập nhật**: playlist tải lại sau mỗi 3 giờ, EPG sau mỗi 6 giờ (kể cả khi box mở nhiều ngày liền);
  mở app là hiện ngay dữ liệu đã lưu rồi cập nhật ngầm; bấm *Làm mới* để cập nhật tay
- **Cuộn vòng** danh sách kênh (bảng kênh và bảng chọn kênh toàn màn hình): ở kênh cuối bấm ▼ về kênh đầu, ở kênh đầu bấm ▲ xuống kênh cuối
- Mở app là vào thẳng kênh xem gần nhất; tự thử lại khi mất luồng
- Lưu cache playlist và EPG nên lần sau mở app có kênh và lịch ngay (ghi qua file tạm, không bao giờ ghi dở)
- Bấm *Làm mới* liên tục không tạo nhiều lượt tải chồng nhau; kênh trùng hoàn toàn (cùng nhóm + cùng link) chỉ giữ 1

## Thanh thông tin khi xem kênh
- Vào kênh hoặc đổi kênh: thanh thông tin hiện ở cạnh dưới khoảng 5 giây rồi mờ dần (bấm INFO trên remote để hiện lại).
- Gồm: số kênh, logo, tên kênh, chương trình đang phát + tiến độ, chương trình tiếp theo, đồng hồ và
  thông số luồng: chất lượng (SD/HD/FHD/4K), độ phân giải, fps, codec hình (H.264/H.265…), âm thanh (AAC 2.0, AC3 5.1…), bitrate.

## Cách build
1. Cài **Android Studio** bản Koala (2024.1) trở lên, mở thư mục `MyTivi`.
2. Đợi Gradle sync (cần Internet, Android SDK 34 – Studio sẽ tự đề nghị cài).
   Nếu Studio báo thiếu Gradle wrapper, chọn dùng Gradle 8.7 trở lên.
3. Chạy thử: tạo **Android TV emulator** (Device Manager > Create Device > TV), hoặc cắm TV box.
4. Tạo APK: menu **Build > Build APK(s)** → file `app/build/outputs/apk/debug/app-debug.apk`.

## Build APK không cần cài Android Studio (GitHub Actions)
1. Tạo tài khoản GitHub (miễn phí) và tạo một repository mới.
2. Giải nén `MyTivi.zip`, rồi tải **toàn bộ nội dung thư mục MyTivi** lên repo
   (nhớ cả thư mục ẩn `.github`; trên web chọn *Add file > Upload files* và kéo thả cả thư mục).
3. Vào tab **Actions > Build APK > Run workflow** (hoặc đợi nó tự chạy sau khi upload).
4. Sau khoảng 5–8 phút, mở lượt chạy vừa xong, kéo xuống mục **Artifacts**, tải `MyTivi-apk`.
   Giải nén sẽ có `app-debug.apk` – cài thẳng lên TV box.

## Cài lên TV box
- Bật *Tùy chọn nhà phát triển > Gỡ lỗi ADB* trên box, rồi:
  `adb connect <IP-của-box>:5555` và `adb install app-debug.apk`
- Hoặc chép APK vào USB, mở bằng file manager trên box (cần cho phép cài từ nguồn không rõ).

## Cấu trúc mã
| File | Việc |
|---|---|
| `M3uParser.kt` | Đọc #EXTINF (tvg-id, tvg-logo, group-title...) |
| `EpgParser.kt` | Đọc XMLTV, chỉ giữ -2h..+24h |
| `Http.kt` | Tải playlist/EPG bằng OkHttp, lưu file cache, tự giải nén gzip |
| `MainViewModel.kt` | Trạng thái: kênh, yêu thích, EPG, cache |
| `BrowseScreen.kt` | Màn hình chính: xem trước + bảng lịch kênh/chương trình |
| `GuideModel.kt` | Chia EPG thành các ô trên bảng lịch |
| `Updater.kt`, `UpdateViewModel.kt`, `UpdateDialog.kt` | Kiểm tra, tải và cài bản cập nhật từ GitHub Releases |
| `PlayerFactory.kt` | Tạo ExoPlayer dùng chung (xem trước + toàn màn hình) |
| `PlayerScreen.kt` | Trình phát ExoPlayer, thanh thông tin, bảng chọn kênh |
| `StreamInfo.kt` | Đọc thông số luồng (độ phân giải, fps, codec, bitrate) cho thanh thông tin |
| `SetupScreen.kt` | Nhập link playlist / EPG, xem phiên bản, kiểm tra cập nhật |

## Hạn chế hiện tại / việc nên làm tiếp
- EPG ghép theo `tvg-id`; kênh không có tvg-id sẽ không hiện lịch.
- Chưa có: nhập số kênh bằng phím số, ghi hình, catch-up, multiview, khóa phụ huynh, Xtream Codes, nhiều playlist.
- Link kiểu `url|User-Agent=...` (header riêng) chưa được hỗ trợ.

Chỉ dùng với nội dung bạn có quyền xem.

## Đổi tần suất tự cập nhật
Trong `MainViewModel.kt`, đầu file có 3 hằng số `PLAYLIST_TTL_MS`, `EPG_TTL_MS`, `CHECK_EVERY_MS`.

## Logo
Icon app: `app/src/main/res/drawable/ic_launcher.xml` (vector). Banner trên màn hình Android TV:
`app/src/main/res/drawable-xhdpi/tv_banner.png` (640x360). Có thể xóa file cũ `drawable/banner.xml` nếu còn.

## Chỉnh bảng lịch
Đầu file `BrowseScreen.kt`: `LEFT_W` (cột tên kênh), `ROW_H` (cao mỗi hàng), `DP_PER_MIN` (độ rộng theo phút),
`GUIDE_HOURS` (số giờ hiển thị). Kênh không có `tvg-id` khớp EPG sẽ hiện "Không có thông tin".

## Cập nhật trong app (không cần tải lại APK bằng tay)
- Mỗi lần push mã lên nhánh master, GitHub Actions build APK, ký bằng khóa cố định và tạo một **Release** `v<số lượt build>` kèm `MyTivi.apk`.
- Trong app: bấm ◄ mở ngăn nhóm kênh > **Kiểm tra cập nhật**. Có bản mới thì bấm **Cập nhật ngay**; app tải về và mở trình cài đặt của Android.
- App tự kiểm tra ngầm mỗi lần mở; có bản mới thì nút cập nhật đổi màu xanh.
- Lần đầu cập nhật, Android hỏi cho phép MyTivi "cài ứng dụng không rõ nguồn gốc"; bật một lần là xong.
- Link tải bản mới nhất (dùng cho app Downloader trên box):
  `https://github.com/<tài-khoản>/<repo>/releases/latest/download/MyTivi.apk`
- Repo phải để **Public** thì app mới đọc được bản phát hành.

### Khóa ký (bắt buộc, làm một lần)
Thêm 2 secret ở repo > Settings > Secrets and variables > Actions: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`.
Không có 2 secret này thì workflow dừng ngay ở bước "Prepare signing key".
Giữ kỹ file khóa: mất khóa thì không cập nhật đè được nữa, phải gỡ app cài lại.
