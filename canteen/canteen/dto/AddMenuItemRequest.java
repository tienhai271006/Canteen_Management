package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** {@code counterSlots} là phần giữ cho khách mua tại quầy (BR33), mặc định 0. */
public record AddMenuItemRequest(
        @NotNull Long dishId,
        @NotNull @Min(0) @Max(10_000) Integer totalSlots,
        @Min(0) @Max(10_000) Integer counterSlots) {
}
