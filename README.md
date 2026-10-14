# Drive 3 Hồ Sơ

App Android: 3 hồ sơ cố định (Ảnh / Video / Tệp), mỗi hồ sơ đăng nhập 1 tài khoản Google riêng trong Drive web.
Mỗi hồ sơ chạy process riêng + thư mục dữ liệu WebView riêng nên cookie không lẫn nhau.

## Máy hỗ trợ
- Android 7.1.1 (API 25) trở lên. App chỉ có mã Java, không có thư viện native,
  nên **1 file APK chạy được trên mọi CPU**: ARM 32-bit, ARM64, x86, x86_64.
- Lưu ý Android 7–8: hệ điều hành chưa cho tách thư mục dữ liệu WebView theo process
  (tính năng này có từ Android 9), nên 3 hồ sơ có thể dùng chung đăng nhập.
  Từ Android 9 trở lên thì tách riêng hoàn toàn.
- Nên cập nhật **Android System WebView** / Chrome trong Google Play cho máy cũ để Drive web chạy tốt.

## Tải lên
Việc tải thật do trang Drive trong WebView làm, nên app có thêm:
- Khung phần trăm tiến độ ở đáy màn hình + thông báo "Đang tải lên…" có thanh tiến độ.
- Giữ màn hình sáng trong lúc tải.
- Dịch vụ nền (cùng process với hồ sơ) giữ CPU + Wi-Fi thức khi tắt màn hình, để process khó bị hệ thống dọn.
- Thông báo khi tải xong.

Phần trăm được tính từ số byte trang Drive gửi đi so với dung lượng tệp đã chọn, nên là số ước lượng.

## Build APK bằng GitHub
1. Tạo repo mới, giải nén zip và đẩy toàn bộ lên nhánh `main`.
2. Vào tab **Actions** -> workflow **Build APK** (tự chạy khi push, hoặc bấm *Run workflow*).
3. Xong, tải artifact **Drive3-apk** -> cài file `app-debug.apk`.
