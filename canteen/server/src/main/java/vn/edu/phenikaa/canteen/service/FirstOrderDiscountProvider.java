package vn.edu.phenikaa.canteen.service;

import java.util.Optional;

/**
 * Cổng nối ưu đãi đơn đầu (UC8.3 mở rộng UC5.3). Pha 6 chỉ định nghĩa giao diện; Pha 8 viết bean
 * ({@code PromotionService} implements giao diện này). Chưa có bean thì đơn không có khoản giảm.
 */
public interface FirstOrderDiscountProvider {

    /** Điều khoản giảm giá lấy từ chiến dịch (BR05): giảm theo phần trăm, có trần, và có mức đơn tối thiểu. */
    record Terms(Long entitlementId, int percent, long maxDiscountAmount, long minOrderAmount) {
    }

    /** Quyền ưu đãi khả dụng của khách (nếu có). KHÔNG giữ quyền, chỉ để tính tiền. */
    Optional<Terms> findUsable(Long customerId);

    /**
     * Giữ quyền cho đơn, chạy trong transaction của {@code placeOrder}. Phải là câu UPDATE có điều kiện
     * (chỉ khi còn AVAILABLE) và ném {@code PROMOTION_NOT_ELIGIBLE} nếu quyền vừa bị đơn khác giữ.
     */
    void hold(Long entitlementId, Long orderId);

    // ---- Vòng đời quyền sau khi đơn đã giữ quyền. OrderService.transition gọi các hàm này (BR07). ----
    // Mặc định không làm gì, để thiếu bean thì đơn vẫn chạy bình thường. Đều idempotent: chạy lặp không đổi thêm.

    /** Đơn Completed và Paid: quyền sang Đã dùng. */
    default void consume(Long orderId) {
    }

    /** Đơn Cancelled hoặc Expired: trả quyền về Khả dụng nếu còn hạn, quá hạn thì Hết hạn. */
    default void release(Long orderId) {
    }

    /** Đơn NotCollected: quyền sang Chờ xem xét cho đến khi có kết luận (Pha 10). */
    default void holdForReview(Long orderId) {
    }

    /** Sự việc không nhận món được kết luận (Pha 10): miễn thì trả quyền nếu còn hạn, xác nhận lỗi khách thì mất quyền. */
    default void resolveReview(Long orderId, boolean restore) {
    }

    /** Hủy kết luận vi phạm (BR32): khôi phục quyền đã mất vì sự việc đó nếu quyền vẫn còn hạn. */
    default void reinstate(Long orderId) {
    }
}
