package vn.edu.phenikaa.canteen.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.MenuItem;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    @Query("select mi from MenuItem mi join fetch mi.dish where mi.menu.id = :menuId order by mi.id")
    List<MenuItem> findByMenuIdWithDish(@Param("menuId") Long menuId);

    Optional<MenuItem> findByIdAndMenuId(Long id, Long menuId);

    /** Món kèm món gốc, thực đơn và quầy, dùng cho giỏ hàng và đặt đơn (Pha 6). */
    @Query("select mi from MenuItem mi join fetch mi.dish join fetch mi.menu m join fetch m.vendor where mi.id = :id")
    Optional<MenuItem> findDetailById(@Param("id") Long id);

    boolean existsByMenuIdAndDishId(Long menuId, Long dishId);

    long countByMenuId(Long menuId);

    long countByMenuIdAndAvailableTrue(Long menuId);
}
