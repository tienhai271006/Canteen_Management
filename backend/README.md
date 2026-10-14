# Canteen Backend

Express + TypeScript + Prisma (PostgreSQL). Đã làm: **UC1 Tài khoản, UC2 Điểm bán, UC3 Món ăn & thực đơn**.

## Chạy lần đầu

```bash
# 1. Bật PostgreSQL (hoặc dùng docker-compose ở thư mục gốc dự án sau này)
docker run -d --name canteen-db -p 5432:5432 \
  -e POSTGRES_USER=canteen -e POSTGRES_PASSWORD=canteen -e POSTGRES_DB=canteen \
  postgres:16

# 2. Cài đặt
cp .env.example .env          # sửa JWT_SECRET
npm install
npx prisma migrate dev --name init   # tạo bảng

# 3. Ràng buộc không âm / không bán vượt hạn mức (BR12) – làm 1 lần
npx prisma migrate dev --create-only --name quota_checks
#   dán nội dung prisma/manual/quota_checks.sql vào migration.sql vừa tạo, rồi:
npx prisma migrate dev

# 4. Dữ liệu mẫu + chạy
npm run db:seed
npm run dev                   # http://localhost:4000/api/v1/health
```

Chưa cấu hình SMTP thì mã OTP được in ra console của server.

## Quy ước API

- Prefix `/api/v1`, header `Authorization: Bearer <token>`.
- Thành công: `{ "data": ... }`. Lỗi: `{ "error": { "code", "message", "details?" } }`.

## Endpoint đã có

| Nhóm | Endpoint |
|---|---|
| Auth | `POST /auth/register` · `/auth/login` · `/auth/logout` · `/auth/verify-email` · `/auth/resend-otp` · `/auth/change-password` |
| Hồ sơ | `GET/PATCH /users/me` · `GET /users/me/status` |
| Quầy | `POST /vendors` · `GET /vendors/mine` · `GET/PATCH /vendors/:id` · `POST /vendors/:id/suspend` · `/resume` |
| Nhân sự | `GET/POST /vendors/:id/staff` · `PATCH/DELETE /vendors/:id/staff/:userId` |
| Admin duyệt quầy | `GET /admin/vendors` · `POST /admin/vendors/:id/approve` · `/reject` |
| Món | `GET/POST /vendors/:id/dishes` · `GET/PATCH /dishes/:dishId` · `PATCH .../price` · `PATCH .../status` |
| Ca & khung nhận | `GET /vendors/:id/sessions` · `PUT /vendors/:id/sessions/:BREAKFAST\|LUNCH\|DINNER` |
| Thực đơn | `GET /vendors/:id/menus?date&session` · `POST /vendors/:id/menus/items` · `DELETE .../items/:menuItemId` |
| Hạn mức | `PATCH /vendors/:id/quotas/:menuItemId` · `POST .../transfer` · `POST .../leftover` · `GET .../adjustments` |

## Tài khoản mẫu (sau `db:seed`, mật khẩu `Canteen@123`)

`admin@phenikaa-uni.edu.vn` · `quanly.d6@phenikaa-uni.edu.vn` · `nhanvien.d6@phenikaa-uni.edu.vn` ·
`quanly.basao@phenikaa-uni.edu.vn` · `quanly.lan@phenikaa-uni.edu.vn` · `sv.demo@phenikaa-uni.edu.vn`
