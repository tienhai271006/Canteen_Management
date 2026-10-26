package vn.edu.phenikaa.canteen.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.edu.phenikaa.canteen.common.entity.BaseEntity;

/**
 * Hạn mức của một món trong một thực đơn.
 * Số suất đặt online còn lại = total - counter - reserved.
 * <p>
 * LƯU Ý CHO PHA 6: {@code reserved} được tăng/giảm bằng câu UPDATE có điều kiện (không qua entity) nên phải kèm
 * {@code version = version + 1} để khóa lạc quan của QuotaAdminService vẫn đúng.
 */
@Getter
@Setter
@Entity
@Table(name = "quota")
public class Quota extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false, unique = true)
    private MenuItem menuItem;

    @Column(name = "total_slots", nullable = false)
    private int totalSlots;

    @Column(name = "reserved_slots", nullable = false)
    private int reservedSlots;

    /** Phần dành cho khách mua tại quầy, không bán online (BR33). */
    @Column(name = "counter_slots", nullable = false)
    private int counterSlots;

    public int availableOnline() {
        return totalSlots - counterSlots - reservedSlots;
    }
}
