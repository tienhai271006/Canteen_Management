package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.dto.PageParams;
import vn.edu.phenikaa.canteen.common.dto.PageResponse;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.security.CurrentUser;
import vn.edu.phenikaa.canteen.dto.CancelRequestResponse;
import vn.edu.phenikaa.canteen.dto.OrderHistoryResponse;
import vn.edu.phenikaa.canteen.dto.OrderResponse;
import vn.edu.phenikaa.canteen.model.CancelRequest;
import vn.edu.phenikaa.canteen.model.CancelRequestStatus;
import vn.edu.phenikaa.canteen.model.NotificationType;
import vn.edu.phenikaa.canteen.model.Order;
import vn.edu.phenikaa.canteen.model.OrderStatus;
import vn.edu.phenikaa.canteen.repository.CancelRequestRepository;
import vn.edu.phenikaa.canteen.repository.OrderRepository;
import vn.edu.phenikaa.canteen.repository.OrderStatusHistoryRepository;

/**
 * UC6 phía khách: xem đơn của mình (6.1), tự hủy Pending (6.2), gửi hoặc rút yêu cầu hủy Confirmed (6.3).
 * Mọi hàm lấy id khách từ token và chỉ thấy đơn của mình; đơn người khác trả 404 (BR03).
 * Đổi trạng thái luôn đi qua {@link OrderService#transition}.
 */
@Service
@RequiredArgsConstructor
public class CustomerOrderService {

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final CancelRequestRepository cancelRequests;
    private final OrderService orderService;
    private final NotificationService notifications;
    private final Clock clock;

    @PersistenceContext
    private EntityManager em;

    // ---- UC6.1 ----

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(Set<OrderStatus> statuses, int page, int size) {
        Collection<OrderStatus> filter = statuses == null || statuses.isEmpty()
                ? EnumSet.allOf(OrderStatus.class) : statuses;
        return PageResponse.of(orders.findMine(CurrentUser.id(), filter, PageParams.of(page, size)),
                OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public List<OrderHistoryResponse> history(Long orderId) {
        requireOwnId(orderId);
        return history.findByOrderIdOrderByIdAsc(orderId).stream().map(OrderHistoryResponse::from).toList();
    }

    // ---- UC6.2 ----

    /**
     * Chỉ hủy trực tiếp khi đơn còn Pending tại thời điểm ghi (BR16). Đã Expired hoặc Cancelled thì trả trạng thái hiện có,
     * không trả suất lần nữa vì {@code transition} chỉ chạy khi đơn còn Pending. Nếu quầy vừa xác nhận cùng lúc,
     * khóa lạc quan trên đơn làm đúng một bên thắng (BR13).
     */
    @Transactional
    public OrderResponse cancelPending(Long orderId) {
        Order order = ownDetail(orderId);
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.PENDING) {
            orderService.transition(order, OrderStatus.CANCELLED, Actor.customer(CurrentUser.id()), "Khách hủy sớm");
        } else if (status == OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED,
                    "Quầy đã xác nhận đơn nên không hủy trực tiếp được, hãy gửi yêu cầu hủy để quầy duyệt.");
        } else if (status == OrderStatus.PREPARING || status == OrderStatus.READY) {
            throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED,
                    "Quầy đã bắt đầu chuẩn bị, hãy gửi yêu cầu hỗ trợ ngoại lệ cho quầy.");
        } else if (status == OrderStatus.COMPLETED || status == OrderStatus.NOT_COLLECTED) {
            throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED, "Đơn đã kết thúc nên không thể hủy.");
        }
        return OrderResponse.from(order);
    }

    // ---- UC6.3 ----

    /** Gửi yêu cầu KHÔNG đổi trạng thái đơn (BR17). Mỗi đơn chỉ có một yêu cầu đang chờ. */
    @Transactional
    public CancelRequestResponse requestCancel(Long orderId, String reason) {
        Order order = ownDetail(orderId);
        OrderStatus status = order.getStatus();
        if (status != OrderStatus.CONFIRMED) {
            String msg = switch (status) {
                case PENDING -> "Đơn chưa được quầy xác nhận, bạn có thể hủy trực tiếp.";
                case PREPARING, READY -> "Quầy đã bắt đầu chuẩn bị, hãy gửi yêu cầu hỗ trợ ngoại lệ cho quầy.";
                default -> "Đơn đã kết thúc nên không thể hủy.";
            };
            throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED, msg);
        }
        // Tăng version của đơn: nếu quầy chuyển Preparing cùng lúc thì một trong hai giao dịch bị 409,
        // nên không thể có yêu cầu đang chờ trên đơn đã Preparing (BR17).
        em.lock(order, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        if (cancelRequests.existsByOrderIdAndStatus(orderId, CancelRequestStatus.PENDING)) {
            throw new BusinessException(ErrorCode.CANCEL_REQUEST_PENDING);
        }
        CancelRequest r = new CancelRequest();
        r.setOrderId(orderId);
        r.setRequestedBy(CurrentUser.id());
        r.setReason(reason.trim());
        try {
            cancelRequests.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            // Hai yêu cầu cùng lúc: UNIQUE trên pending_order_id chặn yêu cầu thứ hai.
            throw new BusinessException(ErrorCode.CANCEL_REQUEST_PENDING);
        }
        notifications.notifyVendorMembers(order.getVendor().getId(), NotificationType.CANCEL_REQUEST_RECEIVED,
                "Khách xin hủy đơn " + order.getOrderCode(), "Lý do: " + r.getReason(),
                "CANCEL_REQ:" + r.getId() + ":NEW", "ORDER", orderId);
        return CancelRequestResponse.from(r, order.getOrderCode());
    }

    @Transactional
    public CancelRequestResponse withdrawCancelRequest(Long orderId, Long requestId) {
        Order order = ownDetail(orderId);
        CancelRequest r = cancelRequests.findByIdAndOrderId(requestId, orderId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy yêu cầu hủy."));
        if (r.getStatus() != CancelRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.REQUEST_ALREADY_HANDLED);
        }
        r.setStatus(CancelRequestStatus.WITHDRAWN);
        r.setHandledAt(LocalDateTime.now(clock));
        return CancelRequestResponse.from(r, order.getOrderCode());
    }

    @Transactional(readOnly = true)
    public List<CancelRequestResponse> listCancelRequests(Long orderId) {
        String code = requireOwnId(orderId).getOrderCode();
        return cancelRequests.findByOrderIdOrderByIdDesc(orderId).stream()
                .map(r -> CancelRequestResponse.from(r, code)).toList();
    }

    // ---- helpers ----

    private Order ownDetail(Long orderId) {
        return orders.findDetailById(orderId)
                .filter(o -> o.getCustomerId().equals(CurrentUser.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    private Order requireOwnId(Long orderId) {
        return orders.findById(orderId)
                .filter(o -> o.getCustomerId().equals(CurrentUser.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }
}
