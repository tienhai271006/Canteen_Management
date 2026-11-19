-- Pha 10: không nhận món, vi phạm, hạn chế, phản hồi, xử lý món đã làm.

-- UC3.7: một món có thể xử lý nhiều đợt (vd. 1 bán lại + 1 hao hụt, hoặc xử lý dần), nên bỏ UNIQUE (order_id, dish_id).
-- Tổng số đã xử lý không vượt số lượng của dòng đơn do WasteHandlingService kiểm tra dưới khóa dòng đơn.
-- Thêm chỉ mục thay thế trong CÙNG câu ALTER để khóa ngoại fk_waste_order luôn có chỉ mục.
ALTER TABLE waste_record
    ADD KEY ix_waste_order_dish (order_id, dish_id),
    DROP INDEX uk_waste_order_dish;

-- BR35: mỗi sự việc chỉ có tối đa một phản hồi đang xử lý (NULL không vi phạm UNIQUE).
ALTER TABLE feedback
    ADD COLUMN open_incident_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'OPEN' THEN incident_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_feedback_open (open_incident_id);

-- BR31: mỗi khách chỉ có tối đa một hạn chế đang hiệu lực, hai quản trị viên bấm cùng lúc thì chỉ một bên thành công.
ALTER TABLE restriction
    ADD COLUMN active_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN user_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_restriction_active (active_user_id);
