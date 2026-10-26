package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Chỉ các trường được phép sửa. Không có vai trò, trạng thái hay quyền ưu đãi (UC1.8). */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 120) String fullName,
        @Size(max = 30) String studentCode,
        @Pattern(regexp = "^[0-9+ ]{8,20}$", message = "Số điện thoại không hợp lệ") String phone,
        @Email @Size(max = 255) String email) {
}
