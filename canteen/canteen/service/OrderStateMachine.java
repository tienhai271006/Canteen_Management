package vn.edu.phenikaa.canteen.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.model.OrderStatus;

/**
 * Bảng chuyển trạng thái hợp lệ, đặt ở MỘT nơi duy nhất. Mọi thay đổi trạng thái đều đi qua
 * {@code OrderService.transition}, và hàm đó hỏi lớp này trước.
 * <pre>
 * PENDING   -> CONFIRMED | CANCELLED | EXPIRED
 * CONFIRMED -> PREPARING | CANCELLED
 * PREPARING -> READY
 * READY     -> COMPLETED | NOT_COLLECTED
 * COMPLETED, CANCELLED, EXPIRED, NOT_COLLECTED: kết thúc
 * </pre>
 * Ai được phép bấm chuyển nào (khách, nhân viên, hệ thống) do service của từng use case quyết định (Pha 7, 9, 10).
 */
@Component
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> NEXT = new EnumMap<>(OrderStatus.class);

    static {
        NEXT.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.EXPIRED));
        NEXT.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.PREPARING, OrderStatus.CANCELLED));
        NEXT.put(OrderStatus.PREPARING, EnumSet.of(OrderStatus.READY));
        NEXT.put(OrderStatus.READY, EnumSet.of(OrderStatus.COMPLETED, OrderStatus.NOT_COLLECTED));
        NEXT.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        NEXT.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        NEXT.put(OrderStatus.EXPIRED, EnumSet.noneOf(OrderStatus.class));
        NEXT.put(OrderStatus.NOT_COLLECTED, EnumSet.noneOf(OrderStatus.class));
    }

    public Set<OrderStatus> nextOf(OrderStatus from) {
        return Set.copyOf(NEXT.get(from));
    }

    public boolean canTransition(OrderStatus from, OrderStatus to) {
        return NEXT.get(from).contains(to);
    }

    public void require(OrderStatus from, OrderStatus to) {
        if (!canTransition(from, to)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_TRANSITION,
                    "Đơn không thể chuyển từ " + from + " sang " + to + ".");
        }
    }

    public boolean isTerminal(OrderStatus status) {
        return NEXT.get(status).isEmpty();
    }

    /**
     * Chuyển sang trạng thái này thì phải trả suất (BR14). Vì chuyển trạng thái chỉ xảy ra đúng một lần
     * (trạng thái kết thúc không đi tiếp được) nên suất được trả đúng một lần.
     * NOT_COLLECTED KHÔNG trả suất: món đã làm, xử lý bán lại hoặc hao hụt ở Pha 10 (BR15).
     */
    public boolean releasesQuota(OrderStatus to) {
        return to == OrderStatus.CANCELLED || to == OrderStatus.EXPIRED;
    }
}
