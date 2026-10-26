package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDate;

import vn.edu.phenikaa.canteen.model.DishCategory;
import vn.edu.phenikaa.canteen.model.MealSession;

/** Điều kiện tìm kiếm món (R4.x). Trường null nghĩa là không lọc theo trường đó. */
public record CatalogCriteria(
        LocalDate date,
        MealSession session,
        Long areaId,
        Long vendorId,
        String keyword,
        DishCategory category,
        Long minPrice,
        Long maxPrice,
        boolean availableOnly,
        Sort sort) {

    public enum Sort {
        NAME, PRICE_ASC, PRICE_DESC, REMAINING_DESC
    }
}
