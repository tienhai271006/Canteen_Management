package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import vn.edu.phenikaa.canteen.model.MealSession;
import vn.edu.phenikaa.canteen.model.Menu;
import vn.edu.phenikaa.canteen.model.Order;
import vn.edu.phenikaa.canteen.model.OrderItem;
import vn.edu.phenikaa.canteen.model.OrderStatus;
import vn.edu.phenikaa.canteen.model.PaymentStatus;
import vn.edu.phenikaa.canteen.model.PickupSlot;
import vn.edu.phenikaa.canteen.model.Vendor;

public record OrderResponse(
        Long id,
        String orderCode,
        OrderStatus status,
        PaymentStatus paymentStatus,
        Long vendorId,
        String vendorName,
        String pickupLocation,
        LocalDate menuDate,
        MealSession mealSession,
        Long pickupSlotId,
        LocalTime pickupStart,
        LocalTime pickupEnd,
        List<Item> items,
        long subtotalAmount,
        long discountAmount,
        long totalAmount,
        String note,
        LocalDateTime pendingExpiresAt,
        LocalDateTime createdAt) {

    public record Item(Long menuItemId, Long dishId, String dishName, long unitPrice, int quantity, long lineTotal) {

        static Item from(OrderItem i) {
            return new Item(i.getMenuItemId(), i.getDishId(), i.getDishName(), i.getUnitPrice(),
                    i.getQuantity(), i.getLineTotal());
        }
    }

    /** Cần vendor, pickupSlot và slot.menu đã được nạp (xem OrderRepository.findDetailById). */
    public static OrderResponse from(Order o) {
        Vendor v = o.getVendor();
        PickupSlot slot = o.getPickupSlot();
        Menu menu = slot.getMenu();
        return new OrderResponse(o.getId(), o.getOrderCode(), o.getStatus(), o.getPaymentStatus(),
                v.getId(), v.getName(), v.getPickupLocation(),
                menu.getMenuDate(), menu.getMealSession(), slot.getId(), slot.getStartTime(), slot.getEndTime(),
                o.getItems().stream().map(Item::from).toList(),
                o.getSubtotalAmount(), o.getDiscountAmount(), o.getTotalAmount(), o.getNote(),
                o.getPendingExpiresAt(), o.getCreatedAt());
    }
}
