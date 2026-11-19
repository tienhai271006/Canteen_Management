package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FeedbackResolveRequest(@NotBlank @Size(max = 1000) String response, @NotNull FeedbackAction action) {
}
