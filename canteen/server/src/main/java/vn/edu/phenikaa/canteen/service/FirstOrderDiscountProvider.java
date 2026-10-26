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
}
