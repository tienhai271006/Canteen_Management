-- UC10: không nhận món, vi phạm, hạn chế, phản hồi, hao hụt.

CREATE TABLE incident (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    order_id      BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    vendor_id     BIGINT       NOT NULL,
    type          VARCHAR(20)  NOT NULL DEFAULT 'NO_SHOW',
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING_REVIEW', -- PENDING_REVIEW | CONFIRMED | EXEMPTED | REVOKED
    occurred_at   DATETIME(6)  NOT NULL,                       -- mốc tính cửa sổ 30 ngày (BR26)
    decided_by    BIGINT       NULL,
    decided_at    DATETIME(6)  NULL,
    decision_note VARCHAR(255) NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_incident_order (order_id),                   -- mỗi đơn một vi phạm (BR25)
    KEY ix_incident_user_status_time (user_id, status, occurred_at),
    CONSTRAINT fk_incident_order   FOREIGN KEY (order_id)   REFERENCES orders (id),
    CONSTRAINT fk_incident_user    FOREIGN KEY (user_id)    REFERENCES user_account (id),
    CONSTRAINT fk_incident_vendor  FOREIGN KEY (vendor_id)  REFERENCES vendor (id),
    CONSTRAINT fk_incident_decider FOREIGN KEY (decided_by) REFERENCES user_account (id),
    CONSTRAINT ck_incident_status CHECK (status IN ('PENDING_REVIEW', 'CONFIRMED', 'EXEMPTED', 'REVOKED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE restriction (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',        -- ACTIVE | EXPIRED | REVOKED
    starts_at   DATETIME(6)  NOT NULL,
    ends_at     DATETIME(6)  NOT NULL,                         -- 7 ngày (BR31)
    reason      VARCHAR(255) NOT NULL,
    decided_by  BIGINT       NOT NULL,
    revoked_by  BIGINT       NULL,
    revoked_at  DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_restriction_user_status (user_id, status, ends_at),
    KEY ix_restriction_expiry (status, ends_at),
    CONSTRAINT fk_restriction_user    FOREIGN KEY (user_id)    REFERENCES user_account (id),
    CONSTRAINT fk_restriction_decider FOREIGN KEY (decided_by) REFERENCES user_account (id),
    CONSTRAINT fk_restriction_revoker FOREIGN KEY (revoked_by) REFERENCES user_account (id),
    CONSTRAINT ck_restriction_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED')),
    CONSTRAINT ck_restriction_period CHECK (ends_at > starts_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE feedback (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    incident_id BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    content     VARCHAR(1000) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'OPEN',          -- OPEN | RESOLVED
    response    VARCHAR(1000) NULL,
    handled_by  BIGINT       NULL,
    handled_at  DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_feedback_incident (incident_id),
    CONSTRAINT fk_feedback_incident FOREIGN KEY (incident_id) REFERENCES incident (id),
    CONSTRAINT fk_feedback_user     FOREIGN KEY (user_id)     REFERENCES user_account (id),
    CONSTRAINT fk_feedback_handler  FOREIGN KEY (handled_by)  REFERENCES user_account (id),
    CONSTRAINT ck_feedback_status CHECK (status IN ('OPEN', 'RESOLVED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Món đã làm nhưng không được nhận: ghi bán lại hoặc hao hụt, không tự cộng suất (BR15).
CREATE TABLE waste_record (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_id    BIGINT       NOT NULL,
    dish_id     BIGINT       NOT NULL,
    quantity    INT          NOT NULL,
    outcome     VARCHAR(20)  NOT NULL,                         -- RESOLD | WASTED
    note        VARCHAR(255) NULL,
    recorded_by BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_waste_order_dish (order_id, dish_id),
    CONSTRAINT fk_waste_order    FOREIGN KEY (order_id)    REFERENCES orders (id),
    CONSTRAINT fk_waste_dish     FOREIGN KEY (dish_id)     REFERENCES dish (id),
    CONSTRAINT fk_waste_recorder FOREIGN KEY (recorded_by) REFERENCES user_account (id),
    CONSTRAINT ck_waste_outcome CHECK (outcome IN ('RESOLD', 'WASTED')),
    CONSTRAINT ck_waste_qty CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
