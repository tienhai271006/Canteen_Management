package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.edu.phenikaa.canteen.model.DishCategory;

/** Đổi giá và ẩn/hiện món có endpoint riêng để thao tác nhạy cảm này dễ kiểm soát và ghi nhận. */
public record UpdateDishRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotNull DishCategory category) {
}
