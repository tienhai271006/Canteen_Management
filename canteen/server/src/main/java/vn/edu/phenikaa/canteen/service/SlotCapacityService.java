package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.dto.SlotCapacityResponse;
import vn.edu.phenikaa.canteen.model.Order;
import vn.edu.phenikaa.canteen.model.OrderItem;
import vn.edu.phenikaa.canteen.model.OrderStatus;
import vn.edu.phenikaa.canteen.model.PickupSlot;
import vn.edu.phenikaa.canteen.repository.OrderRepository;
import vn.edu.phenikaa.canteen.repository.PickupSlotRepository;

/**
 * Giữ và trả suất theo KHUNG GIỜ NHẬN (UC3.4, UC3.5), song song với {@link QuotaService} (suất theo món).
 * Cùng nguyên tắc: một câu UPDATE có điều kiện, chạy trong transaction của người gọi, nên đơn lỗi thì suất khung
 * cũng được hoàn lại. Khung có {@code capacity} null là chưa giới hạn và luôn giữ được.
 */
@Service
@RequiredArgsConstructor
public class SlotCapacityService {

    private final PickupSlotRepository slots;
    private final OrderRepository orders;
    private final Clock clock;

    /** Các khung của một thực đơn kèm số đang giữ / đã dùng / còn nhận, theo giờ bắt đầu. */
    @Transactional(readOnly = true)
    public List<SlotCapacityResponse> view(Long menuId) {
        Map<Long, Integer> used = new HashMap<>();
        for (Object[] row : orders.sumQuantityBySlot(menuId, List.of(OrderStatus.COMPLETED, OrderStatus.NOT_COLLECTED))) {
            used.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return slots.findByMenuIdOrderByStartTime(menuId).stream()
                .map(s -> SlotCapacityResponse.of(s, used.getOrDefault(s.getId(), 0)))
                .toList();
    }

    public SlotCapacityResponse viewOne(PickupSlot slot) {
        return view(slot.getMenu().getId()).stream().filter(v -> v.id().equals(slot.getId())).findFirst().orElseThrow();
    }

    /** @param qty tổng số suất của đơn, cộng mọi món */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Long slotId, int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("Số suất phải dương.");
        }
        if (slots.tryReserve(slotId, qty, LocalDateTime.now(clock)) != 1) {
            throw new BusinessException(ErrorCode.SLOT_FULL);
        }
    }

    /** Trả đúng một lần khi đơn chuyển sang CANCELLED hoặc EXPIRED, cùng chỗ với {@link QuotaService#releaseForOrder}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseForOrder(Order order) {
        int qty = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        if (qty <= 0) {
            return;
        }
        if (slots.tryRelease(order.getPickupSlot().getId(), qty, LocalDateTime.now(clock)) != 1) {
            throw new IllegalStateException("Không thể trả " + qty + " suất cho khung " + order.getPickupSlot().getId()
                    + ": số suất đang giữ ít hơn.");
        }
    }
}
