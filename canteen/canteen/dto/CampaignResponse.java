package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDateTime;

import vn.edu.phenikaa.canteen.model.Campaign;

public record CampaignResponse(
        Long id,
        String name,
        int discountPercent,
        long maxDiscountAmount,
        long minOrderAmount,
        int entitlementValidDays,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        boolean active) {

    public static CampaignResponse from(Campaign c) {
        return new CampaignResponse(c.getId(), c.getName(), c.getDiscountPercent(), c.getMaxDiscountAmount(),
                c.getMinOrderAmount(), c.getEntitlementValidDays(), c.getStartsAt(), c.getEndsAt(), c.isActive());
    }
}
