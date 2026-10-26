-- UC2: khu vực, điểm bán, nhân sự quầy.

CREATE TABLE area (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_area_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE vendor (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    area_id          BIGINT       NOT NULL,
    name             VARCHAR(120) NOT NULL,
    description      VARCHAR(500) NULL,
    pickup_location  VARCHAR(255) NULL,                        -- địa điểm nhận
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | ACTIVE | SUSPENDED
    status_reason    VARCHAR(255) NULL,
    approved_by      BIGINT       NULL,
    approved_at      DATETIME(6)  NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version          BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_vendor_area_status (area_id, status),
    CONSTRAINT fk_vendor_area     FOREIGN KEY (area_id)     REFERENCES area (id),
    CONSTRAINT fk_vendor_approver FOREIGN KEY (approved_by) REFERENCES user_account (id),
    CONSTRAINT ck_vendor_status   CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Nguồn sự thật cho NFR01/BR03: người dùng chỉ truy cập quầy có dòng active ở đây.
CREATE TABLE vendor_member (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    vendor_id   BIGINT      NOT NULL,
    member_role VARCHAR(20) NOT NULL,                          -- STAFF | MANAGER
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version     BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vendor_member (user_id, vendor_id),
    KEY ix_vendor_member_vendor (vendor_id),
    CONSTRAINT fk_vendor_member_user   FOREIGN KEY (user_id)   REFERENCES user_account (id),
    CONSTRAINT fk_vendor_member_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (id),
    CONSTRAINT ck_vendor_member_role   CHECK (member_role IN ('STAFF', 'MANAGER'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
