package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDateTime;

import vn.edu.phenikaa.canteen.model.Notification;
import vn.edu.phenikaa.canteen.model.NotificationType;

/** Thông báo trong ứng dụng. Trạng thái gửi email không nằm ở đây và không đồng nghĩa người dùng đã đọc (BR30). */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String body,
        String refType,
        Long refId,
        LocalDateTime readAt,
        LocalDateTime createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getRefType(),
                n.getRefId(), n.getReadAt(), n.getCreatedAt());
    }
}
