package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** {@code capacity} null nghĩa là bỏ giới hạn cho khung này. */
public record SlotCapacityRequest(@Min(0) @Max(10_000) Integer capacity) {
}
