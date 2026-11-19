-- UC6.4, UC7.7: yêu cầu hỗ trợ đến muộn. Quầy duyệt thì hạn nhận mới được ghi vào đơn (BR22); yêu cầu đang chờ không tự gia hạn.

CREATE TABLE late_arrival_request (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    order_id            BIGINT       NOT NULL,
    requested_by        BIGINT       NOT NULL,
    reason              VARCHAR(255) NOT NULL,
    proposed_arrival_at DATETIME(6)  NOT NULL,                 -- giờ đến khách đề xuất
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING | APPROVED | REJECTED | WITHDRAWN
    approved_deadline   DATETIME(6)  NULL,                     -- hạn nhận quầy đồng ý (chỉ khi APPROVED)
    handled_by          BIGINT       NULL,
    handled_at          DATETIME(6)  NULL,
    handle_reason       VARCHAR(255) NULL,
    handle_channel      VARCHAR(20)  NULL,                     -- APP | PHONE (BR19)
    -- Mỗi đơn chỉ có tối đa một yêu cầu đang chờ; NULL không vi phạm UNIQUE.
    pending_order_id    BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN order_id ELSE NULL END) STORED,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version             BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_late_arrival_pending (pending_order_id),
    KEY ix_late_arrival_order (order_id),
    CONSTRAINT fk_late_arrival_order   FOREIGN KEY (order_id)     REFERENCES orders (id),
    CONSTRAINT fk_late_arrival_user    FOREIGN KEY (requested_by) REFERENCES user_account (id),
    CONSTRAINT fk_late_arrival_handler FOREIGN KEY (handled_by)   REFERENCES user_account (id),
    CONSTRAINT ck_late_arrival_status  CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT ck_late_arrival_channel CHECK (handle_channel IS NULL OR handle_channel IN ('APP', 'PHONE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
