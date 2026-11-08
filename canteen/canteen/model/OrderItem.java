package vn.edu.phenikaa.canteen.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.edu.phenikaa.canteen.common.entity.BaseEntity;

/** Chụp lại tên món và đơn giá lúc đặt (BR04). */
@Getter
@Setter
@Entity
@Table(name = "order_item")
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(name = "menu_item_id", nullable = false, updatable = false)
    private Long menuItemId;

    @Column(name = "dish_id", nullable = false, updatable = false)
    private Long dishId;

    @Column(name = "dish_name", nullable = false, updatable = false, length = 120)
    private String dishName;

    @Column(name = "unit_price", nullable = false, updatable = false)
    private long unitPrice;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, updatable = false)
    private long lineTotal;
}
