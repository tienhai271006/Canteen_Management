package vn.edu.phenikaa.canteen.repository;

import java.time.LocalDateTime;
import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.Feedback;
import vn.edu.phenikaa.canteen.model.FeedbackStatus;
import vn.edu.phenikaa.canteen.model.IncidentStatus;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    boolean existsByIncidentIdAndStatus(Long incidentId, FeedbackStatus status);

    Page<Feedback> findByStatusInOrderByIdDesc(Collection<FeedbackStatus> statuses, Pageable pageable);

    @Query(value = "select f from Feedback f where f.status in :statuses "
            + "and f.incidentId in (select i.id from Incident i where i.vendorId = :vendorId) order by f.id desc",
            countQuery = "select count(f) from Feedback f where f.status in :statuses "
                    + "and f.incidentId in (select i.id from Incident i where i.vendorId = :vendorId)")
    Page<Feedback> findByVendor(@Param("vendorId") Long vendorId, @Param("statuses") Collection<FeedbackStatus> statuses,
                                Pageable pageable);

    /** BR35: số phản hồi đang mở gắn với các sự việc ĐÃ XÁC NHẬN còn trong cửa sổ của khách (tranh chấp). */
    @Query("select count(f) from Feedback f where f.status = :open and f.incidentId in "
            + "(select i.id from Incident i where i.userId = :userId and i.status = :confirmed and i.occurredAt >= :since)")
    long countOpenOnConfirmed(@Param("userId") Long userId, @Param("open") FeedbackStatus open,
                              @Param("confirmed") IncidentStatus confirmed, @Param("since") LocalDateTime since);
}
