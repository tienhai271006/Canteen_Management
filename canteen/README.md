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

## Pha 10: Không nhận món và vi phạm

Luồng: đơn Ready quá hạn nhận, nhân viên kiểm tra thực tế rồi ghi **Not collected**. Việc này chỉ tạo một *sự việc* chờ xem xét,
chưa cộng vi phạm. Quản lý quầy (hoặc quản trị viên) kết luận **Xác nhận** hoặc **Miễn**. Chỉ sự việc đã xác nhận, xảy ra trong
30 ngày gần nhất, mới được đếm: lần 1 nhắc, lần 2 cảnh báo, từ lần 3 chuyển quản trị viên (không tự khóa). Quản trị viên quyết
định hạn chế đặt trước 7 ngày (chặn tạo đơn, vẫn đăng nhập, xem, phản hồi được).

| Đường dẫn | Ai | Việc |
|---|---|---|
| `GET /api/counter/orders/overdue?vendorId=` | Nhân viên, quản lý quầy | Đơn Ready đã quá hạn cần kiểm tra |
| `POST /api/counter/orders/{id}/not-collected` | Nhân viên, quản lý quầy | Ghi Not collected, tạo sự việc (UC7.8) |
| `GET /api/counter/waste?vendorId=`, `GET/POST /api/counter/orders/{id}/waste` | Nhân viên, quản lý quầy | Xem và xử lý món đã làm: bán lại hoặc hao hụt (UC3.7) |
| `GET /api/manager/incidents?vendorId=&status=`, `POST /api/manager/incidents/{id}/decision` | Quản lý quầy | Xem và kết luận sự việc của quầy mình |
| `GET /api/manager/feedback?vendorId=`, `POST /api/manager/feedback/{id}/resolve` | Quản lý quầy | Giải quyết phản hồi |
| `GET /api/admin/incidents`, `POST /api/admin/incidents/{id}/decision`, `POST .../revoke` | Quản trị viên | Xem, kết luận, hủy kết luận |
| `GET /api/admin/users/{id}/violations` | Quản trị viên | Hồ sơ vi phạm một khách |
| `GET/POST /api/admin/restrictions`, `POST .../{id}/revoke` | Quản trị viên | Quyết định và gỡ hạn chế đặt trước |
| `GET /api/me/incidents`, `POST /api/me/incidents/{id}/feedback` | Khách | Xem sự việc của mình và gửi phản hồi |

Tham số `app.incident.*` (cửa sổ 30 ngày, ngưỡng 3, hạn chế 7 ngày) nằm trong `application.yml`.

Lưu ý thiết kế:
- Not collected **không** tự trả suất và không tự mở bán lại (BR15). Chỉ phần quầy ghi *bán lại* mới trả đúng suất đó.
- Ưu đãi đơn đầu của đơn không nhận sang *Chờ xem xét*; kết luận **Miễn** trả quyền nếu còn hạn, **Xác nhận** làm mất quyền,
  **Hủy kết luận** khôi phục quyền nếu còn hạn. Chính sách nằm ở `PromotionService.resolveReview` và `reinstate`.
- Sự việc có phản hồi đang mở là *tranh chấp*: không xác nhận được và không dùng để quyết định hạn chế cho tới khi phản hồi được giải quyết (BR35).
- Migration `V10` bỏ UNIQUE `(order_id, dish_id)` của `waste_record` để xử lý được nhiều đợt, và thêm UNIQUE cho "một phản hồi mở mỗi sự việc" và "một hạn chế hiệu lực mỗi khách".

## Pha 11: Thống kê

| Đường dẫn | Ai | Việc |
|---|---|---|
| `GET /api/manager/stats?vendorId=&from=&to=&session=` | Quản lý đúng quầy | Thống kê một quầy (UC9.1 đến UC9.4) |
| `GET /api/admin/stats/vendors/{vendorId}?from=&to=&session=` | Quản trị viên | Như trên, cho mọi quầy |
| `GET /api/admin/stats/overview?from=&to=` | Quản trị viên | Tổng hợp các quầy và chỉ tiêu chung (UC10.7) |

`from`, `to` là **ngày phục vụ** (ngày của thực đơn) dạng `yyyy-MM-dd`; bỏ trống thì 7 ngày gần nhất đến hôm nay, tối đa 366 ngày.
`session` (MORNING, LUNCH, AFTERNOON) là tùy chọn.

Cách tính:
- **Đơn (UC9.1)**: đếm theo trạng thái (luôn đủ 8 trạng thái, thiếu thì bằng 0) và chi tiết theo ngày, buổi.
- **Doanh thu (UC9.2, BR34)**: chỉ đơn Completed và Paid. `grossAmount` là giá trị trước giảm, `discountAmount` là chi phí ưu đãi,
  `netRevenue` là tiền thực thu (= gross - discount). Đơn không nhận chưa thanh toán không tạo doanh thu.
- **Món (UC9.3)**: `ordered = sold + cancelled + notCollected + inProgress`. `cancelled` gồm Cancelled và Expired.
- **Hao hụt và sự việc (UC9.4)**: `unprocessed = notCollectedPortions - resold - wasted`, kèm số sự việc theo trạng thái và phản hồi đang mở.

Thống kê tính theo ngày phục vụ chứ không theo ngày tạo đơn, để đơn đặt hôm trước cho bữa trưa hôm sau nằm đúng bữa đó.
Truy vấn đi từ `orders` qua `pickup_slot` sang `menu` và lọc bằng `(vendor_id, menu_date)`; chỉ mục
`(vendor_id, status, created_at)` đã có từ V4 phục vụ hàng đợi của quầy.

## Pha 12: OCR thẻ sinh viên (tùy chọn)

OCR chỉ là cách điền hồ sơ nhanh hơn, **không** thay xác minh email và **không** chứng minh thẻ là thật (BR09). Mặc định
**tắt**; khi tắt, người dùng nhập tay như cũ.

| Đường dẫn | Ai | Việc |
|---|---|---|
| `GET /api/me/student-card/ocr-status` | Khách | `{available}`: giao diện dùng để hiện hay ẩn nút "Quét thẻ" |
| `POST /api/me/student-card/scan` (multipart, trường `image`) | Khách | UC1.3: nhận ảnh, trả bản nháp `{draftId, fullName, studentCode, email, expiresAt}` |
| `GET /api/me/student-card/drafts/{draftId}` | Khách | Mở lại bản nháp còn hạn |
| `POST /api/me/student-card/drafts/{draftId}/confirm` | Khách | UC1.4: lưu giá trị đã kiểm tra `{fullName, studentCode, email?}` rồi xóa bản nháp |
| `DELETE /api/me/student-card/drafts/{draftId}` | Khách | Hủy, không ghi gì vào hồ sơ |

Luồng: ảnh vào bộ nhớ, nhận dạng, **bỏ ảnh**, chỉ giữ chữ đã rút ra trong bản nháp có hạn `app.ocr.draft-ttl` (10 phút).
Quét lại thì bản cũ bị thay. Job `OcrDraftCleanupJob` dọn bản hết hạn; bản hết hạn cũng không truy cập được dù chưa dọn (BR10, NFR07).

Điểm cần biết:
- **Ảnh không bao giờ ghi ra đĩa** hay vào DB. Chữ OCR không được ghi log vì là dữ liệu cá nhân.
- **Chỉ xác nhận mới ghi hồ sơ.** Lưu đi qua `ProfileService.updateProfile`, nên dùng chung luật trùng mã sinh viên; lưu lỗi thì hồ sơ
  giữ nguyên và bản nháp còn để sửa rồi gửi lại. Số điện thoại hiện có được giữ. Email chỉ đổi nếu người dùng gửi `email`,
  và theo đúng luồng đổi email (email trường vẫn phải xác minh OTP).
- **Bản nháp gắn với chủ**: người khác biết `draftId` cũng nhận 404. Mỗi người một bản nháp, tối đa `max-scans-per-hour` lượt quét/giờ.
- **Chống lạm dụng**: chỉ nhận JPEG và PNG (nhận diện bằng byte đầu, không tin Content-Type), kiểm tra số điểm ảnh *trước* khi giải mã,
  giới hạn số lượt OCR chạy đồng thời và hạn giờ cho mỗi lượt (quá tải trả `OCR_BUSY`).
- **Bộ rút chữ** (`StudentCardParser`) ưu tiên dòng có nhãn ("Họ và tên", "MSV", "Student ID"...) rồi mới đoán theo hình dạng.
  Mã sinh viên khớp `app.ocr.student-code-pattern` (mặc định 8 đến 10 chữ số): **hãy chỉnh theo mã thật của trường**.
  Kết quả luôn là gợi ý để người dùng sửa.
- **Bản nháp nằm trong bộ nhớ một tiến trình.** Chạy nhiều bản server thì cần sticky session hoặc thay `OcrDraftStore` bằng kho dùng chung (Redis có TTL).
  Khởi động lại làm mất bản nháp; người dùng chỉ cần quét lại.

Bật OCR:
1. Cài Tesseract và dữ liệu tiếng Việt trên máy chạy server, ví dụ Debian/Ubuntu: `apt install tesseract-ocr tesseract-ocr-vie tesseract-ocr-eng libtesseract-dev`.
2. Đặt `OCR_ENABLED=true`; nếu dữ liệu ngôn ngữ không ở chỗ mặc định thì đặt thêm `TESSDATA_PATH` (thư mục chứa `vie.traineddata`).
3. Muốn đổi sang dịch vụ thị giác trên đám mây: viết một lớp `implements OcrEngine` và bỏ `Tess4jOcrEngine`; phần còn lại không đổi.

Mã lỗi mới: `OCR_UNAVAILABLE`, `OCR_BUSY`, `OCR_RATE_LIMITED`, `OCR_NO_TEXT`, `OCR_DRAFT_NOT_FOUND` (cộng `FILE_INVALID`, `FILE_TOO_LARGE` có sẵn).

### Hạn mức theo khung giờ nhận (V11)

- `pickup_slot.capacity` (NULL = không giới hạn) và `reserved_qty`. Đặt đơn giữ suất khung bằng một câu UPDATE có điều kiện,
  sau khi giữ suất món; hủy/hết hạn trả lại theo cùng thứ tự. Khung đầy trả `SLOT_FULL` (409).
- `PUT /api/manager/menus/{menuId}/slots/{slotId}/capacity` body `{"capacity": 40}` hoặc `{"capacity": null}`; không giảm dưới số đã nhận
  (`SLOT_CAPACITY_BELOW_COMMITTED`).
- Còn nhận = hạn mức − đang giữ − đã dùng; "đã dùng" là suất của đơn COMPLETED/NOT_COLLECTED.
- Chưa chạy migration V11 trên MySQL thật và chưa chạy test server (môi trường không có Maven/Docker).

### Giao diện quầy (web_client)

Hình 21 `Orders.jsx` · Hình 22 `CounterBoard.jsx` · Hình 23 `ManagerMenu.jsx` · Hình 24 `ManagerStats.jsx` (gọi `/api/manager/stats` của Pha 11).

### Chia chi phí ưu đãi và khoản bù (V12)

- Quy tắc: nền tảng chịu `app.promotion.platform-share-percent` (mặc định 70%) của mỗi khoản giảm trên đơn Completed và Paid,
  làm tròn xuống từng đơn; quầy chịu phần còn lại. Đổi tỷ lệ chỉ tác động tới cách tính, không sửa đơn đã tạo.
- Khoản nền tảng đã chuyển bù ghi bằng `POST /api/admin/reimbursements` `{"vendorId":1,"forDate":"2026-11-03","amount":300000,"note":"..."}`
  (chỉ ADMIN, chỉ ghi thêm). `forDate` là ngày phục vụ mà khoản này bù cho, cùng mốc với thống kê.
- `/api/manager/stats` trả thêm `revenue.funding` {platformPercent, platformAmount, vendorAmount, reimbursed, outstanding}.
- Chưa có màn hình admin để nhập khoản bù (dùng Swagger hoặc curl), sẽ làm cùng các màn hình admin sau.

### Dữ liệu mẫu cho mọi tài khoản thử (profile dev)

`DevDataSeeder` chạy sau `DevAccountSeeder`, chỉ khi chưa có đơn nào có khóa `dev-seed-...` (chạy lại không nhân đôi). Mật khẩu chung `Canteen@123`.
- Lịch sử 6 ngày cho hai quầy đang hoạt động: đơn hoàn tất (có đơn được ưu đãi), hủy, hết hạn, không nhận. Dùng cho "Đơn của tôi" của `sv@` và `guest@`, và cho thống kê của `manager@`.
- Hôm nay (Cơm Nhà Cô Lan): đủ các trạng thái Pending đến Completed, một yêu cầu hủy và một yêu cầu báo đến muộn đang chờ, cho `staff@` và `manager@`.
- Hao hụt đã xử lý (bán lại, đổ bỏ) và còn một đơn không nhận chưa xử lý; hai khoản bù nền tảng do `admin@` ghi.
- Thông báo cho cả năm tài khoản. Quyền ưu đãi đơn đầu của `sv@` không bị dùng, vẫn thử được.
- Chưa chạy trên MySQL thật (môi trường này không có Docker). Nếu lỗi, xóa DB dev và chạy lại, rồi gửi log cho em.
