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

/** Sự việc cần xem xét sau một đơn NOT_COLLECTED (UC7.8, UC10.3). Mỗi đơn đúng một sự việc (UNIQUE order_id, BR25). */
@Getter
@Setter
@Entity
@Table(name = "incident")
public class Incident extends BaseEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "vendor_id", nullable = false, updatable = false)
    private Long vendorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private IncidentType type = IncidentType.NO_SHOW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status = IncidentStatus.PENDING_REVIEW;

    /** Mốc tính cửa sổ 30 ngày (BR26): lúc hạn nhận của đơn trôi qua, không phải lúc quầy bấm ghi nhận. */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_note", length = 255)
    private String decisionNote;
}
