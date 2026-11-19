package vn.edu.phenikaa.canteen.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNullOrderByIdDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    /** Chỉ đánh dấu thông báo của chính người gọi; 0 dòng nghĩa là không có, của người khác, hoặc đã đọc rồi. */
    @Modifying(flushAutomatically = true)
    @Query("update Notification n set n.readAt = :now, n.updatedAt = :now, n.version = n.version + 1 "
            + "where n.id = :id and n.userId = :userId and n.readAt is null")
    int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query("update Notification n set n.readAt = :now, n.updatedAt = :now, n.version = n.version + 1 "
            + "where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
