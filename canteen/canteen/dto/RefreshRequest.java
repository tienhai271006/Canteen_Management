package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.NotBlank;

/** Dùng cho cả làm mới phiên và đăng xuất. */
public record RefreshRequest(@NotBlank String refreshToken) {
}
