package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDateTime;

import vn.edu.phenikaa.canteen.model.QuotaAdjustment;

public record QuotaAdjustmentResponse(
        Long id,
        int deltaTotal,
        int deltaCounter,
        String reason,
        Long actorId,
        LocalDateTime createdAt) {

    public static QuotaAdjustmentResponse from(QuotaAdjustment a) {
        return new QuotaAdjustmentResponse(a.getId(), a.getDeltaTotal(), a.getDeltaCounter(), a.getReason(),
                a.getActorId(), a.getCreatedAt());
    }
}
