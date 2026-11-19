package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDateTime;

import vn.edu.phenikaa.canteen.model.IncidentStatus;
import vn.edu.phenikaa.canteen.model.IncidentType;

/** {@code disputed}: đang có phản hồi chờ xử lý (BR35). */
public record IncidentResponse(
        Long id,
        Long orderId,
        String orderCode,
        Long vendorId,
        String vendorName,
        Long userId,
        String customerName,
        IncidentType type,
        IncidentStatus status,
        LocalDateTime occurredAt,
        LocalDateTime decidedAt,
        String decisionNote,
        boolean disputed) {
}
