package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Khu vực không đổi được sau khi đăng ký (thực đơn và đơn hàng đã gắn với quầy theo khu). */
public record UpdateVendorRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @Size(max = 255) String pickupLocation) {
}
