package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lý do bắt buộc: khách xin hủy (UC6.3) và quầy từ chối phục vụ (UC7.5) đều phải lưu vết (BR19, NFR03). */
public record ReasonRequest(@NotBlank @Size(max = 255) String reason) {
}
