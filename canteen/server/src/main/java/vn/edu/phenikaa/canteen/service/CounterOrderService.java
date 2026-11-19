package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.dto.PageParams;
import vn.edu.phenikaa.canteen.common.dto.PageResponse;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.security.CurrentUser;
import vn.edu.phenikaa.canteen.common.security.VendorAccessChecker;
import vn.edu.phenikaa.canteen.dto.CompleteOrderRequest;
import vn.edu.phenikaa.canteen.dto.OrderResponse;
import vn.edu.phenikaa.canteen.model.CancelRequestStatus;
import vn.edu.phenikaa.canteen.model.NotificationType;
import vn.edu.phenikaa.canteen.model.Order;
import vn.edu.phenikaa.canteen.model.OrderStatus;
import vn.edu.phenikaa.canteen.model.PaymentStatus;
import vn.edu.phenikaa.canteen.repository.CancelRequestRepository;
import vn.edu.phenikaa.canteen.repository.OrderRepository;

/**
 * UC7.1 đến UC7.5: nhân viên quầy đưa đơn đi qua Confirmed, Preparing, Ready, Completed.
 * Mọi hàm lấy quầy từ chính đơn trong DB rồi kiểm tra thành viên, không tin id quầy trên URL (NFR01, BR03).
 * Đổi trạng thái đi qua {@link OrderService#transition} nên lịch sử và việc trả suất luôn đúng một chỗ.
 * Không nhận món (UC7.8) thuộc Pha 10; job hết hạn Pending thuộc Pha 9.
 */
@Service
@RequiredArgsConstructor
public class CounterOrderService {

    /** Hàng đợi mặc định của quầy: các đơn còn phải xử lý. */
    private static final Set<OrderStatus> ACTIVE =
            EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY);

    private final OrderRepository orders;
    private final CancelRequestRepository cancelRequests;
    private final OrderService orderService;
    private final PickupDeadlineCalculator deadlines;
    private final NotificationService notifications;
    private final VendorAccessChecker access;
    private final Clock clock;

    private static final java.time.format.DateTimeFormatter WHEN =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(Long vendorId, Set<OrderStatus> statuses, int page, int size) {
        access.requireMember(vendorId);
        Collection<OrderStatus> filter = statuses == null || statuses.isEmpty() ? ACTIVE : statuses;
        return PageResponse.of(orders.findByVendorAndStatuses(vendorId, filter, PageParams.of(page, size)),
                OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long orderId) {
        return OrderResponse.from(loadForCounter(orderId));
    }

    /** Khách đọc mã đơn tại quầy (UC7.4). Chỉ tìm trong quầy mà người dùng thuộc về. */
    @Transactional(readOnly = true)
    public OrderResponse findByCode(Long vendorId, String code) {
        access.requireMember(vendorId);
        return orders.findDetailByVendorAndCode(vendorId, code.trim())
                .map(OrderResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    /** UC7.1: nhận Pending còn hạn. Đơn đã quá hạn xác nhận thì không nhận lại (BR13). Không giữ suất lần hai (BR14). */
    @Transactional
    public OrderResponse confirm(Long orderId) {
        Order order = loadForCounter(orderId);
        LocalDateTime now = LocalDateTime.now(clock);
        boolean overdue = order.getStatus() == OrderStatus.PENDING
                && order.getPendingExpiresAt() != null && !now.isBefore(order.getPendingExpiresAt());
        if (order.getStatus() == OrderStatus.EXPIRED || overdue) {
            throw new BusinessException(ErrorCode.ORDER_EXPIRED);
        }
        orderService.transition(order, OrderStatus.CONFIRMED, actor(), null);
        return OrderResponse.from(order);
    }

    /**
     * UC7.5: quầy không thể phục vụ, ghi lý do, đơn sang Cancelled và trả suất. Không tính lỗi khách.
     * Đơn Confirmed đang có yêu cầu hủy chờ thì phải xử lý yêu cầu trước (BR17).
     */
    @Transactional
    public OrderResponse reject(Long orderId, String reason) {
        Order order = loadForCounter(orderId);
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED,
                    "Chỉ từ chối được đơn Pending hoặc Confirmed. Đơn đã chuẩn bị cần quản lý xử lý ngoại lệ.");
        }
        requireNoPendingCancelRequest(order.getId());
        orderService.transition(order, OrderStatus.CANCELLED, actor(), "Quầy không thể phục vụ: " + reason.trim());
        return OrderResponse.from(order);
    }

    /** UC7.2: phải giải quyết yêu cầu hủy đang chờ trước khi chuẩn bị (BR17). */
    @Transactional
    public OrderResponse startPreparing(Long orderId) {
        Order order = loadForCounter(orderId);
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            requireNoPendingCancelRequest(order.getId());
        }
        orderService.transition(order, OrderStatus.PREPARING, actor(), null);
        return OrderResponse.from(order);
    }

    /**
     * UC7.3: chuyển Ready và chốt hạn nhận (BR22). Nếu đã có hạn được quầy duyệt gia hạn từ trước (UC7.7)
     * thì giữ hạn đó, không tính lại.
     */
    @Transactional
    public OrderResponse markReady(Long orderId) {
        Order order = loadForCounter(orderId);
        // Tự gửi thông báo sau khi có hạn nhận để nội dung nêu đúng hạn (BR22).
        orderService.transition(order, OrderStatus.READY, actor(), null, false);
        if (order.getPickupDeadline() == null) {
            var slot = order.getPickupSlot();
            order.setPickupDeadline(deadlines.compute(slot.getMenu().getMenuDate(), slot.getEndTime(),
                    order.getReadyAt()));
        }
        notifications.notifyUser(order.getCustomerId(), NotificationType.ORDER_READY,
                "Đơn " + order.getOrderCode() + " đã sẵn sàng",
                "Hãy đến " + order.getVendor().getPickupLocation() + " nhận món trước "
                        + order.getPickupDeadline().format(WHEN) + ".",
                "ORDER:" + order.getId() + ":READY", "ORDER", order.getId(), false);
        return OrderResponse.from(order);
    }

    /**
     * UC7.4, BR20: chỉ Completed khi đúng mã đơn, đã thu đúng số tiền sau giảm giá và đã bàn giao món.
     * Thanh toán và hoàn tất được ghi trong cùng một transaction nên không có đơn Completed mà chưa Paid.
     */
    @Transactional
    public OrderResponse complete(Long orderId, CompleteOrderRequest req) {
        Order order = loadForCounter(orderId);
        if (order.getStatus() != OrderStatus.READY) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_TRANSITION, "Chỉ bàn giao được đơn đang ở trạng thái Ready.");
        }
        if (!order.getOrderCode().equalsIgnoreCase(req.orderCode().trim())) {
            throw new BusinessException(ErrorCode.ORDER_CODE_MISMATCH);
        }
        if (req.amountReceived().longValue() != order.getTotalAmount()) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    "Số tiền phải thu là " + order.getTotalAmount() + "đ (đã trừ giảm giá).");
        }
        if (!Boolean.TRUE.equals(req.handedOver())) {
            throw new BusinessException(ErrorCode.HANDOVER_NOT_CONFIRMED);
        }
        order.setPaymentStatus(PaymentStatus.PAID);
        orderService.transition(order, OrderStatus.COMPLETED, actor(), null);
        return OrderResponse.from(order);
    }

    // ---- helpers ----

    private Order loadForCounter(Long orderId) {
        Order order = orders.findDetailById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        access.requireMember(order.getVendor().getId());
        return order;
    }

    private void requireNoPendingCancelRequest(Long orderId) {
        if (cancelRequests.existsByOrderIdAndStatus(orderId, CancelRequestStatus.PENDING)) {
            throw new BusinessException(ErrorCode.CANCEL_REQUEST_PENDING,
                    "Đơn đang có yêu cầu hủy chờ xử lý, hãy duyệt hoặc từ chối trước.");
        }
    }

    private static Actor actor() {
        return Actor.of(CurrentUser.get());
    }
}
