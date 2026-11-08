package vn.edu.phenikaa.canteen.model;

/**
 * Khớp ck_entitlement_status. AVAILABLE: dùng được. HELD: đang giữ cho một đơn. USED: đơn đã Completed và Paid.
 * EXPIRED: hết hạn. UNDER_REVIEW: đơn không nhận món, chờ xem xét (BR07).
 */
public enum EntitlementStatus {
    AVAILABLE, HELD, USED, EXPIRED, UNDER_REVIEW
}
