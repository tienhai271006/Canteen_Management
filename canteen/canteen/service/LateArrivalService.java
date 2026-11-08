package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.dto.PageParams;
import vn.edu.phenikaa.canteen.common.dto.PageResponse;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.security.CurrentUser;
import vn.edu.phenikaa.canteen.common.security.VendorAccessChecker;
import vn.edu.phenikaa.canteen.dto.DecisionRequest;
import vn.edu.phenikaa.canteen.dto.LateArrivalCreateRequest;
import vn.edu.phenikaa.canteen.dto.LateArrivalDecisionRequest;
import vn.edu.phenikaa.canteen.dto.LateArrivalResponse;
import vn.edu.phenikaa.canteen.model.HandleChannel;
import vn.edu.phenikaa.canteen.model.LateArrivalRequest;
import vn.edu.phenikaa.canteen.model.LateArrivalStatus;
import vn.edu.phenikaa.canteen.model.NotificationType;
import vn.edu.phenikaa.canteen.model.Order;
import vn.edu.phenikaa.canteen.model.OrderStatus;
import vn.edu.phenikaa.canteen.repository.LateArrivalRequestRepository;
import vn.edu.phenikaa.canteen.repository.OrderRepository;

/**
 * UC6.4 (khách báo đến muộn) và UC7.7 (quầy quyết định). Yêu cầu đang chờ KHÔNG tự gia hạn.
 * Khi quầy chấp thuận, hạn nhận mới được ghi thẳng vào đơn và thay hạn cũ (BR22). Nếu đơn chưa Ready thì
 * {@code CounterOrderService.markReady} giữ nguyên hạn đã thỏa thuận thay vì tính lại.
 * Quyết định (người, lý do, thời gian, kênh) nằm trên bản ghi yêu cầu để làm lịch sử (BR19).
 * Hủy ngoại lệ từ Preparing/Ready chưa hỗ trợ vì bảng chuyển trạng thái không có cạnh đó.
 */
@Service
@RequiredArgsConstructor
public class LateArrivalService {

    private static final Set<OrderStatus> ELIGIBLE =
            EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY);

    private final LateArrivalRequestRepository requests;
    private final OrderRepository orders;
    private final OrderCodeLookup codes;
    private final NotificationService notifications;
    private final VendorAccessChecker access;
    private final Clock clock;

    private static final java.time.format.DateTimeFormatter WHEN =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");

    // ---- khách (UC6.4) ----

    @Transactional
    public LateArrivalResponse request(Long orderId, LateArrivalCreateRequest req) {
        Order order = ownOrder(orderId);
        if (!ELIGIBLE.contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.LATE_ARRIVAL_NOT_ALLOWED);
        }
        if (!req.proposedArrivalAt().isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Giờ đến đề xuất phải sau thời điểm hiện tại.");
        }
        if (requests.existsByOrderIdAndStatus(orderId, LateArrivalStatus.PENDING)) {
            throw new BusinessException(ErrorCode.LATE_ARRIVAL_PENDING);
        }
        LateArrivalRequest r = new LateArrivalRequest();
        r.setOrderId(orderId);
        r.setRequestedBy(CurrentUser.id());
        r.setReason(req.reason().trim());
        r.setProposedArrivalAt(req.proposedArrivalAt());
        try {
            requests.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.LATE_ARRIVAL_PENDING);
        }
        notifications.notifyVendorMembers(order.getVendor().getId(), NotificationType.LATE_ARRIVAL_RECEIVED,
                "Khách báo đến muộn, đơn " + order.getOrderCode(),
                "Giờ đến đề xuất: " + req.proposedArrivalAt().format(WHEN) + ". Lý do: " + r.getReason(),
                "LATE_REQ:" + r.getId() + ":NEW", "ORDER", orderId);
        return LateArrivalResponse.from(r, order.getOrderCode());
    }

    @Transactional
    public LateArrivalResponse withdraw(Long orderId, Long requestId) {
        Order order = ownOrder(orderId);
        LateArrivalRequest r = requests.findByIdAndOrderId(requestId, orderId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy yêu cầu hỗ trợ."));
        requirePending(r);
        r.setStatus(LateArrivalStatus.WITHDRAWN);
        r.setHandledAt(LocalDateTime.now(clock));
        return LateArrivalResponse.from(r, order.getOrderCode());
    }

    @Transactional(readOnly = true)
    public List<LateArrivalResponse> listForOrder(Long orderId) {
        String code = ownOrder(orderId).getOrderCode();
        return requests.findByOrderIdOrderByIdDesc(orderId).stream()
                .map(r -> LateArrivalResponse.from(r, code)).toList();
    }

    // ---- quầy (UC7.7) ----

    @Transactional(readOnly = true)
    public PageResponse<LateArrivalResponse> list(Long vendorId, Set<LateArrivalStatus> statuses, int page, int size) {
        access.requireMember(vendorId);
        Collection<LateArrivalStatus> filter = statuses == null || statuses.isEmpty()
                ? EnumSet.of(LateArrivalStatus.PENDING) : statuses;
        Page<LateArrivalRequest> result = requests.findByVendor(vendorId, filter, PageParams.of(page, size));
        Map<Long, String> codeMap = codes.codes(result.getContent().stream().map(LateArrivalRequest::getOrderId).toList());
        return PageResponse.of(result, r -> LateArrivalResponse.from(r, codeMap.get(r.getOrderId())));
    }

    @Transactional
    public LateArrivalResponse approve(Long requestId, LateArrivalDecisionRequest decision) {
        LateArrivalRequest r = load(requestId);
        Order order = orders.findById(r.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        access.requireMember(order.getVendor().getId());
        requirePending(r);
        if (!ELIGIBLE.contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.LATE_ARRIVAL_NOT_ALLOWED,
                    "Đơn đã kết thúc nên không gia hạn được. Hãy từ chối yêu cầu và ghi lý do.");
        }
        if (!decision.newDeadline().isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Hạn nhận mới phải sau thời điểm hiện tại.");
        }
        order.setPickupDeadline(decision.newDeadline());
        r.setApprovedDeadline(decision.newDeadline());
        close(r, LateArrivalStatus.APPROVED, decision.reason(), decision.channelOrDefault());
        notifications.notifyUser(order.getCustomerId(), NotificationType.LATE_ARRIVAL_DECIDED,
                "Quầy đồng ý giữ món cho đơn " + order.getOrderCode(),
                "Hạn nhận mới: " + decision.newDeadline().format(WHEN) + ". Quầy phản hồi: " + decision.reason().trim(),
                "LATE_REQ:" + r.getId() + ":" + r.getStatus(), "ORDER", order.getId(), true);
        return LateArrivalResponse.from(r, order.getOrderCode());
    }

    @Transactional
    public LateArrivalResponse reject(Long requestId, DecisionRequest decision) {
        LateArrivalRequest r = load(requestId);
        Order order = orders.findById(r.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        access.requireMember(order.getVendor().getId());
        requirePending(r);
        close(r, LateArrivalStatus.REJECTED, decision.reason(), decision.channelOrDefault());
        notifications.notifyUser(order.getCustomerId(), NotificationType.LATE_ARRIVAL_DECIDED,
                "Quầy chưa thể gia hạn cho đơn " + order.getOrderCode(),
                "Quầy phản hồi: " + decision.reason().trim() + ". Hạn nhận cũ vẫn được giữ nguyên.",
                "LATE_REQ:" + r.getId() + ":" + r.getStatus(), "ORDER", order.getId(), true);
        return LateArrivalResponse.from(r, order.getOrderCode());
    }

    // ---- helpers ----

    private LateArrivalRequest load(Long requestId) {
        return requests.findById(requestId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy yêu cầu hỗ trợ."));
    }

    private Order ownOrder(Long orderId) {
        return orders.findById(orderId)
                .filter(o -> o.getCustomerId().equals(CurrentUser.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    private static void requirePending(LateArrivalRequest r) {
        if (r.getStatus() != LateArrivalStatus.PENDING) {
            throw new BusinessException(ErrorCode.REQUEST_ALREADY_HANDLED);
        }
    }

    private void close(LateArrivalRequest r, LateArrivalStatus status, String reason, HandleChannel channel) {
        r.setStatus(status);
        r.setHandledBy(CurrentUser.id());
        r.setHandledAt(LocalDateTime.now(clock));
        r.setHandleReason(reason.trim());
        r.setHandleChannel(channel);
    }
}
