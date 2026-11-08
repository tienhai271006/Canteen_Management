package vn.edu.phenikaa.canteen.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.dto.PageParams;
import vn.edu.phenikaa.canteen.common.dto.PageResponse;
import vn.edu.phenikaa.canteen.dto.ChangePriceRequest;
import vn.edu.phenikaa.canteen.dto.CreateDishRequest;
import vn.edu.phenikaa.canteen.dto.DishResponse;
import vn.edu.phenikaa.canteen.dto.DishVisibilityRequest;
import vn.edu.phenikaa.canteen.dto.UpdateDishRequest;
import vn.edu.phenikaa.canteen.service.DishService;

@Tag(name = "Manager - Dish")
@RestController
@RequestMapping("/api/manager/dishes")
@RequiredArgsConstructor
public class ManagerDishController {

    private final DishService dishService;

    @GetMapping
    public PageResponse<DishResponse> list(@RequestParam Long vendorId,
                                           @RequestParam(defaultValue = "true") boolean includeHidden,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return dishService.list(vendorId, includeHidden, PageParams.of(page, size));
    }

    @PostMapping
    public ResponseEntity<DishResponse> create(@Valid @RequestBody CreateDishRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dishService.create(req));
    }

    @GetMapping("/{dishId}")
    public DishResponse get(@PathVariable Long dishId) {
        return dishService.get(dishId);
    }

    @PutMapping("/{dishId}")
    public DishResponse update(@PathVariable Long dishId, @Valid @RequestBody UpdateDishRequest req) {
        return dishService.update(dishId, req);
    }

    /** Giá mới chỉ áp dụng cho đơn tạo sau thời điểm này (BR04). */
    @PatchMapping("/{dishId}/price")
    public DishResponse changePrice(@PathVariable Long dishId, @Valid @RequestBody ChangePriceRequest req) {
        return dishService.changePrice(dishId, req.price());
    }

    @PatchMapping("/{dishId}/visibility")
    public DishResponse setVisibility(@PathVariable Long dishId, @Valid @RequestBody DishVisibilityRequest req) {
        return dishService.setActive(dishId, req.active());
    }

    @PostMapping(value = "/{dishId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DishResponse uploadImage(@PathVariable Long dishId, @RequestPart("file") MultipartFile file) {
        return dishService.setImage(dishId, file);
    }

    @DeleteMapping("/{dishId}/image")
    public DishResponse removeImage(@PathVariable Long dishId) {
        return dishService.removeImage(dishId);
    }
}
