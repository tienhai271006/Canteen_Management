package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuspendVendorRequest(@NotBlank @Size(max = 255) String reason) {
}
