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

/** Phản hồi của khách về một sự việc (UC10.6). Đang mở thì sự việc bị coi là tranh chấp (BR35). */
@Getter
@Setter
@Entity
@Table(name = "feedback")
public class Feedback extends BaseEntity {

    @Column(name = "incident_id", nullable = false, updatable = false)
    private Long incidentId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false, length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackStatus status = FeedbackStatus.OPEN;

    @Column(length = 1000)
    private String response;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;
}
