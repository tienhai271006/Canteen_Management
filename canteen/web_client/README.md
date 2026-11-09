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
| 19 | UC4 Khám phá (`pages/Discover.jsx`) | Xong |
| 20 | UC5 Giỏ hàng và đặt món (`pages/Cart.jsx`) | Xong |
| 21 đến 24 | UC6, UC7, UC3, UC9 (`Orders`, `Counter*`, `Manager*`) | Xong (theo bản nhóm) |
| 25 | UC10 Quản trị (`pages/Admin.jsx`, `AdminIncidents.jsx`, `AdminPages.jsx`) | Xong |

## Giới hạn của UC1 do phía server

- OCR thẻ sinh viên: server chưa có endpoint. Giao diện gọi `POST /api/me/student-card/ocr` (xem `api.js`), nếu lỗi sẽ chuyển sang nhập tay.
- Xác minh bằng liên kết: server chỉ có OTP nên nút đang bị khóa.
- Khoa/Ngành: hồ sơ trên server chưa có cột này nên chưa được lưu.

## Ghi chú UC4

- Khách chưa đăng nhập vẫn xem được thực đơn (API `/api/public/**`); bấm thêm vào giỏ sẽ chuyển tới đăng nhập.
- API chỉ lọc một `vendorId`, nên chọn nhiều điểm bán được lọc ở phía giao diện.
- Loại món lấy theo server: Món chính, Món phụ, Đồ uống, Ăn vặt, Tráng miệng (mẫu ghi Cơm, Canh).
- Buổi mặc định theo giờ hiện tại; thực đơn luôn là của hôm nay vì mẫu không có ô chọn ngày.

## Ghi chú UC5

- Ưu đãi đơn đầu: server chưa có bean tính ưu đãi (Pha 8) nên `estimatedDiscount` đang bằng 0 và khung voucher chỉ hiện khi tài khoản có quyền ưu đãi `AVAILABLE`. Lựa chọn "Không dùng voucher" được gửi lên là `useVoucher:false` trong `POST /api/orders`; server chưa đọc trường này, cần bổ sung vào `PlaceOrderRequest` khi làm Pha 8.
- Khung nhận "Đã đầy": `SlotResponse` chưa có thông tin còn chỗ. Giao diện đã sẵn sàng đọc `full` hoặc `available:false`; hiện khung giờ đã qua bị khóa, còn khung đầy sẽ được server từ chối khi đặt và báo lỗi.
- Mức 50%, tối đa 30.000đ, đơn từ 25.000đ là hằng số hiển thị theo chính sách; số tiền giảm thật luôn lấy từ server.
- Sau khi đặt: hiện đơn Pending, đếm ngược 5 phút và tự cập nhật trạng thái mỗi 8 giây. Màn "Đơn của tôi" làm ở Hình 21.

## Ghi chú UC10 (Hình 25, tài khoản `admin@canteen.local`)

- Đăng nhập bằng ADMIN thì `App.jsx` chuyển sang giao diện quản trị (thanh bên: Điểm bán, Chiến dịch ưu đãi, Sự việc & hạn chế, Thông báo email, Người dùng).
- Sự việc & hạn chế: `GET /api/admin/incidents`, `POST .../decision` (CONFIRM/EXEMPT, căn cứ bắt buộc), `POST .../revoke`, `GET /api/admin/users/{id}/violations`, `POST /api/admin/restrictions`, `GET/POST /api/admin/feedback`.
  Nút "Lưu quyết định" chỉ mở khi người dùng đạt 3 vi phạm đã xác nhận, không còn sự việc chờ xem xét và không còn phản hồi chưa xử lý (BR26, BR35). Hằng số 3 khai báo ở `AdminIncidents.jsx` (`THRESHOLD`), khớp `IncidentProperties.escalateAt`.
- Điểm bán: duyệt, tạm ngừng (có lý do), mở lại. Chiến dịch: tạo, sửa, bật, tắt.
- Thông báo email: hiện thông báo trong ứng dụng của admin (`/api/notifications`), vì server chưa có API xem nhật ký gửi email.
- Người dùng: bị khóa, server chưa có API quản lý người dùng.
- Mẫu ghi mã người dùng dạng `24100xxx`; giao diện hiện tên khách vì `IncidentResponse` chỉ trả `customerName`.
