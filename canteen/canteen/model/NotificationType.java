package vn.edu.phenikaa.canteen.model;

/** Loại thông báo (cột notification.type). */
public enum NotificationType {
    ORDER_CONFIRMED,
    ORDER_READY,
    ORDER_CANCELLED,
    ORDER_EXPIRED,
    CANCEL_REQUEST_RECEIVED,   // gửi quầy
    CANCEL_REQUEST_DECIDED,    // gửi khách
    LATE_ARRIVAL_RECEIVED,     // gửi quầy
    LATE_ARRIVAL_DECIDED,      // gửi khách
    RESTRICTION_ENDED,
    // Pha 10
    NO_SHOW_RECORDED,          // gửi khách: đơn quá hạn đã đóng, chưa phải kết luận vi phạm
    INCIDENT_PENDING_REVIEW,   // gửi quầy: có sự việc cần xem xét
    INCIDENT_REMINDER,         // gửi khách: vi phạm đã xác nhận lần 1
    INCIDENT_WARNING,          // gửi khách: lần 2
    INCIDENT_ESCALATED,        // gửi khách và quản trị viên: từ lần 3, chuyển xem xét
    INCIDENT_EXEMPTED,         // gửi khách: sự việc được miễn
    INCIDENT_REVOKED,          // gửi khách: hủy kết luận vi phạm
    RESTRICTION_STARTED,       // gửi khách
    RESTRICTION_REVOKED,       // gửi khách
    RESTRICTION_REVIEW_NEEDED, // gửi quản trị viên: sửa ghi nhận sai làm hạn chế cần xem lại (BR32)
    FEEDBACK_RECEIVED,         // gửi quầy và quản trị viên
    FEEDBACK_RESOLVED          // gửi khách
}
