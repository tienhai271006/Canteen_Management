package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import vn.edu.phenikaa.canteen.model.MealSession;

public record CreateMenuRequest(
        @NotNull Long vendorId,
        @NotNull LocalDate menuDate,
        @NotNull MealSession mealSession,
        @NotNull LocalTime orderCutoffTime) {
}
