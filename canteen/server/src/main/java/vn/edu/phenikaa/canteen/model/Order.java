package vn.edu.phenikaa.canteen.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.edu.phenikaa.canteen.common.entity.BaseEntity;
import vn.edu.phenikaa.canteen.model.PickupSlot;
import vn.edu.phenikaa.canteen.model.Vendor;

/**
 * Đơn đặt trước. Tên entity trong JPQL là {@code CustomerOrder} (tránh trùng từ khóa ORDER của HQL); bảng vẫn là {@code orders}.
 * Giá, khoản giảm và tổng tiền được chốt lúc tạo đơn (BR04), đổi giá món sau đó không ảnh hưởng.
 * Đổi trạng thái chỉ đi qua {@code OrderService.transition} để luôn kiểm tra bảng chuyển, ghi lịch sử và trả suất.
 */
@Getter
@Setter
@Entity(name = "CustomerOrder")
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(name = "order_code", nullable = false, updatable = false, length = 30)
    private String orderCode;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false, updatable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pickup_slot_id", nullable = false, updatable = false)
    private PickupSlot pickupSlot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "subtotal_amount", nullable = false, updatable = false)
    private long subtotalAmount;

    @Column(name = "discount_amount", nullable = false, updatable = false)
    private long discountAmount;

    @Column(name = "total_amount", nullable = false, updatable = false)
    private long totalAmount;

    /** Quyền ưu đãi đơn đầu đang giữ hoặc đã dùng cho đơn này (Pha 8). */
    @Column(name = "entitlement_id")
    private Long entitlementId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 64)
    private String idempotencyKey;

    @Column(length = 255)
    private String note;

    @Column(name = "pending_expires_at")
    private LocalDateTime pendingExpiresAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "pickup_deadline")
    private LocalDateTime pickupDeadline;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "closed_reason", length = 255)
    private String closedReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }
}
