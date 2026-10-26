package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.NotNull;

public record DishVisibilityRequest(@NotNull Boolean active) {
}
