package vn.edu.phenikaa.canteen.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.Cart;

public interface CartRepository extends JpaRepository<Cart, Long> {

    /** Nạp giỏ cùng món, thực đơn và quầy trong một lần để không bị N+1. */
    @Query("select c from Cart c "
            + "left join fetch c.items ci "
            + "left join fetch ci.menuItem mi "
            + "left join fetch mi.dish "
            + "left join fetch mi.menu m "
            + "left join fetch m.vendor "
            + "where c.userId = :userId")
    Optional<Cart> findDetailByUserId(@Param("userId") Long userId);
}
