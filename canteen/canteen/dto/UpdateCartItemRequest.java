package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Đặt lại số lượng (tuyệt đối) và ghi chú. Ghi chú null nghĩa là xóa ghi chú. Muốn bỏ món thì dùng DELETE. */
public record UpdateCartItemRequest(
        @NotNull @Min(1) @Max(100) Integer quantity,
        @Size(max = 255) String note) {
}
