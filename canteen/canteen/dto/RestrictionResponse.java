package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDateTime;

import vn.edu.phenikaa.canteen.model.Restriction;
import vn.edu.phenikaa.canteen.model.RestrictionStatus;

public record RestrictionResponse(
        Long id,
        Long userId,
        RestrictionStatus status,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String reason,
        LocalDateTime revokedAt) {

    public static RestrictionResponse from(Restriction r) {
        return new RestrictionResponse(r.getId(), r.getUserId(), r.getStatus(), r.getStartsAt(), r.getEndsAt(),
                r.getReason(), r.getRevokedAt());
    }
}
