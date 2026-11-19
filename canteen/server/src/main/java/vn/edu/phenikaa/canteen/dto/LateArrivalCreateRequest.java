package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Khách báo đến muộn: giờ đến đề xuất (giờ địa phương) và lý do (UC6.4). */
public record LateArrivalCreateRequest(
        @NotNull LocalDateTime proposedArrivalAt,
        @NotBlank @Size(max = 255) String reason) {
}
