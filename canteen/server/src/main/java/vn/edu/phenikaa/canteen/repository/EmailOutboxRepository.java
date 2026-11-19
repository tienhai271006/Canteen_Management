package vn.edu.phenikaa.canteen.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.phenikaa.canteen.model.EmailOutbox;
import vn.edu.phenikaa.canteen.model.EmailOutboxStatus;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    @Query("select e.id from EmailOutbox e where e.status = :pending and e.nextAttemptAt <= :now "
            + "order by e.nextAttemptAt, e.id")
    List<Long> findDueIds(@Param("pending") EmailOutboxStatus pending, @Param("now") LocalDateTime now,
                          Pageable pageable);

    /**
     * Nhận gửi một email: chỉ thành công nếu còn PENDING và đã đến hạn, đồng thời tăng số lần thử và đẩy hạn tiếp theo
     * ra {@code leaseUntil}. Nhờ vậy hai nơi (gửi ngay sau commit và lịch chạy nền) không gửi cùng một email.
     */
    @Modifying(flushAutomatically = true)
    @Query("update EmailOutbox e set e.attempts = e.attempts + 1, e.nextAttemptAt = :leaseUntil, e.updatedAt = :now, "
            + "e.version = e.version + 1 "
            + "where e.id = :id and e.status = :pending and e.nextAttemptAt <= :now")
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil,
              @Param("pending") EmailOutboxStatus pending);

    @Modifying(flushAutomatically = true)
    @Query("update EmailOutbox e set e.status = :sent, e.sentAt = :now, e.lastError = null, e.updatedAt = :now, "
            + "e.version = e.version + 1 where e.id = :id and e.status = :pending")
    int markSent(@Param("id") Long id, @Param("now") LocalDateTime now, @Param("pending") EmailOutboxStatus pending,
                 @Param("sent") EmailOutboxStatus sent);

    /** Gửi lỗi nhưng còn lượt: hẹn lần sau. */
    @Modifying(flushAutomatically = true)
    @Query("update EmailOutbox e set e.nextAttemptAt = :next, e.lastError = :error, e.updatedAt = :now, "
            + "e.version = e.version + 1 where e.id = :id and e.status = :pending")
    int scheduleRetry(@Param("id") Long id, @Param("next") LocalDateTime next, @Param("error") String error,
                      @Param("now") LocalDateTime now, @Param("pending") EmailOutboxStatus pending);

    /** Hết lượt thử: FAILED. Thông báo trong ứng dụng vẫn còn nguyên (NFR05). */
    @Modifying(flushAutomatically = true)
    @Query("update EmailOutbox e set e.status = :failed, e.lastError = :error, e.updatedAt = :now, "
            + "e.version = e.version + 1 where e.id = :id and e.status = :pending")
    int markFailed(@Param("id") Long id, @Param("error") String error, @Param("now") LocalDateTime now,
                   @Param("pending") EmailOutboxStatus pending, @Param("failed") EmailOutboxStatus failed);

    /** Xóa nội dung email chứa mã OTP sau khi đã gửi, để mã không nằm lại trong DB. */
    @Modifying(flushAutomatically = true)
    @Query("update EmailOutbox e set e.body = :body, e.updatedAt = :now, e.version = e.version + 1 where e.id = :id")
    int redactBody(@Param("id") Long id, @Param("body") String body, @Param("now") LocalDateTime now);
}
