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
 * Thông báo trong ứng dụng. Việc tạo đi qua {@code NotificationService} bằng INSERT ... ON DUPLICATE KEY theo
 * {@code event_key}, nên entity này chủ yếu dùng để đọc và đánh dấu đã đọc.
 */
@Getter
@Setter
@Entity
@Table(name = "notification")
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, updatable = false, length = 160)
    private String title;

    @Column(nullable = false, updatable = false, length = 1000)
    private String body;

    @Column(name = "event_key", nullable = false, updatable = false, length = 120)
    private String eventKey;

    @Column(name = "ref_type", updatable = false, length = 30)
    private String refType;

    @Column(name = "ref_id", updatable = false)
    private Long refId;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
