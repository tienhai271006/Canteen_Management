package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDateTime;

import vn.edu.phenikaa.canteen.model.CancelRequest;
import vn.edu.phenikaa.canteen.model.CancelRequestStatus;
import vn.edu.phenikaa.canteen.model.HandleChannel;

public record CancelRequestResponse(
        Long id,
        Long orderId,
        String orderCode,
        CancelRequestStatus status,
        String reason,
        LocalDateTime createdAt,
        LocalDateTime handledAt,
        String handleReason,
        HandleChannel handleChannel) {

    public static CancelRequestResponse from(CancelRequest r, String orderCode) {
        return new CancelRequestResponse(r.getId(), r.getOrderId(), orderCode, r.getStatus(), r.getReason(),
                r.getCreatedAt(), r.getHandledAt(), r.getHandleReason(), r.getHandleChannel());
    }
}
