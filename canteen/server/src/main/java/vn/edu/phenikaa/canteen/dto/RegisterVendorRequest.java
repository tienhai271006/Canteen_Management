package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterVendorRequest(
        @NotNull Long areaId,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @Size(max = 255) String pickupLocation) {
}
