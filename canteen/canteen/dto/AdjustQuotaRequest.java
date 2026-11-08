package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Giá trị MỚI (không phải mức tăng giảm). Lý do bắt buộc để lưu vết (BR33, NFR03). */
public record AdjustQuotaRequest(
        @NotNull @Min(0) @Max(10_000) Integer totalSlots,
        @NotNull @Min(0) @Max(10_000) Integer counterSlots,
        @NotBlank @Size(max = 255) String reason) {
}
