package vn.edu.phenikaa.canteen.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.EntitlementStatus;
import vn.edu.phenikaa.canteen.model.PromotionEntitlement;

public interface PromotionEntitlementRepository extends JpaRepository<PromotionEntitlement, Long> {

    Optional<PromotionEntitlement> findByUserId(Long userId);

    /**
     * Điều khoản của quyền khả dụng: [entitlementId, percent, maxDiscount, minOrder]. Trả cột chứ không trả entity
     * để không đưa bản sao cũ vào persistence context ngay trước câu UPDATE giữ quyền.
     */
    @Query("select e.id, c.discountPercent, c.maxDiscountAmount, c.minOrderAmount "
            + "from PromotionEntitlement e, Campaign c "
            + "where c.id = e.campaignId and e.userId = :userId and e.status = :available "
            + "and e.expiresAt > :now and c.active = true and c.startsAt <= :now and c.endsAt > :now")
    List<Object[]> findUsableTerms(@Param("userId") Long userId,
                                   @Param("available") EntitlementStatus available,
                                   @Param("now") LocalDateTime now);

    /** AVAILABLE sang HELD cho một đơn; 0 dòng nghĩa là quyền vừa bị đơn khác giữ hoặc đã hết hạn. */
    @Modifying(flushAutomatically = true)
    @Query("update PromotionEntitlement e set e.status = :held, e.orderId = :orderId, e.updatedAt = :now, "
            + "e.version = e.version + 1 "
            + "where e.id = :id and e.status = :available and e.expiresAt > :now")
    int hold(@Param("id") Long id, @Param("orderId") Long orderId, @Param("now") LocalDateTime now,
             @Param("available") EntitlementStatus available, @Param("held") EntitlementStatus held);

    /** HELD sang USED khi đơn Completed và Paid (BR07). Chạy lặp không đổi gì thêm. */
    @Modifying(flushAutomatically = true)
    @Query("update PromotionEntitlement e set e.status = :used, e.usedAt = :now, e.updatedAt = :now, "
            + "e.version = e.version + 1 "
            + "where e.orderId = :orderId and e.status = :held")
    int consume(@Param("orderId") Long orderId, @Param("now") LocalDateTime now,
                @Param("held") EntitlementStatus held, @Param("used") EntitlementStatus used);

    /** Trả quyền về AVAILABLE khi hủy hợp lệ nếu còn hạn; gỡ liên kết với đơn đã đóng. */
    @Modifying(flushAutomatically = true)
    @Query("update PromotionEntitlement e set e.status = :available, e.orderId = null, e.updatedAt = :now, "
            + "e.version = e.version + 1 "
            + "where e.orderId = :orderId and e.status = :held and e.expiresAt > :now")
    int restore(@Param("orderId") Long orderId, @Param("now") LocalDateTime now,
                @Param("held") EntitlementStatus held, @Param("available") EntitlementStatus available);

    /** Quyền đang giữ mà đã quá hạn thì chuyển EXPIRED. Gọi sau {@link #restore} để chỉ còn các dòng quá hạn. */
    @Modifying(flushAutomatically = true)
    @Query("update PromotionEntitlement e set e.status = :expired, e.updatedAt = :now, e.version = e.version + 1 "
            + "where e.orderId = :orderId and e.status = :held")
    int expireHeld(@Param("orderId") Long orderId, @Param("now") LocalDateTime now,
                   @Param("held") EntitlementStatus held, @Param("expired") EntitlementStatus expired);

    /** Đơn không nhận món: quyền sang chờ xem xét (BR07). */
    @Modifying(flushAutomatically = true)
    @Query("update PromotionEntitlement e set e.status = :review, e.updatedAt = :now, e.version = e.version + 1 "
            + "where e.orderId = :orderId and e.status = :held")
    int holdForReview(@Param("orderId") Long orderId, @Param("now") LocalDateTime now,
                      @Param("held") EntitlementStatus held, @Param("review") EntitlementStatus review);

    /** Đổi trạng thái quyền của một đơn, chỉ khi quyền còn đang ở {@code from} (idempotent). */
    @Modifying(flushAutomatically = true)
    @Query("update PromotionEntitlement e set e.status = :to, e.updatedAt = :now, e.version = e.version + 1 "
            + "where e.orderId = :orderId and e.status = :from")
    int transition(@Param("orderId") Long orderId, @Param("now") LocalDateTime now,
                   @Param("from") EntitlementStatus from, @Param("to") EntitlementStatus to);

    /** Như {@link #transition} nhưng chỉ khi quyền vẫn còn hạn. */
    @Modifying(flushAutomatically = true)
    @Query("update PromotionEntitlement e set e.status = :to, e.updatedAt = :now, e.version = e.version + 1 "
            + "where e.orderId = :orderId and e.status = :from and e.expiresAt > :now")
    int transitionIfValid(@Param("orderId") Long orderId, @Param("now") LocalDateTime now,
                          @Param("from") EntitlementStatus from, @Param("to") EntitlementStatus to);

    /** Quyền khả dụng đã quá hạn thì chuyển EXPIRED. Quyền đang HELD thì chờ đơn đóng rồi {@code expireHeld} xử lý. */
    @Modifying(flushAutomatically = true)
    @Query("update PromotionEntitlement e set e.status = :expired, e.updatedAt = :now, e.version = e.version + 1 "
            + "where e.status = :available and e.expiresAt <= :now")
    int expireDue(@Param("now") LocalDateTime now, @Param("available") EntitlementStatus available,
                  @Param("expired") EntitlementStatus expired);
}
