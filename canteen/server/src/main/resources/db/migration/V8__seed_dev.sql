-- DỮ LIỆU MẪU CHO PHÁT TRIỂN. Không đưa vào môi trường thật.
-- Tài khoản thử KHÔNG nằm ở đây: DevAccountSeeder (profile dev) tạo chúng bằng PasswordEncoder
-- để không phải lưu hash trong repo. Phân công nhân sự quầy cũng do DevAccountSeeder làm.

INSERT INTO area (code, name, description) VALUES
    ('D6',    'Canteen D6',    'Canteen tòa D6'),
    ('BASAO', 'Nhà ăn Ba Sao', 'Nhà ăn Ba Sao với nhiều quầy độc lập');

INSERT INTO vendor (area_id, name, description, pickup_location, status, approved_at)
SELECT a.id, 'Cơm Nhà Cô Lan', 'Cơm phần, món mặn mỗi ngày', 'Quầy 1, canteen D6', 'ACTIVE', NOW(6) FROM area a WHERE a.code = 'D6';
INSERT INTO vendor (area_id, name, description, pickup_location, status, approved_at)
SELECT a.id, 'Bún Phở Ba Sao', 'Bún, phở nóng', 'Quầy 3, nhà ăn Ba Sao', 'ACTIVE', NOW(6) FROM area a WHERE a.code = 'BASAO';
INSERT INTO vendor (area_id, name, description, pickup_location, status)
SELECT a.id, 'Trà Sữa Lán Xanh', 'Đồ uống (đang chờ duyệt)', 'Lán xanh cổng sau', 'PENDING' FROM area a WHERE a.code = 'D6';

INSERT INTO dish (vendor_id, name, description, category, price)
SELECT v.id, d.name, d.descr, d.category, d.price FROM vendor v JOIN (
    SELECT 'Cơm Nhà Cô Lan' vname, 'Cơm gà xối mỡ' name, 'Gà chiên giòn, cơm trắng' descr, 'MAIN' category, 35000 price
    UNION ALL SELECT 'Cơm Nhà Cô Lan', 'Cơm sườn nướng', 'Sườn nướng mật ong', 'MAIN', 40000
    UNION ALL SELECT 'Cơm Nhà Cô Lan', 'Canh rau', 'Canh rau theo ngày', 'SIDE', 10000
    UNION ALL SELECT 'Cơm Nhà Cô Lan', 'Trà đá', 'Ly lớn', 'DRINK', 5000
    UNION ALL SELECT 'Bún Phở Ba Sao', 'Bún bò Huế', 'Bún bò giò heo', 'MAIN', 40000
    UNION ALL SELECT 'Bún Phở Ba Sao', 'Phở bò', 'Phở bò tái chín', 'MAIN', 45000
    UNION ALL SELECT 'Bún Phở Ba Sao', 'Nước cam', 'Cam vắt', 'DRINK', 15000
) d ON d.vname = v.name;

-- Thực đơn buổi trưa của hôm nay và ngày mai cho các quầy đang hoạt động.
INSERT INTO menu (vendor_id, menu_date, meal_session, order_cutoff_time, status)
SELECT v.id, d.dt, 'LUNCH', '10:30:00', 'PUBLISHED'
FROM vendor v CROSS JOIN (SELECT CURDATE() dt UNION ALL SELECT CURDATE() + INTERVAL 1 DAY) d
WHERE v.status = 'ACTIVE';

INSERT INTO pickup_slot (menu_id, start_time, end_time)
SELECT m.id, s.st, s.en FROM menu m CROSS JOIN (
    SELECT '11:00:00' st, '11:30:00' en
    UNION ALL SELECT '11:30:00', '12:00:00'
    UNION ALL SELECT '12:00:00', '12:30:00'
) s;

INSERT INTO menu_item (menu_id, dish_id)
SELECT m.id, dh.id FROM menu m JOIN dish dh ON dh.vendor_id = m.vendor_id;

-- 30 suất mỗi món, trong đó 5 suất dành cho khách tại quầy (BR33).
INSERT INTO quota (menu_item_id, total_slots, reserved_slots, counter_slots)
SELECT mi.id, 30, 0, 5 FROM menu_item mi;

INSERT INTO campaign (name, discount_percent, max_discount_amount, min_order_amount,
                      entitlement_valid_days, starts_at, ends_at, active)
VALUES ('Ưu đãi đơn đầu', 50, 30000, 25000, 7, NOW(6) - INTERVAL 1 DAY, NOW(6) + INTERVAL 180 DAY, TRUE);
