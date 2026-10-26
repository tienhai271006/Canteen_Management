package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.dto.PageResponse;
import vn.edu.phenikaa.canteen.common.storage.FileStorageService;
import vn.edu.phenikaa.canteen.dto.CatalogCriteria;
import vn.edu.phenikaa.canteen.dto.CatalogItemResponse;
import vn.edu.phenikaa.canteen.dto.SlotResponse;
import vn.edu.phenikaa.canteen.model.Area;
import vn.edu.phenikaa.canteen.model.Dish;
import vn.edu.phenikaa.canteen.model.Menu;
import vn.edu.phenikaa.canteen.model.MenuItem;
import vn.edu.phenikaa.canteen.model.MenuStatus;
import vn.edu.phenikaa.canteen.model.Quota;
import vn.edu.phenikaa.canteen.model.Vendor;
import vn.edu.phenikaa.canteen.model.VendorStatus;
import vn.edu.phenikaa.canteen.repository.PickupSlotRepository;
import vn.edu.phenikaa.canteen.repository.QuotaRepository;

/**
 * UC4: khách tìm và lọc món. Chỉ thấy món của thực đơn đã công bố, quầy đang hoạt động, món chưa bị ẩn
 * và chưa bị đánh dấu hết. Quầy tạm ngừng biến mất khỏi kết quả (đơn cũ của quầy vẫn xử lý bình thường).
 * <p>
 * Gốc truy vấn là Quota (không phải MenuItem) vì cần lọc theo số suất còn lại và tất cả quan hệ còn lại
 * đều là nhiều-một, nên nạp được một lần bằng fetch join mà vẫn phân trang đúng.
 * Tìm kiếm không phân biệt hoa thường và dấu ("com" ra "Cơm") nhờ collation utf8mb4_unicode_ci của cột.
 */
@Service
@RequiredArgsConstructor
public class CatalogQueryService {

    private final QuotaRepository quotas;
    private final PickupSlotRepository slots;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<CatalogItemResponse> search(CatalogCriteria criteria, Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        LocalTime nowTime = LocalTime.now(clock);
        LocalDate date = criteria.date() != null ? criteria.date() : today;

        Page<Quota> page = quotas.findAll(specification(criteria, date), pageable);

        List<Long> menuIds = page.getContent().stream()
                .map(q -> q.getMenuItem().getMenu().getId()).distinct().toList();
        Map<Long, List<SlotResponse>> slotsByMenu = new HashMap<>();
        if (!menuIds.isEmpty()) {
            slots.findByMenuIdInOrderByStartTime(menuIds).forEach(s ->
                    slotsByMenu.computeIfAbsent(s.getMenu().getId(), k -> new ArrayList<>()).add(SlotResponse.from(s)));
        }

        return PageResponse.of(page, q -> toResponse(q, today, nowTime, slotsByMenu));
    }

    private CatalogItemResponse toResponse(Quota q, LocalDate today, LocalTime nowTime,
                                           Map<Long, List<SlotResponse>> slotsByMenu) {
        MenuItem item = q.getMenuItem();
        Menu menu = item.getMenu();
        Dish dish = item.getDish();
        Vendor vendor = menu.getVendor();
        Area area = vendor.getArea();

        int remaining = Math.max(q.availableOnline(), 0);
        boolean beforeCutoff = menu.getMenuDate().isAfter(today)
                || (menu.getMenuDate().isEqual(today) && nowTime.isBefore(menu.getOrderCutoffTime()));
        return new CatalogItemResponse(
                item.getId(), menu.getId(), menu.getMenuDate(), menu.getMealSession(), menu.getOrderCutoffTime(),
                vendor.getId(), vendor.getName(), area.getId(), area.getName(), vendor.getPickupLocation(),
                dish.getId(), dish.getName(), dish.getDescription(), dish.getCategory(), dish.getPrice(),
                dish.getImagePath() == null ? null : FileStorageService.PUBLIC_URL_PREFIX + dish.getImagePath(),
                remaining, beforeCutoff && remaining > 0,
                slotsByMenu.getOrDefault(menu.getId(), List.of()));
    }

    private static Specification<Quota> specification(CatalogCriteria c, LocalDate date) {
        return (root, query, cb) -> {
            // Truy vấn đếm (phân trang) không được dùng fetch join, truy vấn lấy dữ liệu thì nạp sẵn một lần.
            boolean fetch = !isCountQuery(query);
            Join<Quota, MenuItem> item = join(root, "menuItem", fetch);
            Join<MenuItem, Menu> menu = join(item, "menu", fetch);
            Join<MenuItem, Dish> dish = join(item, "dish", fetch);
            Join<Menu, Vendor> vendor = join(menu, "vendor", fetch);
            Join<Vendor, Area> area = join(vendor, "area", fetch);

            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(menu.get("status"), MenuStatus.PUBLISHED));
            where.add(cb.equal(vendor.get("status"), VendorStatus.ACTIVE));
            where.add(cb.isTrue(dish.<Boolean>get("active")));
            where.add(cb.isTrue(item.<Boolean>get("available")));
            where.add(cb.equal(menu.get("menuDate"), date));

            if (c.session() != null) {
                where.add(cb.equal(menu.get("mealSession"), c.session()));
            }
            if (c.areaId() != null) {
                where.add(cb.equal(area.get("id"), c.areaId()));
            }
            if (c.vendorId() != null) {
                where.add(cb.equal(vendor.get("id"), c.vendorId()));
            }
            if (c.category() != null) {
                where.add(cb.equal(dish.get("category"), c.category()));
            }
            if (c.minPrice() != null) {
                where.add(cb.greaterThanOrEqualTo(dish.<Long>get("price"), c.minPrice()));
            }
            if (c.maxPrice() != null) {
                where.add(cb.lessThanOrEqualTo(dish.<Long>get("price"), c.maxPrice()));
            }
            if (c.keyword() != null && !c.keyword().isBlank()) {
                String like = "%" + escapeLike(c.keyword().trim().toLowerCase()) + "%";
                where.add(cb.or(
                        cb.like(cb.lower(dish.<String>get("name")), like, '\\'),
                        cb.like(cb.lower(vendor.<String>get("name")), like, '\\')));
            }
            if (c.availableOnly()) {
                where.add(cb.greaterThan(remaining(cb, root), 0));
            }

            if (fetch) {
                query.orderBy(orderBy(c.sort(), cb, root, dish));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    private static jakarta.persistence.criteria.Expression<Integer> remaining(CriteriaBuilder cb,
                                                                               From<?, Quota> root) {
        return cb.diff(cb.diff(root.<Integer>get("totalSlots"), root.<Integer>get("counterSlots")),
                root.<Integer>get("reservedSlots"));
    }

    private static List<Order> orderBy(CatalogCriteria.Sort sort, CriteriaBuilder cb, From<?, Quota> root,
                                       Join<MenuItem, Dish> dish) {
        List<Order> orders = new ArrayList<>();
        CatalogCriteria.Sort s = sort == null ? CatalogCriteria.Sort.NAME : sort;
        switch (s) {
            case PRICE_ASC -> orders.add(cb.asc(dish.get("price")));
            case PRICE_DESC -> orders.add(cb.desc(dish.get("price")));
            case REMAINING_DESC -> orders.add(cb.desc(remaining(cb, root)));
            default -> orders.add(cb.asc(dish.get("name")));
        }
        orders.add(cb.asc(root.get("id")));                 // thứ tự ổn định để phân trang không lặp hoặc sót dòng
        return orders;
    }

    private static boolean isCountQuery(CriteriaQuery<?> query) {
        Class<?> type = query.getResultType();
        return Long.class.equals(type) || long.class.equals(type);
    }

    /** Fetch join của Hibernate vừa là Fetch vừa là Join nên ép kiểu được; cách này cho phép dùng chung điều kiện lọc. */
    @SuppressWarnings("unchecked")
    private static <X, Y> Join<X, Y> join(From<?, X> from, String attribute, boolean fetch) {
        if (fetch) {
            return (Join<X, Y>) (Join<?, ?>) from.fetch(attribute);
        }
        return from.join(attribute);
    }

    /** Thoát ký tự đại diện của LIKE để người dùng gõ "%" hoặc "_" không khớp mọi thứ. */
    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
