-- UC1: tài khoản và xác thực. Quy ước chung: mọi bảng kế thừa BaseEntity có id, created_at, updated_at, version.
-- Bảng chỉ ghi thêm (append-only) không có updated_at/version và không kế thừa BaseEntity.

CREATE TABLE user_account (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    email             VARCHAR(255) NOT NULL,
    pending_email     VARCHAR(255) NULL,                       -- email mới chờ xác minh (UC1.8)
    full_name         VARCHAR(120) NOT NULL,
    password_hash     VARCHAR(100) NOT NULL,                   -- BCrypt, không lưu mật khẩu rõ (NFR04)
    role              VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | LOCKED (khóa đăng nhập)
    email_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verified_at DATETIME(6)  NULL,
    student_code      VARCHAR(30)  NULL,
    phone             VARCHAR(20)  NULL,
    last_login_at     DATETIME(6)  NULL,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version           BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_email (email),
    UNIQUE KEY uk_user_student_code (student_code),
    CONSTRAINT ck_user_role   CHECK (role IN ('CUSTOMER', 'VENDOR_STAFF', 'VENDOR_MANAGER', 'ADMIN')),
    CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE', 'LOCKED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE email_verification (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    email          VARCHAR(255) NOT NULL,                      -- địa chỉ nhận mã, chính là địa chỉ cần xác minh
    purpose        VARCHAR(20)  NOT NULL,                      -- VERIFY | CHANGE_EMAIL
    code_hash      VARCHAR(64)  NOT NULL,                      -- HMAC-SHA256 của OTP, không lưu OTP rõ
    expires_at     DATETIME(6)  NOT NULL,
    attempts       INT          NOT NULL DEFAULT 0,            -- số lần nhập sai (NFR04)
    consumed_at    DATETIME(6)  NULL,                          -- dùng một lần
    invalidated_at DATETIME(6)  NULL,                          -- bị thay bằng mã mới / đổi địa chỉ
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version        BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_email_verification_user (user_id, created_at),
    CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE refresh_token (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,                           -- SHA-256 của token
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version    BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY ix_refresh_token_user (user_id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
