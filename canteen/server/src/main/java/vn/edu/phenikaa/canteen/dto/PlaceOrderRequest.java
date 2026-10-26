package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Đơn được tạo từ giỏ hiện tại của người gọi. Mã khách lấy từ token, không nhận từ client (BR03). */
public record PlaceOrderRequest(
        @NotNull Long pickupSlotId,
        @Size(max = 255) String note) {
}
