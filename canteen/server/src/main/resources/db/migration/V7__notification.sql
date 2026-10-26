-- Thông báo trong ứng dụng và hàng đợi email (outbox).

CREATE TABLE notification (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(40)  NOT NULL,
    title      VARCHAR(160) NOT NULL,
    body       VARCHAR(1000) NOT NULL,
    event_key  VARCHAR(120) NOT NULL,                          -- định danh sự kiện, chống tạo trùng
    ref_type   VARCHAR(30)  NULL,
    ref_id     BIGINT       NULL,
    read_at    DATETIME(6)  NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version    BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_event (event_key),
    KEY ix_notification_user (user_id, created_at),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE email_outbox (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    notification_id BIGINT        NULL,
    user_id         BIGINT        NULL,
    to_email        VARCHAR(255)  NOT NULL,
    subject         VARCHAR(255)  NOT NULL,
    body            TEXT          NOT NULL,
    event_key       VARCHAR(120)  NOT NULL,                    -- thử gửi lại dùng cùng sự kiện, không gửi trùng (BR29)
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING | SENT | FAILED
    attempts        INT           NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_error      VARCHAR(500)  NULL,
    sent_at         DATETIME(6)   NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version         BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_outbox_event (event_key),
    KEY ix_email_outbox_due (status, next_attempt_at),
    CONSTRAINT fk_email_outbox_notification FOREIGN KEY (notification_id) REFERENCES notification (id),
    CONSTRAINT fk_email_outbox_user         FOREIGN KEY (user_id)         REFERENCES user_account (id),
    CONSTRAINT ck_email_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
