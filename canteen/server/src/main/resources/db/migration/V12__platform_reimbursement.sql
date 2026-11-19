-- Quy tắc ưu đãi đơn đầu: nền tảng chịu app.promotion.platform-share-percent (mặc định 70%) mỗi khoản giảm của đơn
-- Completed và Paid, quầy chịu phần còn lại. Bảng này ghi các lần nền tảng ĐÃ chuyển khoản bù cho quầy (chỉ ghi thêm).
-- for_date là NGÀY PHỤC VỤ mà khoản chuyển này bù cho, cùng mốc thời gian với thống kê (UC9).
CREATE TABLE platform_reimbursement (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    vendor_id   BIGINT       NOT NULL,
    for_date    DATE         NOT NULL,
    amount      BIGINT       NOT NULL,
    note        VARCHAR(255) NULL,
    recorded_by BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_reimb_vendor_date (vendor_id, for_date),
    CONSTRAINT fk_reimb_vendor   FOREIGN KEY (vendor_id)   REFERENCES vendor (id),
    CONSTRAINT fk_reimb_recorder FOREIGN KEY (recorded_by) REFERENCES user_account (id),
    CONSTRAINT ck_reimb_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
