-- UC8: ưu đãi đơn đầu.

CREATE TABLE campaign (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    name                   VARCHAR(120) NOT NULL,
    discount_percent       INT          NOT NULL,              -- 50 (BR05)
    max_discount_amount    BIGINT       NOT NULL,              -- 30000
    min_order_amount       BIGINT       NOT NULL,              -- 25000
    entitlement_valid_days INT          NOT NULL DEFAULT 7,
    starts_at              DATETIME(6)  NOT NULL,
    ends_at                DATETIME(6)  NOT NULL,
    active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version                BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_campaign_percent CHECK (discount_percent BETWEEN 1 AND 100),
    CONSTRAINT ck_campaign_period  CHECK (ends_at > starts_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- BR06: mỗi tài khoản và mỗi email trường chỉ có một lượt trên toàn nền tảng.
-- Hai UNIQUE bên dưới là hàng rào cuối: đổi email hay tạo lại tài khoản đều không có lượt mới.
CREATE TABLE promotion_entitlement (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    campaign_id  BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    school_email VARCHAR(255) NOT NULL,                        -- chuẩn hóa chữ thường, giữ nguyên kể cả khi đổi email
    status       VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',    -- AVAILABLE | HELD | USED | EXPIRED | UNDER_REVIEW
    order_id     BIGINT       NULL,                            -- đơn đang giữ/đã dùng quyền
    granted_at   DATETIME(6)  NOT NULL,
    expires_at   DATETIME(6)  NOT NULL,
    used_at      DATETIME(6)  NULL,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_entitlement_user (user_id),
    UNIQUE KEY uk_entitlement_school_email (school_email),
    KEY ix_entitlement_expiry (status, expires_at),
    CONSTRAINT fk_entitlement_campaign FOREIGN KEY (campaign_id) REFERENCES campaign (id),
    CONSTRAINT fk_entitlement_user     FOREIGN KEY (user_id)     REFERENCES user_account (id),
    CONSTRAINT fk_entitlement_order    FOREIGN KEY (order_id)    REFERENCES orders (id),
    CONSTRAINT ck_entitlement_status CHECK (status IN ('AVAILABLE', 'HELD', 'USED', 'EXPIRED', 'UNDER_REVIEW'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_entitlement FOREIGN KEY (entitlement_id) REFERENCES promotion_entitlement (id);
