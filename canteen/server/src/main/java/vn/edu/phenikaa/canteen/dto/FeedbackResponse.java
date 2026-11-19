package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDateTime;

import vn.edu.phenikaa.canteen.model.Feedback;
import vn.edu.phenikaa.canteen.model.FeedbackStatus;

public record FeedbackResponse(
        Long id,
        Long incidentId,
        Long userId,
        String content,
        FeedbackStatus status,
        String response,
        LocalDateTime createdAt,
        LocalDateTime handledAt) {

    public static FeedbackResponse from(Feedback f) {
        return new FeedbackResponse(f.getId(), f.getIncidentId(), f.getUserId(), f.getContent(), f.getStatus(),
                f.getResponse(), f.getCreatedAt(), f.getHandledAt());
    }
}
