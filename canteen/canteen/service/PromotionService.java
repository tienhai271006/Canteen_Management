package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.dto.PromotionOverviewResponse;
import vn.edu.phenikaa.canteen.model.Campaign;
import vn.edu.phenikaa.canteen.model.EntitlementStatus;
import vn.edu.phenikaa.canteen.model.PromotionEntitlement;
import vn.edu.phenikaa.canteen.model.UserAccount;
import vn.edu.phenikaa.canteen.repository.CampaignRepository;
import vn.edu.phenikaa.canteen.repository.PromotionEntitlementRepository;
import vn.edu.phenikaa.canteen.repository.UserAccountRepository;

/**
 * UC8: ưu đãi đơn đầu. Là bean của {@link FirstOrderDiscountProvider}, nên {@code PricingService} tự nhận được
 * (UC8.3 mở rộng UC5.3) mà không phải sửa công thức giá.
 * <p>
 * Vòng đời quyền (BR07): AVAILABLE, giữ cho một đơn thành HELD, rồi USED khi đơn Completed và Paid; hủy hợp lệ thì
 * trả về AVAILABLE nếu còn hạn; không nhận món thì UNDER_REVIEW. Mọi bước ghi là một câu UPDATE có điều kiện trên
 * trạng thái cũ, nên hai đơn tranh một quyền chỉ một bên thắng (BR36) và chạy lặp không đổi thêm.
 */
@Service
@RequiredArgsConstructor
public class PromotionService implements FirstOrderDiscountProvider {

    private final PromotionEntitlementRepository entitlements;
    private final CampaignRepository campaigns;
    private final UserAccountRepository users;
    private final SchoolEmailPolicy emailPolicy;
    private final JdbcClient jdbc;
    private final Clock clock;

    // ---- cấp quyền (UC1.2 gọi sau khi xác minh email trường) ----

    /**
     * Cấp quyền cho tài khoản vừa xác minh email trường nếu có chiến dịch đang hiệu lực (BR05). Mỗi tài khoản và mỗi
     * email trường chỉ một lượt trên toàn nền tảng (BR06): hai UNIQUE trong DB là hàng rào cuối, nên đổi email hay
     * xác minh lại không cấp thêm. Dùng INSERT ... ON DUPLICATE KEY để trùng thì bỏ qua mà không làm hỏng
     * transaction xác minh đang chạy.
     */
    @Transactional
    public void grantIfEligible(UserAccount user) {
        String email = emailPolicy.normalize(user.getEmail());
        if (!user.isEmailVerified() || !emailPolicy.isSchoolEmail(email)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        campaigns.findFirstByActiveTrueAndStartsAtLessThanEqualAndEndsAtAfterOrderByIdDesc(now, now).ifPresent(c ->
                jdbc.sql("""
                        INSERT INTO promotion_entitlement (campaign_id, user_id, school_email, status, granted_at, expires_at)
                        VALUES (:campaign, :user, :email, 'AVAILABLE', :granted, :expires)
                        ON DUPLICATE KEY UPDATE id = id""")
                        .param("campaign", c.getId())
                        .param("user", user.getId())
                        .param("email", email)
                        .param("granted", now)
                        .param("expires", now.plusDays(c.getEntitlementValidDays()))
                        .update());
    }

    // ---- UC8.3: nối vào báo giá và đặt đơn ----

    /** Chỉ để tính tiền, KHÔNG giữ quyền. Cần email trường đã xác minh, quyền AVAILABLE còn hạn, chiến dịch còn hiệu lực. */
    @Override
    @Transactional(readOnly = true)
    public Optional<Terms> findUsable(Long customerId) {
        UserAccount u = users.findById(customerId).orElse(null);
        if (u == null || !u.isEmailVerified() || !emailPolicy.isSchoolEmail(u.getEmail())) {
            return Optional.empty();
        }
        List<Object[]> rows = entitlements.findUsableTerms(customerId, EntitlementStatus.AVAILABLE,
                LocalDateTime.now(clock));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] r = rows.get(0);
        return Optional.of(new Terms((Long) r[0], ((Number) r[1]).intValue(), ((Number) r[2]).longValue(),
                ((Number) r[3]).longValue()));
    }

    /** Giữ quyền cho đơn trong transaction của placeOrder. Thua cuộc đua thì ném lỗi để cả đơn và suất được hoàn lại. */
    @Override
    @Transactional
    public void hold(Long entitlementId, Long orderId) {
        int updated = entitlements.hold(entitlementId, orderId, LocalDateTime.now(clock),
                EntitlementStatus.AVAILABLE, EntitlementStatus.HELD);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.PROMOTION_NOT_ELIGIBLE,
                    "Ưu đãi đang được giữ cho đơn khác hoặc đã hết hạn. Hãy chọn lại cách thanh toán.");
        }
    }

    // ---- vòng đời sau khi giữ (BR07) ----

    @Override
    @Transactional
    public void consume(Long orderId) {
        entitlements.consume(orderId, LocalDateTime.now(clock), EntitlementStatus.HELD, EntitlementStatus.USED);
    }

    /**
     * Hủy hoặc hết hạn xác nhận thì trả quyền nếu còn hạn (BR07, UC6.2, UC6.3). Chính sách trả quyền gom ở một chỗ
     * này: nếu cần đổi thành "khách tự hủy thì mất quyền" thì sửa tại đây.
     */
    @Override
    @Transactional
    public void release(Long orderId) {
        LocalDateTime now = LocalDateTime.now(clock);
        entitlements.restore(orderId, now, EntitlementStatus.HELD, EntitlementStatus.AVAILABLE);
        entitlements.expireHeld(orderId, now, EntitlementStatus.HELD, EntitlementStatus.EXPIRED);
    }

    @Override
    @Transactional
    public void holdForReview(Long orderId) {
        entitlements.holdForReview(orderId, LocalDateTime.now(clock), EntitlementStatus.HELD,
                EntitlementStatus.UNDER_REVIEW);
    }

    /**
     * Kết luận sự việc không nhận món. Miễn: trả quyền nếu còn hạn, hết hạn thì Hết hạn. Xác nhận lỗi khách: mất quyền
     * (Hết hạn). Chính sách này gom ở đây để đổi một chỗ.
     */
    @Override
    @Transactional
    public void resolveReview(Long orderId, boolean restore) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (restore) {
            entitlements.transitionIfValid(orderId, now, EntitlementStatus.UNDER_REVIEW, EntitlementStatus.AVAILABLE);
        }
        entitlements.transition(orderId, now, EntitlementStatus.UNDER_REVIEW, EntitlementStatus.EXPIRED);
    }

    @Override
    @Transactional
    public void reinstate(Long orderId) {
        entitlements.transitionIfValid(orderId, LocalDateTime.now(clock), EntitlementStatus.EXPIRED,
                EntitlementStatus.AVAILABLE);
    }

    // ---- UC8.2 ----

    @Transactional(readOnly = true)
    public PromotionOverviewResponse overview(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        UserAccount u = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Optional<PromotionEntitlement> e = entitlements.findByUserId(userId);
        if (e.isPresent()) {
            PromotionEntitlement ent = e.get();
            Campaign c = campaigns.findById(ent.getCampaignId()).orElse(null);
            EntitlementStatus effective = ent.getStatus() == EntitlementStatus.AVAILABLE
                    && !now.isBefore(ent.getExpiresAt()) ? EntitlementStatus.EXPIRED : ent.getStatus();
            return new PromotionOverviewResponse(effective.name(), ent.getGrantedAt(), ent.getExpiresAt(),
                    ent.getUsedAt(), c == null ? null : PromotionOverviewResponse.Terms.from(c), noticeFor(effective));
        }
        Campaign current = campaigns.findFirstByActiveTrueAndStartsAtLessThanEqualAndEndsAtAfterOrderByIdDesc(now, now)
                .orElse(null);
        boolean schoolVerified = u.isEmailVerified() && emailPolicy.isSchoolEmail(u.getEmail());
        String notice = !schoolVerified
                ? "Hãy xác minh email trường bằng mã OTP để nhận ưu đãi đơn đầu."
                : current == null
                        ? "Hiện chưa có chiến dịch ưu đãi nào đang diễn ra."
                        : "Tài khoản này chưa có quyền ưu đãi.";
        return new PromotionOverviewResponse(null, null, null, null,
                current == null ? null : PromotionOverviewResponse.Terms.from(current), notice);
    }

    private static String noticeFor(EntitlementStatus s) {
        return switch (s) {
            case AVAILABLE -> "Bạn có thể dùng ưu đãi cho đơn đầu tiên đủ điều kiện.";
            case HELD -> "Ưu đãi đang được giữ cho một đơn chưa hoàn tất.";
            case USED -> "Bạn đã dùng ưu đãi đơn đầu.";
            case EXPIRED -> "Ưu đãi đã hết hạn.";
            case UNDER_REVIEW -> "Ưu đãi đang chờ xem xét sau một đơn không nhận món.";
        };
    }
}
