-- UC5-UC7: giỏ hàng, đơn, lịch sử trạng thái, yêu cầu hủy.

CREATE TABLE cart (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    vendor_id   BIGINT      NULL,                              -- giỏ chỉ chứa món của một quầy (BR01)
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version     BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_user (user_id),
    CONSTRAINT fk_cart_user   FOREIGN KEY (user_id)   REFERENCES user_account (id),
    CONSTRAINT fk_cart_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE cart_item (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    cart_id      BIGINT       NOT NULL,
    menu_item_id BIGINT       NOT NULL,
    quantity     INT          NOT NULL,
    note         VARCHAR(255) NULL,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_item (cart_id, menu_item_id),
    CONSTRAINT fk_cart_item_cart      FOREIGN KEY (cart_id)      REFERENCES cart (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_item_menu_item FOREIGN KEY (menu_item_id) REFERENCES menu_item (id),
    CONSTRAINT ck_cart_item_qty CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE orders (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    order_code         VARCHAR(30)  NOT NULL,                  -- mã hiển thị cho khách
    customer_id        BIGINT       NOT NULL,
    vendor_id          BIGINT       NOT NULL,
    pickup_slot_id     BIGINT       NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    payment_status     VARCHAR(20)  NOT NULL DEFAULT 'UNPAID', -- thanh toán tại quầy (BR20)
    subtotal_amount    BIGINT       NOT NULL,                  -- giá, giảm, phải trả: chốt lúc tạo đơn (BR04)
    discount_amount    BIGINT       NOT NULL DEFAULT 0,
    total_amount       BIGINT       NOT NULL,
    entitlement_id     BIGINT       NULL,                      -- FK thêm ở V5
    idempotency_key    VARCHAR(64)  NOT NULL,                  -- chống nhấn hai lần (BR36)
    note               VARCHAR(255) NULL,
    pending_expires_at DATETIME(6)  NULL,                      -- hạn xác nhận 5 phút (BR13)
    confirmed_at       DATETIME(6)  NULL,
    ready_at           DATETIME(6)  NULL,
    pickup_deadline    DATETIME(6)  NULL,                      -- BR22
    completed_at       DATETIME(6)  NULL,
    closed_reason      VARCHAR(255) NULL,
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version            BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_code (order_code),
    UNIQUE KEY uk_orders_idempotency (customer_id, idempotency_key),
    KEY ix_orders_vendor_status_created (vendor_id, status, created_at),   -- thống kê (Pha 11) và màn hình quầy
    KEY ix_orders_customer_created (customer_id, created_at),
    KEY ix_orders_pending_expiry (status, pending_expires_at),             -- PendingExpiryJob
    KEY ix_orders_pickup_deadline (status, pickup_deadline),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id)    REFERENCES user_account (id),
    CONSTRAINT fk_orders_vendor   FOREIGN KEY (vendor_id)      REFERENCES vendor (id),
    CONSTRAINT fk_orders_slot     FOREIGN KEY (pickup_slot_id) REFERENCES pickup_slot (id),
    CONSTRAINT ck_orders_status CHECK (status IN
        ('PENDING', 'CONFIRMED', 'PREPARING', 'READY', 'COMPLETED', 'CANCELLED', 'EXPIRED', 'NOT_COLLECTED')),
    CONSTRAINT ck_orders_payment CHECK (payment_status IN ('UNPAID', 'PAID')),
    CONSTRAINT ck_orders_amounts CHECK (subtotal_amount >= 0 AND discount_amount >= 0
        AND total_amount >= 0 AND total_amount = subtotal_amount - discount_amount)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE order_item (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_id     BIGINT       NOT NULL,
    menu_item_id BIGINT       NOT NULL,
    dish_id      BIGINT       NOT NULL,
    dish_name    VARCHAR(120) NOT NULL,                        -- chụp lại tên và giá lúc đặt (BR04)
    unit_price   BIGINT       NOT NULL,
    quantity     INT          NOT NULL,
    line_total   BIGINT       NOT NULL,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_order_item_order (order_id),
    CONSTRAINT fk_order_item_order     FOREIGN KEY (order_id)     REFERENCES orders (id),
    CONSTRAINT fk_order_item_menu_item FOREIGN KEY (menu_item_id) REFERENCES menu_item (id),
    CONSTRAINT fk_order_item_dish      FOREIGN KEY (dish_id)      REFERENCES dish (id),
    CONSTRAINT ck_order_item_qty CHECK (quantity > 0 AND unit_price >= 0 AND line_total = unit_price * quantity)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Chỉ ghi thêm (NFR03).
CREATE TABLE order_status_history (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_id    BIGINT       NOT NULL,
    from_status VARCHAR(20)  NULL,
    to_status   VARCHAR(20)  NOT NULL,
    actor_id    BIGINT       NULL,                             -- NULL = hệ thống (job)
    actor_type  VARCHAR(20)  NOT NULL,                         -- CUSTOMER | STAFF | MANAGER | ADMIN | SYSTEM
    reason      VARCHAR(255) NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY ix_order_status_history_order (order_id, created_at),
    CONSTRAINT fk_osh_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_osh_actor FOREIGN KEY (actor_id) REFERENCES user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE cancel_request (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    order_id       BIGINT       NOT NULL,
    requested_by   BIGINT       NOT NULL,
    reason         VARCHAR(255) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',    -- PENDING | APPROVED | REJECTED | WITHDRAWN
    handled_by     BIGINT       NULL,
    handled_at     DATETIME(6)  NULL,
    handle_reason  VARCHAR(255) NULL,
    handle_channel VARCHAR(20)  NULL,                          -- APP | PHONE (BR19)
    -- Mỗi đơn chỉ có tối đa một yêu cầu đang chờ (BR17); NULL không vi phạm UNIQUE.
    pending_order_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN order_id ELSE NULL END) STORED,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version        BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cancel_request_pending (pending_order_id),
    KEY ix_cancel_request_order (order_id),
    CONSTRAINT fk_cancel_request_order   FOREIGN KEY (order_id)     REFERENCES orders (id),
    CONSTRAINT fk_cancel_request_user    FOREIGN KEY (requested_by) REFERENCES user_account (id),
    CONSTRAINT fk_cancel_request_handler FOREIGN KEY (handled_by)   REFERENCES user_account (id),
    CONSTRAINT ck_cancel_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
