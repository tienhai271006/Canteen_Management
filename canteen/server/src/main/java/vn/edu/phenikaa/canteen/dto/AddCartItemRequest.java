package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Thêm món vào giỏ. Nếu món đã có thì cộng dồn số lượng. */
public record AddCartItemRequest(
        @NotNull Long menuItemId,
        @NotNull @Min(1) @Max(100) Integer quantity,
        @Size(max = 255) String note) {
}
