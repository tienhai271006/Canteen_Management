# web_client

Giao diện web (React + Vite) làm theo mục 1.10 "Giao diện minh họa" của tài liệu Phần 1.

```
cd web_client
npm install
npm run dev        # http://localhost:5173, tự chuyển /api sang http://localhost:8080
```
Chạy server Spring Boot trước (`mvn spring-boot:run` trong `server/`). Tài khoản mẫu ở profile dev: `sv@phenikaa-uni.edu.vn` / `Canteen@123`. Mã OTP xem ở Mailpit http://localhost:8025.

## Tiến độ giao diện

| Hình | Nhóm | Trạng thái |
|---|---|---|
| 18 | UC1 Tài khoản và xác thực (`pages/AccountSetup.jsx`, `pages/Login.jsx`) | Xong |
| 19 | UC4 Khám phá | Tạm dùng `Shop.jsx` cũ, chưa theo mẫu |
| 20 | UC5 Giỏ hàng | Tạm dùng `Shop.jsx` cũ |
| 21 đến 25 | UC6, UC7, UC3, UC9, UC10 | Chưa làm |

## Giới hạn của UC1 do phía server

- OCR thẻ sinh viên: server chưa có endpoint. Giao diện gọi `POST /api/me/student-card/ocr` (xem `api.js`), nếu lỗi sẽ chuyển sang nhập tay.
- Xác minh bằng liên kết: server chỉ có OTP nên nút đang bị khóa.
- Khoa/Ngành: hồ sơ trên server chưa có cột này nên chưa được lưu.
