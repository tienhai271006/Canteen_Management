package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDateTime;

import vn.edu.phenikaa.canteen.common.storage.FileStorageService;
import vn.edu.phenikaa.canteen.model.Dish;
import vn.edu.phenikaa.canteen.model.DishCategory;

public record DishResponse(
        Long id,
        Long vendorId,
        String name,
        String description,
        DishCategory category,
        long price,
        String imageUrl,
        boolean active,
        LocalDateTime updatedAt) {

    public static DishResponse from(Dish d) {
        return new DishResponse(d.getId(), d.getVendor().getId(), d.getName(), d.getDescription(), d.getCategory(),
                d.getPrice(), imageUrl(d), d.isActive(), d.getUpdatedAt());
    }

    static String imageUrl(Dish d) {
        return d.getImagePath() == null ? null : FileStorageService.PUBLIC_URL_PREFIX + d.getImagePath();
    }
}
