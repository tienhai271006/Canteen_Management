package vn.edu.phenikaa.canteen.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.WasteOutcome;
import vn.edu.phenikaa.canteen.model.WasteRecord;

public interface WasteRecordRepository extends JpaRepository<WasteRecord, Long> {

    interface Total {
        Long getOrderId();

        Long getDishId();

        WasteOutcome getOutcome();

        Long getTotal();
    }

    @Query("select coalesce(sum(w.quantity), 0) from WasteRecord w where w.orderId = :orderId and w.dishId = :dishId")
    long totalProcessed(@Param("orderId") Long orderId, @Param("dishId") Long dishId);

    @Query("select w.orderId as orderId, w.dishId as dishId, w.outcome as outcome, sum(w.quantity) as total "
            + "from WasteRecord w where w.orderId in :orderIds group by w.orderId, w.dishId, w.outcome")
    List<Total> totalsByOrders(@Param("orderIds") Collection<Long> orderIds);
}
