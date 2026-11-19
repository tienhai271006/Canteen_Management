package vn.edu.phenikaa.canteen.model;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.edu.phenikaa.canteen.common.entity.BaseEntity;

@Getter
@Setter
@Entity
@Table(name = "pickup_slot")
public class PickupSlot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** Tổng số suất khung nhận được; null là chưa giới hạn. Chỉ đổi qua {@code PickupSlotRepository} (câu UPDATE có điều kiện). */
    @Column(name = "capacity", insertable = false, updatable = false)
    private Integer capacity;

    /** Số suất đang giữ cho đơn chưa hủy hay hết hạn. Không bao giờ ghi qua entity, tránh ghi đè bộ đếm đang chạy. */
    @Column(name = "reserved_qty", insertable = false, updatable = false)
    private int reservedQty;

    /** Còn nhận được bao nhiêu suất; null nếu chưa giới hạn. */
    public Integer remaining() {
        return capacity == null ? null : Math.max(0, capacity - reservedQty);
    }

    public boolean isFull() {
        return capacity != null && reservedQty >= capacity;
    }

    public boolean overlaps(LocalTime start, LocalTime end) {
        return startTime.isBefore(end) && start.isBefore(endTime);
    }
}
