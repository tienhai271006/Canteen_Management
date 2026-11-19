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

/** Yêu cầu hỗ trợ đến muộn (UC6.4, UC7.7). Hạn nhận chỉ đổi khi quầy chấp thuận. */
@Getter
@Setter
@Entity
@Table(name = "late_arrival_request")
public class LateArrivalRequest extends BaseEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private Long requestedBy;

    @Column(nullable = false, updatable = false, length = 255)
    private String reason;

    @Column(name = "proposed_arrival_at", nullable = false, updatable = false)
    private LocalDateTime proposedArrivalAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LateArrivalStatus status = LateArrivalStatus.PENDING;

    @Column(name = "approved_deadline")
    private LocalDateTime approvedDeadline;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "handle_reason", length = 255)
    private String handleReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "handle_channel", length = 20)
    private HandleChannel handleChannel;
}
