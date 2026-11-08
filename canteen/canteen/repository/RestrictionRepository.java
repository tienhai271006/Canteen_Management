package vn.edu.phenikaa.canteen.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.Restriction;
import vn.edu.phenikaa.canteen.model.RestrictionStatus;

public interface RestrictionRepository extends JpaRepository<Restriction, Long> {

    boolean existsByUserIdAndStatus(Long userId, RestrictionStatus status);

    Optional<Restriction> findFirstByUserIdAndStatusOrderByIdDesc(Long userId, RestrictionStatus status);

    Page<Restriction> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    Page<Restriction> findByStatusInOrderByIdDesc(Collection<RestrictionStatus> statuses, Pageable pageable);

    /** Dọn hạn chế đã hết hạn nhưng job chưa kịp chạy, để quyết định mới không bị UNIQUE chặn nhầm. */
    @Modifying(flushAutomatically = true)
    @Query("update Restriction r set r.status = :expired, r.updatedAt = :now, r.version = r.version + 1 "
            + "where r.userId = :userId and r.status = :active and r.endsAt <= :now")
    int expireStale(@Param("userId") Long userId, @Param("now") LocalDateTime now,
                    @Param("active") RestrictionStatus active, @Param("expired") RestrictionStatus expired);
}
