package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.edu.phenikaa.canteen.model.DishCategory;

public record CreateDishRequest(
        @NotNull Long vendorId,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotNull DishCategory category,
        @NotNull @Min(0) @Max(DishLimits.MAX_PRICE) Long price) {
}
