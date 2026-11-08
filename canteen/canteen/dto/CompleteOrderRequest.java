package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Bàn giao tại quầy (UC7.4, BR20): nhân viên nhập lại mã đơn khách đọc, số tiền đã thu và xác nhận đã giao món.
 * Thiếu hoặc sai một trong ba thì đơn chưa Completed.
 */
public record CompleteOrderRequest(
        @NotBlank String orderCode,
        @NotNull @Min(0) Long amountReceived,
        @NotNull Boolean handedOver) {
}
