-- Ràng buộc ở mức CSDL cho BR12 / NFR02: số suất không bao giờ âm, không bán vượt hạn mức.
-- Cách dùng: sau migration đầu tiên chạy
--   npx prisma migrate dev --create-only --name quota_checks
-- rồi dán nội dung dưới đây vào file migration.sql vừa tạo và chạy `npx prisma migrate dev`.

ALTER TABLE "Quota" ADD CONSTRAINT quota_non_negative
  CHECK ("onlineTotal" >= 0 AND "walkInTotal" >= 0 AND "reserved" >= 0
         AND "sold" >= 0 AND "resold" >= 0 AND "wasted" >= 0);

ALTER TABLE "Quota" ADD CONSTRAINT quota_not_oversold
  CHECK ("reserved" + "sold" <= "onlineTotal");
