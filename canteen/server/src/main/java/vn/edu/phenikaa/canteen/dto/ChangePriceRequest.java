package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ChangePriceRequest(@NotNull @Min(0) @Max(DishLimits.MAX_PRICE) Long price) {
}
