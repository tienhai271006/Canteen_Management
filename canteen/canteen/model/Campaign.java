package vn.edu.phenikaa.canteen.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.edu.phenikaa.canteen.common.entity.BaseEntity;

/** Chiến dịch ưu đãi đơn đầu (UC8.1). Điều khoản được đọc lúc tạo đơn rồi lưu cố định vào đơn (BR04, BR05). */
@Getter
@Setter
@Entity
@Table(name = "campaign")
public class Campaign extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "discount_percent", nullable = false)
    private int discountPercent;

    @Column(name = "max_discount_amount", nullable = false)
    private long maxDiscountAmount;

    @Column(name = "min_order_amount", nullable = false)
    private long minOrderAmount;

    @Column(name = "entitlement_valid_days", nullable = false)
    private int entitlementValidDays;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(nullable = false)
    private boolean active = true;
}
