// Hằng số chính sách – tương ứng bảng tham số ở mục 1.1.1 của tài liệu.
// Không import env ở đây để có thể dùng cả trong seed/test.

export const TIMEZONE = 'Asia/Ho_Chi_Minh';

export const POLICY = {
  FIRST_ORDER_DISCOUNT_RATE: 0.5, // giảm 50%
  FIRST_ORDER_DISCOUNT_MAX: 30_000, // tối đa 30.000đ
  FIRST_ORDER_MIN_ORDER: 25_000, // đơn tối thiểu 25.000đ
  FIRST_ORDER_VOUCHER_TTL_DAYS: 7,
  VIOLATION_WINDOW_DAYS: 30,
  RESTRICTION_DAYS: 7,
  VIOLATION_THRESHOLD: { REMINDER: 1, WARNING: 2, REVIEW: 3 },
} as const;

export const PRICE_LIMITS = { MIN: 1_000, MAX: 10_000_000 } as const;

export const OTP_LENGTH = 6;
export const OTP_RESEND_COOLDOWN_SECONDS = 60;
