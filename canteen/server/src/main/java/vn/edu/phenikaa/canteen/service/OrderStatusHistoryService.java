package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.model.Order;
import vn.edu.phenikaa.canteen.model.OrderStatus;
import vn.edu.phenikaa.canteen.model.OrderStatusHistory;
import vn.edu.phenikaa.canteen.repository.OrderStatusHistoryRepository;

/** NFR03: mỗi lần đổi trạng thái ghi lại người thực hiện, thời gian và lý do. Chỉ ghi thêm. */
@Service
@RequiredArgsConstructor
public class OrderStatusHistoryService {

    private static final int MAX_REASON = 255;

    private final OrderStatusHistoryRepository history;
    private final Clock clock;

    /** Bắt buộc chạy trong transaction của thao tác đổi trạng thái để hai việc cùng thành công hoặc cùng hủy. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Order order, OrderStatus from, OrderStatus to, Actor actor, String reason) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.setOrderId(order.getId());
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setActorId(actor.id());
        h.setActorType(actor.type());
        h.setReason(truncate(reason));
        h.setCreatedAt(LocalDateTime.now(clock));
        history.save(h);
    }

    private static String truncate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() <= MAX_REASON ? t : t.substring(0, MAX_REASON);
    }
}
