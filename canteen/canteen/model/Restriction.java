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

/** Hạn chế tạo đơn đặt trước do quản trị viên quyết định (BR31). Không ảnh hưởng đăng nhập, xem lịch sử, phản hồi. */
@Getter
@Setter
@Entity
@Table(name = "restriction")
public class Restriction extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestrictionStatus status = RestrictionStatus.ACTIVE;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private LocalDateTime endsAt;

    @Column(nullable = false, updatable = false, length = 255)
    private String reason;

    @Column(name = "decided_by", nullable = false, updatable = false)
    private Long decidedBy;

    @Column(name = "revoked_by")
    private Long revokedBy;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
