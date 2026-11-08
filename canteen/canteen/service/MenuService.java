package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.security.VendorAccessChecker;
import vn.edu.phenikaa.canteen.dto.AddMenuItemRequest;
import vn.edu.phenikaa.canteen.dto.CreateMenuRequest;
import vn.edu.phenikaa.canteen.dto.MenuDetailResponse;
import vn.edu.phenikaa.canteen.dto.MenuItemResponse;
import vn.edu.phenikaa.canteen.dto.MenuSummaryResponse;
import vn.edu.phenikaa.canteen.dto.SlotResponse;
import vn.edu.phenikaa.canteen.model.Dish;
import vn.edu.phenikaa.canteen.model.Menu;
import vn.edu.phenikaa.canteen.model.MenuItem;
import vn.edu.phenikaa.canteen.model.MenuStatus;
import vn.edu.phenikaa.canteen.model.Quota;
import vn.edu.phenikaa.canteen.repository.DishRepository;
import vn.edu.phenikaa.canteen.repository.MenuItemRepository;
import vn.edu.phenikaa.canteen.repository.MenuRepository;
import vn.edu.phenikaa.canteen.repository.PickupSlotRepository;
import vn.edu.phenikaa.canteen.repository.QuotaAdjustmentRepository;
import vn.edu.phenikaa.canteen.repository.QuotaRepository;
import vn.edu.phenikaa.canteen.repository.VendorRepository;

/**
 * UC3: lập thực đơn theo ngày và buổi. Thực đơn đi qua hai trạng thái: DRAFT (đang soạn) và
 * PUBLISHED (khách thấy và đặt được). Thực đơn của ngày đã qua chỉ để xem.
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    static final int MAX_LIST_DAYS = 62;

    private final MenuRepository menus;
    private final MenuItemRepository items;
    private final PickupSlotRepository slots;
    private final QuotaRepository quotas;
    private final QuotaAdjustmentRepository adjustments;
    private final DishRepository dishes;
    private final VendorRepository vendors;
    private final VendorAccessChecker access;
    private final MenuGuard guard;
    private final MenuUsageChecker usage;
    private final Clock clock;

    @Transactional
    public MenuSummaryResponse create(CreateMenuRequest req) {
        access.requireManager(req.vendorId());
        if (req.menuDate().isBefore(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.MENU_NOT_EDITABLE, "Không thể lập thực đơn cho ngày đã qua.");
        }
        if (menus.existsByVendorIdAndMenuDateAndMealSession(req.vendorId(), req.menuDate(), req.mealSession())) {
            throw new BusinessException(ErrorCode.MENU_ALREADY_EXISTS);
        }
        Menu menu = new Menu();
        menu.setVendor(vendors.getReferenceById(req.vendorId()));
        menu.setMenuDate(req.menuDate());
        menu.setMealSession(req.mealSession());
        menu.setOrderCutoffTime(req.orderCutoffTime());
        menu.setStatus(MenuStatus.DRAFT);
        return MenuSummaryResponse.from(menus.save(menu));
    }

    /** Mặc định xem 7 ngày kể từ hôm nay. */
    @Transactional(readOnly = true)
    public List<MenuSummaryResponse> list(Long vendorId, LocalDate from, LocalDate to) {
        access.requireManager(vendorId);
        LocalDate start = from != null ? from : LocalDate.now(clock);
        LocalDate end = to != null ? to : start.plusDays(6);
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > MAX_LIST_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Khoảng ngày không hợp lệ (tối đa " + MAX_LIST_DAYS + " ngày).");
        }
        return menus.findByVendorIdAndMenuDateBetweenOrderByMenuDateAscMealSessionAsc(vendorId, start, end).stream()
                .map(MenuSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MenuDetailResponse get(Long menuId) {
        return detail(guard.managed(menuId));
    }

    @Transactional
    public MenuSummaryResponse publish(Long menuId) {
        Menu menu = guard.editable(menuId);
        if (menu.isPublished()) {
            return MenuSummaryResponse.from(menu);          // công bố lại thì không đổi gì
        }
        if (items.countByMenuIdAndAvailableTrue(menuId) == 0 || slots.countByMenuId(menuId) == 0) {
            throw new BusinessException(ErrorCode.MENU_INCOMPLETE);
        }
        menu.setStatus(MenuStatus.PUBLISHED);
        return MenuSummaryResponse.from(menu);
    }

    /** Đưa về nháp để khách không còn thấy. Không cho phép khi đã có suất được giữ hoặc bán. */
    @Transactional
    public MenuSummaryResponse unpublish(Long menuId) {
        Menu menu = guard.editable(menuId);
        if (!menu.isPublished()) {
            return MenuSummaryResponse.from(menu);
        }
        if (quotas.sumReservedByMenuId(menuId) > 0) {
            throw new BusinessException(ErrorCode.MENU_HAS_ORDERS);
        }
        menu.setStatus(MenuStatus.DRAFT);
        return MenuSummaryResponse.from(menu);
    }

    // ---------------- Món trong thực đơn ----------------

    @Transactional
    public MenuItemResponse addItem(Long menuId, AddMenuItemRequest req) {
        Menu menu = guard.editable(menuId);
        Dish dish = dishes.findById(req.dishId()).orElseThrow(() -> BusinessException.notFound("Không tìm thấy món."));
        if (!dish.getVendor().getId().equals(menu.getVendor().getId())) {
            throw new BusinessException(ErrorCode.DISH_VENDOR_MISMATCH);
        }
        if (!dish.isActive()) {
            throw new BusinessException(ErrorCode.DISH_UNAVAILABLE);
        }
        if (items.existsByMenuIdAndDishId(menuId, dish.getId())) {
            throw new BusinessException(ErrorCode.MENU_ITEM_EXISTS);
        }
        int total = req.totalSlots();
        int counter = req.counterSlots() == null ? 0 : req.counterSlots();
        if (counter > total) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Phần dành cho khách tại quầy không được vượt tổng số suất.");
        }

        MenuItem item = new MenuItem();
        item.setMenu(menu);
        item.setDish(dish);
        item.setAvailable(true);
        items.save(item);

        Quota quota = new Quota();
        quota.setMenuItem(item);
        quota.setTotalSlots(total);
        quota.setCounterSlots(counter);
        quota.setReservedSlots(0);
        quotas.save(quota);
        return MenuItemResponse.from(item, quota);
    }

    /** Đánh dấu hết món hoặc bán lại trong ca. Không đụng tới các suất đã giữ. */
    @Transactional
    public MenuItemResponse setAvailability(Long menuId, Long itemId, boolean available) {
        guard.editable(menuId);
        MenuItem item = requireItem(menuId, itemId);
        item.setAvailable(available);
        Quota quota = quotas.findByMenuItemId(itemId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy hạn mức của món."));
        return MenuItemResponse.from(item, quota);
    }

    /** Chỉ gỡ được món chưa có đơn, chưa giữ suất và chưa từng chỉnh hạn mức; còn lại thì tạm ngừng bán. */
    @Transactional
    public void removeItem(Long menuId, Long itemId) {
        guard.editable(menuId);
        MenuItem item = requireItem(menuId, itemId);
        Quota quota = quotas.findByMenuItemIdForUpdate(itemId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy hạn mức của món."));
        if (quota.getReservedSlots() > 0 || usage.isItemOrdered(itemId) || adjustments.existsByQuotaId(quota.getId())) {
            throw new BusinessException(ErrorCode.MENU_ITEM_IN_USE);
        }
        usage.removeFromCarts(itemId);
        quotas.delete(quota);
        quotas.flush();                                     // xóa hạn mức trước vì nó giữ khóa ngoại tới món
        items.delete(item);
    }

    private MenuItem requireItem(Long menuId, Long itemId) {
        return items.findByIdAndMenuId(itemId, menuId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy món trong thực đơn."));
    }

    private MenuDetailResponse detail(Menu menu) {
        List<MenuItem> menuItems = items.findByMenuIdWithDish(menu.getId());
        Map<Long, Quota> quotaByItem = menuItems.isEmpty() ? Map.of()
                : quotas.findByMenuItemIdIn(menuItems.stream().map(MenuItem::getId).toList()).stream()
                        .collect(Collectors.toMap(q -> q.getMenuItem().getId(), Function.identity()));
        List<MenuItemResponse> itemResponses = menuItems.stream()
                .map(mi -> MenuItemResponse.from(mi, quotaByItem.get(mi.getId())))
                .toList();
        List<SlotResponse> slotResponses = slots.findByMenuIdOrderByStartTime(menu.getId()).stream()
                .map(SlotResponse::from).toList();
        return new MenuDetailResponse(MenuSummaryResponse.from(menu), itemResponses, slotResponses);
    }
}
