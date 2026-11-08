package vn.edu.phenikaa.canteen.model;

import java.time.LocalDate;
import java.time.LocalTime;

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

/** Thực đơn của một quầy cho một ngày và một buổi (duy nhất theo bộ ba này). */
@Getter
@Setter
@Entity
@Table(name = "menu")
public class Menu extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(name = "menu_date", nullable = false)
    private LocalDate menuDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_session", nullable = false, length = 20)
    private MealSession mealSession;

    /** Giờ đóng nhận đơn của ca (BR02). */
    @Column(name = "order_cutoff_time", nullable = false)
    private LocalTime orderCutoffTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MenuStatus status = MenuStatus.DRAFT;

    public boolean isPublished() {
        return status == MenuStatus.PUBLISHED;
    }
}
