package vn.edu.phenikaa.canteen.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.LateArrivalRequest;
import vn.edu.phenikaa.canteen.model.LateArrivalStatus;

public interface LateArrivalRequestRepository extends JpaRepository<LateArrivalRequest, Long> {

    boolean existsByOrderIdAndStatus(Long orderId, LateArrivalStatus status);

    Optional<LateArrivalRequest> findByIdAndOrderId(Long id, Long orderId);

    List<LateArrivalRequest> findByOrderIdOrderByIdDesc(Long orderId);

    @Query(value = "select r from LateArrivalRequest r "
            + "where r.orderId in (select o.id from CustomerOrder o where o.vendor.id = :vendorId) "
            + "and r.status in :statuses order by r.id desc",
            countQuery = "select count(r) from LateArrivalRequest r "
                    + "where r.orderId in (select o.id from CustomerOrder o where o.vendor.id = :vendorId) "
                    + "and r.status in :statuses")
    Page<LateArrivalRequest> findByVendor(@Param("vendorId") Long vendorId,
                                          @Param("statuses") Collection<LateArrivalStatus> statuses,
                                          Pageable pageable);
}
