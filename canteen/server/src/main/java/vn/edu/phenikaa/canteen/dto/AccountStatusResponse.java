package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDateTime;

import vn.edu.phenikaa.canteen.model.AccountStatus;

/**
 * UC1.9. Khi không đọc được lịch sử, historyAvailable=false và các trường promotion, restriction,
 * violationsLast30Days là null, tuyệt đối không mặc định thành "không vi phạm".
 */
public record AccountStatusResponse(
        String email,
        String pendingEmail,
        boolean emailVerified,
        boolean schoolEmail,
        AccountStatus accountStatus,
        boolean historyAvailable,
        PromotionInfo promotion,
        RestrictionInfo restriction,
        Integer violationsLast30Days) {

    public record PromotionInfo(String status, LocalDateTime expiresAt) {
    }

    public record RestrictionInfo(LocalDateTime endsAt, String reason) {
    }
}
