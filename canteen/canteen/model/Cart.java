package vn.edu.phenikaa.canteen.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.edu.phenikaa.canteen.common.entity.BaseEntity;

/**
 * Mỗi khách một giỏ. Giỏ chỉ chứa món của một quầy (BR01) và KHÔNG giữ suất (BR11):
 * suất chỉ bị giữ khi đặt đơn thành công.
 */
@Getter
@Setter
@Entity
@Table(name = "cart")
public class Cart extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** NULL khi giỏ trống. */
    @Column(name = "vendor_id")
    private Long vendorId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CartItem> items = new ArrayList<>();

    public void addItem(CartItem item) {
        item.setCart(this);
        items.add(item);
    }

    public Optional<CartItem> findItem(Long menuItemId) {
        return items.stream().filter(i -> i.getMenuItem().getId().equals(menuItemId)).findFirst();
    }

    /** Dọn hết món và nhả ràng buộc quầy. */
    public void empty() {
        items.clear();
        vendorId = null;
    }
}
