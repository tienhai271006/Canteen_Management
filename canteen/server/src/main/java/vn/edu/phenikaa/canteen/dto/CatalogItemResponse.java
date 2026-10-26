package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import vn.edu.phenikaa.canteen.model.DishCategory;
import vn.edu.phenikaa.canteen.model.MealSession;

/**
 * Một món có thể đặt, hiển thị cho khách. Chỉ lộ số suất còn lại, không lộ phần dành cho quầy.
 * {@code orderable} đã tính cả hạn đóng nhận đơn lẫn hạn mức; việc kiểm tra thật vẫn nằm ở Pha 6 khi đặt đơn.
 */
public record CatalogItemResponse(
        Long menuItemId,
        Long menuId,
        LocalDate menuDate,
        MealSession mealSession,
        LocalTime orderCutoffTime,
        Long vendorId,
        String vendorName,
        Long areaId,
        String areaName,
        String pickupLocation,
        Long dishId,
        String dishName,
        String dishDescription,
        DishCategory category,
        long price,
        String imageUrl,
        int remaining,
        boolean orderable,
        List<SlotResponse> pickupSlots) {
}
