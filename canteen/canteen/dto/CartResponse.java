package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import vn.edu.phenikaa.canteen.model.MealSession;

/**
 * Giỏ hàng kèm giá hiện hành. Khoản giảm ở đây chỉ là ước tính để khách xem (UC8.3),
 * giá chốt thật chỉ có khi đặt đơn (BR04). {@code problem} là tên ErrorCode nếu dòng đó hiện không đặt được.
 */
public record CartResponse(
        Long vendorId,
        String vendorName,
        Long menuId,
        LocalDate menuDate,
        MealSession mealSession,
        LocalTime orderCutoffTime,
        List<Line> items,
        long subtotal,
        long estimatedDiscount,
        long estimatedTotal) {

    public record Line(
            Long menuItemId,
            Long dishId,
            String dishName,
            long unitPrice,
            int quantity,
            long lineTotal,
            String note,
            int availableOnline,
            String problem) {
    }

    public static CartResponse empty() {
        return new CartResponse(null, null, null, null, null, null, List.of(), 0, 0, 0);
    }
}
