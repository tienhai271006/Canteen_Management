package vn.edu.phenikaa.canteen.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.phenikaa.canteen.model.OrderStatusHistory;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    List<OrderStatusHistory> findByOrderIdOrderByIdAsc(Long orderId);
}
