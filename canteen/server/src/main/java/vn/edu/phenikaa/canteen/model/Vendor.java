package vn.edu.phenikaa.canteen.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.edu.phenikaa.canteen.common.entity.BaseEntity;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;

@Getter
@Setter
@Entity
@Table(name = "vendor")
public class Vendor extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "pickup_location", length = 255)
    private String pickupLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VendorStatus status = VendorStatus.PENDING;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    // ---- Chuyển trạng thái: quy tắc nằm ở một nơi duy nhất ----

    /** PENDING -> ACTIVE (R2.2). */
    public void approve(Long adminId, LocalDateTime now) {
        requireStatus(VendorStatus.PENDING, "Chỉ quầy đang chờ duyệt mới được duyệt.");
        status = VendorStatus.ACTIVE;
        approvedBy = adminId;
        approvedAt = now;
        statusReason = null;
    }

    /** ACTIVE -> SUSPENDED (R2.4). Chỉ chặn đơn mới, không đụng tới đơn đang xử lý. */
    public void suspend(String reason) {
        requireStatus(VendorStatus.ACTIVE, "Chỉ quầy đang hoạt động mới được tạm ngừng.");
        status = VendorStatus.SUSPENDED;
        statusReason = reason;
    }

    /** SUSPENDED -> ACTIVE. */
    public void reactivate() {
        requireStatus(VendorStatus.SUSPENDED, "Chỉ quầy đang tạm ngừng mới được mở lại.");
        status = VendorStatus.ACTIVE;
        statusReason = null;
    }

    public boolean isActive() {
        return status == VendorStatus.ACTIVE;
    }

    private void requireStatus(VendorStatus expected, String message) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.VENDOR_INVALID_STATE, message);
        }
    }
}
