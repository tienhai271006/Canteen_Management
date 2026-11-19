-- Hạn mức theo khung giờ nhận (UC3.4, UC3.5): còn món nhưng khung đầy thì không nhận thêm đơn.
--   capacity     tổng số suất (cộng mọi món) khung này nhận được; NULL = chưa giới hạn, giữ nguyên hành vi cũ.
--   reserved_qty số suất đã giữ cho các đơn chưa bị hủy hay hết hạn. Hủy hoặc hết hạn thì trả, giống bảng quota (BR14).
-- Giữ và trả là MỘT câu UPDATE có điều kiện (xem PickupSlotRepository), CHECK bên dưới là lớp chặn cuối cùng.
ALTER TABLE pickup_slot
    ADD COLUMN capacity     INT NULL                AFTER end_time,
    ADD COLUMN reserved_qty INT NOT NULL DEFAULT 0  AFTER capacity,
    ADD CONSTRAINT ck_pickup_slot_capacity CHECK (capacity IS NULL OR capacity >= 0),
    ADD CONSTRAINT ck_pickup_slot_reserved CHECK (reserved_qty >= 0 AND (capacity IS NULL OR reserved_qty <= capacity));

-- Các đơn đã có trước khi thêm cột: tính lại số suất đang giữ để khớp với thực tế.
UPDATE pickup_slot s
   SET s.reserved_qty = COALESCE((SELECT SUM(oi.quantity)
                                    FROM orders o JOIN order_item oi ON oi.order_id = o.id
                                   WHERE o.pickup_slot_id = s.id AND o.status NOT IN ('CANCELLED', 'EXPIRED')), 0);
