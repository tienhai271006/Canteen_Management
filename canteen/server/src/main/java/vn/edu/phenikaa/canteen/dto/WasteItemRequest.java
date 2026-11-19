package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Số suất bán lại và số suất hao hụt của một dòng đơn (UC3.7). Bỏ trống một trong hai nghĩa là 0. */
public record WasteItemRequest(
        @NotNull Long orderItemId,
        @Min(0) Integer resoldQuantity,
        @Min(0) Integer wastedQuantity) {
}
