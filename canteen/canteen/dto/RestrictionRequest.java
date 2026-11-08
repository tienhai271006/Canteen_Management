package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** UC10.5. {@code days} bỏ trống thì dùng mặc định 7 ngày (BR31). */
public record RestrictionRequest(
        @NotNull Long userId,
        @NotBlank @Size(max = 255) String reason,
        @Min(1) @Max(30) Integer days) {
}
