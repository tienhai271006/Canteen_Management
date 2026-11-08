package vn.edu.phenikaa.canteen.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.Incident;
import vn.edu.phenikaa.canteen.model.IncidentStatus;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    boolean existsByOrderId(Long orderId);

    Optional<Incident> findByOrderId(Long orderId);

    Page<Incident> findByVendorIdAndStatusInOrderByIdDesc(Long vendorId, Collection<IncidentStatus> statuses,
                                                          Pageable pageable);

    Page<Incident> findByStatusInOrderByIdDesc(Collection<IncidentStatus> statuses, Pageable pageable);

    Page<Incident> findByUserIdAndStatusInOrderByIdDesc(Long userId, Collection<IncidentStatus> statuses,
                                                        Pageable pageable);

    /** BR26: chỉ đếm sự việc theo THỜI ĐIỂM XẢY RA trong cửa sổ trượt, không theo lúc kết luận hay lúc gửi email. */
    @Query("select count(i) from Incident i where i.userId = :userId and i.status = :status "
            + "and i.occurredAt >= :since and i.occurredAt <= :now")
    long countInWindow(@Param("userId") Long userId, @Param("status") IncidentStatus status,
                       @Param("since") LocalDateTime since, @Param("now") LocalDateTime now);
}
