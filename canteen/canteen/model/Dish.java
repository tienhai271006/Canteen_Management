package vn.edu.phenikaa.canteen.model;

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
import vn.edu.phenikaa.canteen.model.Vendor;

/**
 * Món của quầy. Không bao giờ xóa cứng vì đơn cũ tham chiếu; "ẩn món" là {@code active = false}.
 * Giá chỉ là giá hiện hành: đơn chụp lại tên và giá lúc đặt (BR04) nên đổi giá không ảnh hưởng đơn cũ.
 */
@Getter
@Setter
@Entity
@Table(name = "dish")
public class Dish extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DishCategory category;

    @Column(nullable = false)
    private long price;

    @Column(name = "image_path", length = 255)
    private String imagePath;

    @Column(nullable = false)
    private boolean active = true;
}
