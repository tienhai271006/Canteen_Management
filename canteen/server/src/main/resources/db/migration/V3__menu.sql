-- UC3/UC4: món, thực đơn theo ngày và buổi, khung nhận, hạn mức.

CREATE TABLE dish (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    vendor_id   BIGINT       NOT NULL,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    category    VARCHAR(30)  NOT NULL,                         -- MAIN | SIDE | DRINK | SNACK ...
    price       BIGINT       NOT NULL,                         -- đồng; giá mới chỉ áp dụng cho đơn mới (BR04)
    image_path  VARCHAR(255) NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,            -- FALSE = ẩn món
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_dish_vendor_active (vendor_id, active),
    CONSTRAINT fk_dish_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (id),
    CONSTRAINT ck_dish_price CHECK (price >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE menu (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    vendor_id         BIGINT      NOT NULL,
    menu_date         DATE        NOT NULL,
    meal_session      VARCHAR(20) NOT NULL,                    -- MORNING | LUNCH | AFTERNOON
    order_cutoff_time TIME        NOT NULL,                    -- giờ đóng nhận đơn của ca (BR02)
    status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT',    -- DRAFT | PUBLISHED
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version           BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_menu_vendor_date_session (vendor_id, menu_date, meal_session),
    KEY ix_menu_date (menu_date, meal_session),
    CONSTRAINT fk_menu_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (id),
    CONSTRAINT ck_menu_session CHECK (meal_session IN ('MORNING', 'LUNCH', 'AFTERNOON')),
    CONSTRAINT ck_menu_status  CHECK (status IN ('DRAFT', 'PUBLISHED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE menu_item (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    menu_id    BIGINT      NOT NULL,
    dish_id    BIGINT      NOT NULL,
    available  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version    BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_menu_item (menu_id, dish_id),
    CONSTRAINT fk_menu_item_menu FOREIGN KEY (menu_id) REFERENCES menu (id),
    CONSTRAINT fk_menu_item_dish FOREIGN KEY (dish_id) REFERENCES dish (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE pickup_slot (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    menu_id    BIGINT      NOT NULL,
    start_time TIME        NOT NULL,
    end_time   TIME        NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version    BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pickup_slot (menu_id, start_time),
    CONSTRAINT fk_pickup_slot_menu FOREIGN KEY (menu_id) REFERENCES menu (id),
    CONSTRAINT ck_pickup_slot_time CHECK (end_time > start_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Hạn mức theo (quầy, món, ngày, buổi) vì menu_item đã gắn món với menu của một ngày và buổi.
-- Số suất đặt online còn lại = total_slots - counter_slots - reserved_slots.
-- CHECK là lớp bảo vệ cuối cùng cho BR12: dù code sai cũng không thể bán vượt.
CREATE TABLE quota (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    menu_item_id   BIGINT      NOT NULL,
    total_slots    INT         NOT NULL,
    reserved_slots INT         NOT NULL DEFAULT 0,             -- suất đang giữ/đã bán qua đặt trước
    counter_slots  INT         NOT NULL DEFAULT 0,             -- phần dành cho khách tại quầy (BR33)
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version        BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_quota_menu_item (menu_item_id),
    CONSTRAINT fk_quota_menu_item FOREIGN KEY (menu_item_id) REFERENCES menu_item (id),
    CONSTRAINT ck_quota_non_negative CHECK (total_slots >= 0 AND reserved_slots >= 0 AND counter_slots >= 0),
    CONSTRAINT ck_quota_no_oversell  CHECK (reserved_slots + counter_slots <= total_slots)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Lịch sử điều chỉnh/điều chuyển hạn mức (BR33, NFR03). Chỉ ghi thêm.
CREATE TABLE quota_adjustment (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    quota_id      BIGINT       NOT NULL,
    delta_total   INT          NOT NULL DEFAULT 0,
    delta_counter INT          NOT NULL DEFAULT 0,
    reason        VARCHAR(255) NOT NULL,
    actor_id      BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY ix_quota_adjustment_quota (quota_id),
    CONSTRAINT fk_quota_adjustment_quota FOREIGN KEY (quota_id) REFERENCES quota (id),
    CONSTRAINT fk_quota_adjustment_actor FOREIGN KEY (actor_id) REFERENCES user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
