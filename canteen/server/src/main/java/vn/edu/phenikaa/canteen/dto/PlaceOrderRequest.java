package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Đơn được tạo từ giỏ hiện tại của người gọi. Mã khách lấy từ token, không nhận từ client (BR03). */
public record PlaceOrderRequest(
        @NotNull Long pickupSlotId,
        @Size(max = 255) String note,
        /** Bỏ trống hoặc true: dùng voucher nếu đủ điều kiện. False: để dành voucher cho đơn sau (UC5.3). */
        Boolean useVoucher) {

    public boolean wantsVoucher() {
        return useVoucher == null || useVoucher;
    }
}
