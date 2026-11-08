package vn.edu.phenikaa.canteen.dto;

import java.util.List;

/** Tình trạng xử lý món của một đơn không nhận. {@code remaining} = đặt - đã bán lại - đã hao hụt. */
public record WasteOrderResponse(Long orderId, String orderCode, List<Item> items) {

    public record Item(Long orderItemId, Long dishId, String dishName, int quantity, int resold, int wasted,
                       int remaining) {
    }
}
