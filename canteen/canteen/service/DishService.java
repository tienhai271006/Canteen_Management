package vn.edu.phenikaa.canteen.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.dto.PageResponse;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.security.VendorAccessChecker;
import vn.edu.phenikaa.canteen.common.storage.FileStorageService;
import vn.edu.phenikaa.canteen.dto.CreateDishRequest;
import vn.edu.phenikaa.canteen.dto.DishResponse;
import vn.edu.phenikaa.canteen.dto.UpdateDishRequest;
import vn.edu.phenikaa.canteen.model.Dish;
import vn.edu.phenikaa.canteen.repository.DishRepository;
import vn.edu.phenikaa.canteen.repository.VendorRepository;

/**
 * UC3: thêm, sửa, ẩn món, đổi giá. Món không bị xóa cứng vì đơn cũ tham chiếu.
 * Đổi giá chỉ áp dụng cho đơn mới: đơn đã tạo giữ giá chụp lúc đặt (BR04).
 */
@Service
@RequiredArgsConstructor
public class DishService {

    private final DishRepository dishes;
    private final VendorRepository vendors;
    private final VendorAccessChecker access;
    private final FileStorageService storage;

    @Transactional
    public DishResponse create(CreateDishRequest req) {
        access.requireManager(req.vendorId());
        String name = req.name().trim();
        if (dishes.existsByVendorIdAndNameIgnoreCase(req.vendorId(), name)) {
            throw new BusinessException(ErrorCode.DISH_NAME_DUPLICATE);
        }
        Dish dish = new Dish();
        dish.setVendor(vendors.getReferenceById(req.vendorId()));
        dish.setName(name);
        dish.setDescription(blankToNull(req.description()));
        dish.setCategory(req.category());
        dish.setPrice(req.price());
        dish.setActive(true);
        return DishResponse.from(dishes.save(dish));
    }

    @Transactional(readOnly = true)
    public PageResponse<DishResponse> list(Long vendorId, boolean includeHidden, Pageable pageable) {
        access.requireManager(vendorId);
        return PageResponse.of(dishes.findForVendor(vendorId, includeHidden, pageable), DishResponse::from);
    }

    @Transactional(readOnly = true)
    public DishResponse get(Long dishId) {
        return DishResponse.from(managed(dishId));
    }

    @Transactional
    public DishResponse update(Long dishId, UpdateDishRequest req) {
        Dish dish = managed(dishId);
        String name = req.name().trim();
        if (dishes.existsByVendorIdAndNameIgnoreCaseAndIdNot(dish.getVendor().getId(), name, dishId)) {
            throw new BusinessException(ErrorCode.DISH_NAME_DUPLICATE);
        }
        dish.setName(name);
        dish.setDescription(blankToNull(req.description()));
        dish.setCategory(req.category());
        return DishResponse.from(dish);
    }

    @Transactional
    public DishResponse changePrice(Long dishId, long newPrice) {
        Dish dish = managed(dishId);
        dish.setPrice(newPrice);
        return DishResponse.from(dish);
    }

    /** Ẩn món: khách không còn thấy và không đặt được; món vẫn nằm trong thực đơn cũ và đơn đã đặt. */
    @Transactional
    public DishResponse setActive(Long dishId, boolean active) {
        Dish dish = managed(dishId);
        dish.setActive(active);
        return DishResponse.from(dish);
    }

    @Transactional
    public DishResponse setImage(Long dishId, MultipartFile file) {
        Dish dish = managed(dishId);
        String stored = storage.storeImage(file);
        String old = dish.getImagePath();
        dish.setImagePath(stored);
        deleteAfterCommit(old);
        return DishResponse.from(dish);
    }

    @Transactional
    public DishResponse removeImage(Long dishId) {
        Dish dish = managed(dishId);
        String old = dish.getImagePath();
        dish.setImagePath(null);
        deleteAfterCommit(old);
        return DishResponse.from(dish);
    }

    private Dish managed(Long dishId) {
        Dish dish = dishes.findById(dishId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy món."));
        access.requireManager(dish.getVendor().getId());
        return dish;
    }

    /** Chỉ xóa ảnh cũ sau khi DB đã commit, để rollback không làm món trỏ vào ảnh đã mất. */
    private void deleteAfterCommit(String storedName) {
        if (storedName == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storage.deleteQuietly(storedName);
                }
            });
        } else {
            storage.deleteQuietly(storedName);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
