package vn.edu.phenikaa.canteen.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    @Query("select o from CustomerOrder o "
            + "join fetch o.vendor "
            + "join fetch o.pickupSlot ps "
            + "join fetch ps.menu "
            + "left join fetch o.items "
            + "where o.id = :id")
    Optional<Order> findDetailById(@Param("id") Long id);
}
