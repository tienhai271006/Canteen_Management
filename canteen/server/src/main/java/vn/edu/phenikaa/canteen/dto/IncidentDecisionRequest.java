package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** UC10.3: kết luận Đã xác nhận hoặc Miễn, luôn kèm lý do để lưu vết (NFR03). */
public record IncidentDecisionRequest(@NotNull IncidentDecision decision, @NotBlank @Size(max = 255) String note) {
}
