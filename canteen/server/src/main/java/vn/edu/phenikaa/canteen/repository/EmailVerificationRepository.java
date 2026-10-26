package vn.edu.phenikaa.canteen.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.EmailVerification;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findFirstByUserIdAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(Long userId);

    Optional<EmailVerification> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);

    @Modifying(flushAutomatically = true)
    @Query("""
            update EmailVerification v
               set v.invalidatedAt = :now, v.updatedAt = :now
             where v.userId = :userId and v.consumedAt is null and v.invalidatedAt is null
            """)
    int invalidateOpen(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
