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
 * Yêu cầu hủy đơn Confirmed (UC6.3, UC7.6). Gửi yêu cầu KHÔNG đổi trạng thái đơn; chỉ khi quầy duyệt
 * thì đơn mới sang CANCELLED (BR16, BR17). Cột sinh {@code pending_order_id} trong DB bảo đảm mỗi đơn
 * chỉ có một yêu cầu đang chờ, nên không cần ánh xạ ở đây.
 */
@Getter
@Setter
@Entity
@Table(name = "cancel_request")
public class CancelRequest extends BaseEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private Long requestedBy;

    @Column(nullable = false, updatable = false, length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CancelRequestStatus status = CancelRequestStatus.PENDING;

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
