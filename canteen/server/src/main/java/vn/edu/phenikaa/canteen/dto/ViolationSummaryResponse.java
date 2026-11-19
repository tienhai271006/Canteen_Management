package vn.edu.phenikaa.canteen.dto;

import java.util.List;

/** Hồ sơ để quản trị viên xét hạn chế (UC10.5 bước 1). {@code level}: NONE, REMINDER, WARNING, ESCALATED. */
public record ViolationSummaryResponse(
        Long userId,
        String customerName,
        int validCount,
        int windowDays,
        String level,
        boolean disputedOpen,
        RestrictionResponse activeRestriction,
        List<IncidentResponse> recentIncidents) {
}
