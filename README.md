# Canteen - Đặt món trước tại căng tin

Backend Spring Boot 3 (Java 21) + MySQL 8.4. Xem lộ trình theo pha trong tài liệu dự án.

## Chạy nhanh

```bash
# 1. Hạ tầng: MySQL + Mailpit (xem email thử tại http://localhost:8025)
docker compose up -d

# 2. Chạy server (profile dev mặc định, không cần .env vì đã có giá trị mặc định)
cd server
mvn spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Muốn đổi cấu hình: `cp .env.example .env` rồi export biến, hoặc đặt trong Run Configuration của IDE.
  (Spring Boot không tự đọc file `.env`; docker compose thì có.)

## Cấu trúc

```
server/src/main/java/vn/edu/phenikaa/canteen/
  common/      config, entity (BaseEntity), exception, dto, security, storage
  controller/  mọi controller (auth, vendor, menu, order...)
  service/     mọi service
  repository/  mọi repository
  model/       mọi entity và enum trạng thái
  dto/         mọi request, response
  job/         việc chạy nền (Pha 9)
server/src/main/resources/db/migration/   # Flyway: V1__..., V2__...
```

## Quy ước

- Múi giờ `Asia/Ho_Chi_Minh` (NFR08). Cần giờ hiện tại thì tiêm `Clock`, không gọi `LocalDateTime.now()` trực tiếp.
- Lỗi nghiệp vụ: `throw new BusinessException(ErrorCode.XXX)`; client luôn nhận cùng một dạng JSON (`ErrorResponse`).
- Mọi entity kế thừa `BaseEntity`. Bảng tương ứng phải có các cột:
  `id BIGINT AUTO_INCREMENT`, `created_at DATETIME(6) NOT NULL`, `updated_at DATETIME(6) NOT NULL`, `version BIGINT NOT NULL DEFAULT 0`.
- Schema chỉ đổi qua Flyway migration mới, không sửa file migration đã merge.

## Tài khoản thử (chỉ profile dev)

`DevAccountSeeder` tạo các tài khoản sau khi khởi động, mật khẩu chung `Canteen@123`:

| Email | Vai trò | Quầy được phân công |
|---|---|---|
| admin@canteen.local | ADMIN | - |
| manager@canteen.local | VENDOR_MANAGER | Cơm Nhà Cô Lan, Bún Phở Ba Sao |
| staff@canteen.local | VENDOR_STAFF | Cơm Nhà Cô Lan |
| sv@phenikaa-uni.edu.vn | CUSTOMER (đã xác minh) | - |
| guest@gmail.com | CUSTOMER (chưa xác minh, ngoài miền trường) | - |

Email OTP xem ở Mailpit: http://localhost:8025. Miền email trường cấu hình bằng `SCHOOL_EMAIL_DOMAINS`.

## Lưu ý cho môi trường thật

- `V8__seed_dev.sql` chứa dữ liệu mẫu (khu, quầy, món, thực đơn theo ngày chạy migration). Bỏ file này khỏi bản phát hành.
- Bắt buộc đặt `JWT_SECRET` và `OTP_PEPPER` riêng.

## Phân quyền API

| Đường dẫn | Ai gọi được |
|---|---|
| `/api/auth/register, login, refresh, logout`, `/api/public/**` | Mọi người |
| `/api/me/**`, `/api/auth/change-password`, `/api/auth/verify-email*`, giỏ, đơn | Đã đăng nhập |
| `/api/counter/**` | VENDOR_STAFF, VENDOR_MANAGER |
| `/api/manager/**` | VENDOR_MANAGER |
| `/api/admin/**` | ADMIN |

Ngoài vai trò, mọi API của quầy phải gọi `VendorAccessChecker` với vendorId lấy từ dữ liệu (NFR01, BR03).

## Pha 4: Điểm bán (`vendor/`)

| Đường dẫn | Việc |
|---|---|
| `GET/POST /api/manager/vendors` | Xem quầy của mình, đăng ký quầy mới (PENDING, người đăng ký thành quản lý) |
| `GET/PUT /api/manager/vendors/{id}` | Xem, sửa thông tin quầy |
| `GET/POST/DELETE /api/manager/vendors/{id}/members` | Quản lý thêm/gỡ NHÂN VIÊN (tài khoản phải sẵn là VENDOR_STAFF) |
| `GET /api/admin/vendors?status=&areaId=` | Danh sách quầy |
| `POST /api/admin/vendors/{id}/approve \| suspend \| reactivate` | Duyệt, tạm ngừng (kèm lý do), mở lại |
| `GET/POST/DELETE /api/admin/vendors/{id}/members` | Admin gán mọi vai trò, tự nâng vai trò tài khoản nếu cần |
| `POST/PUT /api/admin/areas` | Thêm, sửa khu vực |

Pha 6 gọi `VendorService.requireAcceptingOrders(vendorId)` khi tạo đơn; không gọi khi xử lý đơn đã có.

## Pha 5: Thực đơn và tìm kiếm (`menu/`, `common/storage/`)

| Đường dẫn | Việc |
|---|---|
| `GET/POST /api/manager/dishes`, `GET/PUT /{id}` | Danh sách (kể cả món ẩn), thêm, sửa món |
| `PATCH /api/manager/dishes/{id}/price`, `/visibility` | Đổi giá (chỉ áp dụng đơn mới), ẩn/hiện món |
| `POST/DELETE /api/manager/dishes/{id}/image` | Tải lên (multipart, field `file`) hoặc gỡ ảnh. JPEG, PNG, WebP, tối đa 2 MB |
| `GET/POST /api/manager/menus`, `GET /{id}` | Lập thực đơn theo ngày và buổi (nháp), xem chi tiết |
| `POST /api/manager/menus/{id}/publish \| unpublish` | Công bố cần ít nhất 1 món bán được và 1 khung nhận |
| `POST /api/manager/menus/{id}/items`, `PATCH .../availability`, `DELETE` | Thêm món kèm tổng suất, đánh dấu hết món, gỡ món |
| `PUT /api/manager/menus/{id}/items/{itemId}/quota`, `GET .../quota/history` | Chỉnh hạn mức (R3.6) và xem lịch sử |
| `PUT .../cutoff`, `GET/POST/PUT/DELETE .../slots` | Giờ đóng nhận đơn, khung giờ nhận |
| `GET /api/public/areas, /vendors, /menu-items, /images/{file}` | Công khai: khu, quầy, tìm món, ảnh |

Tìm món: `q, date, session, areaId, vendorId, category, minPrice, maxPrice, availableOnly, sort (NAME|PRICE_ASC|PRICE_DESC|REMAINING_DESC), page, size`.

Lưu ý cho Pha 6: câu UPDATE giữ/trả suất trên bảng `quota` phải kèm `version = version + 1`, và tăng `reserved_slots` có điều kiện `reserved_slots + counter_slots + :qty <= total_slots`. Thư mục ảnh đặt bằng `STORAGE_DIR` (mặc định `./uploads`), môi trường thật cần mount volume.
