package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.NotNull;

public record ItemAvailabilityRequest(@NotNull Boolean available) {
}
