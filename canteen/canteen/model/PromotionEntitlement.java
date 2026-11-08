package vn.edu.phenikaa.canteen.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.edu.phenikaa.canteen.common.entity.BaseEntity;

/**
 * Quyền ưu đãi của một tài khoản (BR06): UNIQUE theo user_id và school_email nên đổi email hay tạo lại tài khoản
 * không có lượt mới. Mọi chuyển trạng thái trong lúc đặt đơn đều đi qua câu UPDATE có điều kiện ở repository,
 * không sửa entity rồi lưu, để hai đơn tranh một quyền thì chỉ một bên thắng.
 */
@Getter
@Setter
@Entity
@Table(name = "promotion_entitlement")
public class PromotionEntitlement extends BaseEntity {

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private Long campaignId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "school_email", nullable = false, updatable = false)
    private String schoolEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntitlementStatus status = EntitlementStatus.AVAILABLE;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private LocalDateTime grantedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;
}
