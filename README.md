# Drive 3 Hồ Sơ

App Android: 3 hồ sơ cố định (Ảnh / Video / Tệp), mỗi hồ sơ đăng nhập 1 tài khoản Google riêng trong Drive web.
Mỗi hồ sơ chạy process riêng + thư mục dữ liệu WebView riêng nên cookie không lẫn nhau.

## Build APK bằng GitHub
1. Tạo repo mới, giải nén zip và đẩy toàn bộ lên nhánh `main`.
2. Vào tab **Actions** -> workflow **Build APK** (tự chạy khi push, hoặc bấm *Run workflow*).
3. Xong, tải artifact **Drive3-apk** -> cài file `app-debug.apk`.
