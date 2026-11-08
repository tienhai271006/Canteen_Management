package vn.edu.phenikaa.canteen.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;

public record UpdateCutoffRequest(@NotNull LocalTime orderCutoffTime) {
}
