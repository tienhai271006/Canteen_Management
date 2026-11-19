package vn.edu.phenikaa.canteen.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.edu.phenikaa.canteen.common.entity.BaseEntity;

/** Kết quả xử lý một phần món đã làm mà không được nhận (UC3.7, BR15). Chỉ ghi thêm, không sửa. */
@Getter
@Setter
@Entity
@Table(name = "waste_record")
public class WasteRecord extends BaseEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "dish_id", nullable = false, updatable = false)
    private Long dishId;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private WasteOutcome outcome;

    @Column(updatable = false, length = 255)
    private String note;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private Long recordedBy;
}
