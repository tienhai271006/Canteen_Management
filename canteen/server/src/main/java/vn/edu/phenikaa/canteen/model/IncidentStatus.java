package vn.edu.phenikaa.canteen.model;

/**
 * Kết luận về sự việc, tách khỏi trạng thái đơn (BR24). Chỉ CONFIRMED mới được cộng vào ngưỡng (BR26).
 * PENDING_REVIEW: chờ xem xét. EXEMPTED: miễn. REVOKED: đã xác nhận nhưng sau đó bị hủy kết luận (giữ lịch sử).
 */
public enum IncidentStatus {
    PENDING_REVIEW, CONFIRMED, EXEMPTED, REVOKED
}
