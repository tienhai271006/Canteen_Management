package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDateTime;

import vn.edu.phenikaa.canteen.model.Campaign;

/**
 * UC8.2: điều kiện, hạn dùng và trạng thái quyền lợi của người đang đăng nhập.
 * {@code status} là trạng thái hiệu lực: quyền AVAILABLE nhưng đã quá hạn được báo là EXPIRED ngay cả khi job
 * dọn dẹp chưa chạy. {@code status} null nghĩa là chưa có quyền; khi đó {@code notice} nói lý do.
 */
public record PromotionOverviewResponse(
        String status,
        LocalDateTime grantedAt,
        LocalDateTime expiresAt,
        LocalDateTime usedAt,
        Terms terms,
        String notice) {

    public record Terms(String campaignName, int discountPercent, long maxDiscountAmount, long minOrderAmount,
                        int validDays) {
        public static Terms from(Campaign c) {
            return new Terms(c.getName(), c.getDiscountPercent(), c.getMaxDiscountAmount(), c.getMinOrderAmount(),
                    c.getEntitlementValidDays());
        }
    }
}
