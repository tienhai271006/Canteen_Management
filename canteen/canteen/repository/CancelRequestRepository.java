package vn.edu.phenikaa.canteen.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.CancelRequest;
import vn.edu.phenikaa.canteen.model.CancelRequestStatus;

public interface CancelRequestRepository extends JpaRepository<CancelRequest, Long> {

    boolean existsByOrderIdAndStatus(Long orderId, CancelRequestStatus status);

    Optional<CancelRequest> findByIdAndOrderId(Long id, Long orderId);

    List<CancelRequest> findByOrderIdOrderByIdDesc(Long orderId);

    @Query(value = "select r from CancelRequest r "
            + "where r.orderId in (select o.id from CustomerOrder o where o.vendor.id = :vendorId) "
            + "and r.status in :statuses order by r.id desc",
            countQuery = "select count(r) from CancelRequest r "
                    + "where r.orderId in (select o.id from CustomerOrder o where o.vendor.id = :vendorId) "
                    + "and r.status in :statuses")
    Page<CancelRequest> findByVendor(@Param("vendorId") Long vendorId,
                                     @Param("statuses") Collection<CancelRequestStatus> statuses,
                                     Pageable pageable);
}
