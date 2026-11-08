package vn.edu.phenikaa.canteen.dto;

import vn.edu.phenikaa.canteen.model.Dish;
import vn.edu.phenikaa.canteen.model.DishCategory;
import vn.edu.phenikaa.canteen.model.MenuItem;
import vn.edu.phenikaa.canteen.model.Quota;

/** Dạng đầy đủ cho quản lý: có cả suất đã giữ và phần dành cho quầy. */
public record MenuItemResponse(
        Long id,
        Long dishId,
        String dishName,
        DishCategory category,
        long price,
        String imageUrl,
        boolean dishActive,
        boolean available,
        int totalSlots,
        int reservedSlots,
        int counterSlots,
        int availableOnline) {

    public static MenuItemResponse from(MenuItem mi, Quota q) {
        Dish d = mi.getDish();
        return new MenuItemResponse(mi.getId(), d.getId(), d.getName(), d.getCategory(), d.getPrice(),
                DishResponse.imageUrl(d), d.isActive(), mi.isAvailable(),
                q.getTotalSlots(), q.getReservedSlots(), q.getCounterSlots(), q.availableOnline());
    }
}
