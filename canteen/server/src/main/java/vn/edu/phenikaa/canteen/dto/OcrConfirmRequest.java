package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Giá trị người dùng đã đối chiếu và sửa (UC1.4). {@code email} là tùy chọn; nếu gửi thì đi theo luồng đổi email của hồ
 * sơ (email trường phải xác minh OTP, nhập tay hay OCR đều không thay bước đó).
 */
public record OcrConfirmRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{4,30}$", message = "Mã sinh viên chỉ gồm chữ và số (4 đến 30 ký tự)") String studentCode,
        @Email @Size(max = 255) String email) {
}
