package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDateTime;

import vn.edu.phenikaa.canteen.model.HandleChannel;
import vn.edu.phenikaa.canteen.model.LateArrivalRequest;
import vn.edu.phenikaa.canteen.model.LateArrivalStatus;

public record LateArrivalResponse(
        Long id,
        Long orderId,
        String orderCode,
        LateArrivalStatus status,
        String reason,
        LocalDateTime proposedArrivalAt,
        LocalDateTime approvedDeadline,
        LocalDateTime createdAt,
        LocalDateTime handledAt,
        String handleReason,
        HandleChannel handleChannel) {

    public static LateArrivalResponse from(LateArrivalRequest r, String orderCode) {
        return new LateArrivalResponse(r.getId(), r.getOrderId(), orderCode, r.getStatus(), r.getReason(),
                r.getProposedArrivalAt(), r.getApprovedDeadline(), r.getCreatedAt(), r.getHandledAt(),
                r.getHandleReason(), r.getHandleChannel());
    }
}
