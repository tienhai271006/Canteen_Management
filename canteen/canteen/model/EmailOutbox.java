package vn.edu.phenikaa.canteen.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.edu.phenikaa.canteen.common.entity.BaseEntity;

/**
 * Hàng đợi email (NFR05). Trạng thái, số lần thử và lỗi cuối được ghi lại để thử gửi lại; {@code event_key} UNIQUE
 * bảo đảm thử lại cùng một sự kiện không tạo email thứ hai (BR29). Chỉ cập nhật bằng các câu UPDATE có điều kiện.
 */
@Getter
@Setter
@Entity
@Table(name = "email_outbox")
public class EmailOutbox extends BaseEntity {

    @Column(name = "notification_id", updatable = false)
    private Long notificationId;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "to_email", nullable = false, updatable = false)
    private String toEmail;

    @Column(nullable = false, updatable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "event_key", nullable = false, updatable = false, length = 120)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailOutboxStatus status = EmailOutboxStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
