package vn.edu.phenikaa.canteen.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.dto.PageParams;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.security.CurrentUser;
import vn.edu.phenikaa.canteen.common.security.VendorAccessChecker;
import vn.edu.phenikaa.canteen.dto.WasteHandlingRequest;
import vn.edu.phenikaa.canteen.dto.WasteItemRequest;
import vn.edu.phenikaa.canteen.dto.WasteOrderResponse;
import vn.edu.phenikaa.canteen.model.Order;
import vn.edu.phenikaa.canteen.model.OrderItem;
import vn.edu.phenikaa.canteen.model.OrderStatus;
import vn.edu.phenikaa.canteen.model.WasteOutcome;
import vn.edu.phenikaa.canteen.model.WasteRecord;
import vn.edu.phenikaa.canteen.repository.OrderRepository;
import vn.edu.phenikaa.canteen.repository.WasteRecordRepository;

/**
 * UC3.7, BR15: món đã làm mà không được nhận không tự cộng lại suất. Nhân viên kiểm tra thực tế rồi ghi từng phần là
 * BÁN LẠI hoặc HAO HỤT. Chỉ phần bán lại mới trả suất (đúng một lần), phần hao hụt không đổi hạn mức; không có doanh thu
 * cho đơn cũ (BR34, đơn vẫn NOT_COLLECTED).
 * Ví dụ trong tài liệu: đơn giữ 2 suất không nhận, bán lại 1 và hao hụt 1 thì quầy còn thêm đúng 1 suất để bán.
 * Khóa dòng đơn khi ghi để hai người xử lý cùng lúc không làm tổng vượt số lượng đã đặt.
 */
@Service
@RequiredArgsConstructor
public class WasteHandlingService {

    private final OrderRepository orders;
    private final WasteRecordRepository waste;
    private final QuotaService quotaService;
    private final VendorAccessChecker access;
    private final JdbcClient jdbc;

    /** Các đơn không nhận của quầy còn phần món chưa xử lý, đơn mới nhất trước. */
    @Transactional(readOnly = true)
    public List<WasteOrderResponse> listPending(Long vendorId, int page, int size) {
        access.requireMember(vendorId);
        Pageable p = PageParams.of(page, size);
        List<Long> ids = jdbc.sql("""
                        SELECT o.id FROM orders o
                         WHERE o.vendor_id = :v AND o.status = 'NOT_COLLECTED'
                           AND EXISTS (SELECT 1 FROM order_item oi WHERE oi.order_id = o.id AND oi.quantity >
                                 COALESCE((SELECT SUM(w.quantity) FROM waste_record w
                                            WHERE w.order_id = o.id AND w.dish_id = oi.dish_id), 0))
                         ORDER BY o.id DESC LIMIT :limit OFFSET :offset""")
                .param("v", vendorId).param("limit", p.getPageSize()).param("offset", p.getOffset())
                .query(Long.class).list();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Order> byId = new HashMap<>();
        orders.findAllById(ids).forEach(o -> byId.put(o.getId(), o));
        List<Order> ordered = new ArrayList<>();
        ids.forEach(id -> ordered.add(byId.get(id)));
        return views(ordered);
    }

    @Transactional(readOnly = true)
    public WasteOrderResponse get(Long orderId) {
        Order order = orders.findDetailById(orderId).orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        access.requireMember(order.getVendor().getId());
        return views(List.of(order)).get(0);
    }

    @Transactional
    public WasteOrderResponse handle(Long orderId, WasteHandlingRequest req) {
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        access.requireMember(order.getVendor().getId());
        if (order.getStatus() != OrderStatus.NOT_COLLECTED) {
            throw new BusinessException(ErrorCode.WASTE_NOT_ALLOWED);
        }
        Map<Long, OrderItem> items = new HashMap<>();
        order.getItems().forEach(i -> items.put(i.getId(), i));

        Set<Long> seen = new HashSet<>();
        Long actor = CurrentUser.id();
        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        for (WasteItemRequest r : req.items()) {
            if (!seen.add(r.orderItemId())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Một món xuất hiện hai lần trong yêu cầu.");
            }
            OrderItem item = items.get(r.orderItemId());
            if (item == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Món không thuộc đơn này.");
            }
            int resold = r.resoldQuantity() == null ? 0 : r.resoldQuantity();
            int wasted = r.wastedQuantity() == null ? 0 : r.wastedQuantity();
            if (resold + wasted <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Cần ghi ít nhất một suất bán lại hoặc hao hụt.");
            }
            long remaining = item.getQuantity() - waste.totalProcessed(orderId, item.getDishId());
            if (resold + wasted > remaining) {
                throw new BusinessException(ErrorCode.WASTE_EXCEEDS_REMAINING,
                        "Món " + item.getDishName() + " còn " + remaining + " suất chưa xử lý.");
            }
            if (resold > 0) {
                save(orderId, item, resold, WasteOutcome.RESOLD, note, actor);
                quotaService.release(item.getMenuItemId(), resold);   // chỉ phần bán lại mới tăng lượng có thể bán
            }
            if (wasted > 0) {
                save(orderId, item, wasted, WasteOutcome.WASTED, note, actor);
            }
        }
        return views(List.of(order)).get(0);
    }

    private void save(Long orderId, OrderItem item, int qty, WasteOutcome outcome, String note, Long actor) {
        WasteRecord w = new WasteRecord();
        w.setOrderId(orderId);
        w.setDishId(item.getDishId());
        w.setQuantity(qty);
        w.setOutcome(outcome);
        w.setNote(note);
        w.setRecordedBy(actor);
        waste.save(w);
    }

    private List<WasteOrderResponse> views(List<Order> list) {
        Map<String, Integer> totals = new HashMap<>();
        waste.totalsByOrders(list.stream().map(Order::getId).toList()).forEach(t ->
                totals.put(t.getOrderId() + ":" + t.getDishId() + ":" + t.getOutcome(), t.getTotal().intValue()));
        return list.stream().map(o -> new WasteOrderResponse(o.getId(), o.getOrderCode(),
                o.getItems().stream().map(i -> {
                    int resold = totals.getOrDefault(o.getId() + ":" + i.getDishId() + ":" + WasteOutcome.RESOLD, 0);
                    int wasted = totals.getOrDefault(o.getId() + ":" + i.getDishId() + ":" + WasteOutcome.WASTED, 0);
                    return new WasteOrderResponse.Item(i.getId(), i.getDishId(), i.getDishName(), i.getQuantity(),
                            resold, wasted, i.getQuantity() - resold - wasted);
                }).toList())).toList();
    }
}
