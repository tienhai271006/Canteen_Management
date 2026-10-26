package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(@NotBlank @Pattern(regexp = "^\\d{6}$", message = "Mã gồm 6 chữ số") String code) {
}
