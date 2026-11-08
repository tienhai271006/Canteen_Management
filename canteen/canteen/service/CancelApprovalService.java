package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

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
import vn.edu.phenikaa.canteen.dto.CancelRequestResponse;
import vn.edu.phenikaa.canteen.dto.DecisionRequest;
import vn.edu.phenikaa.canteen.model.CancelRequest;
import vn.edu.phenikaa.canteen.model.CancelRequestStatus;
import vn.edu.phenikaa.canteen.model.NotificationType;
import vn.edu.phenikaa.canteen.model.Order;
import vn.edu.phenikaa.canteen.model.OrderStatus;
import vn.edu.phenikaa.canteen.repository.CancelRequestRepository;
import vn.edu.phenikaa.canteen.repository.OrderRepository;

/**
 * UC7.6: quầy duyệt hoặc từ chối yêu cầu hủy đơn Confirmed. Lưu người xử lý, lý do, thời gian và kênh (BR19).
 * Hết thời gian phản hồi KHÔNG tự coi là duyệt (BR17), nên không có job nào gọi vào đây.
 * Duyệt thì đơn chuyển Cancelled qua {@link OrderService#transition}, suất được trả đúng một lần (BR14)
 * và không tự cộng vi phạm (BR25).
 */
@Service
@RequiredArgsConstructor
public class CancelApprovalService {

    private final CancelRequestRepository requests;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final OrderCodeLookup codes;
    private final NotificationService notifications;
    private final VendorAccessChecker access;
    private final Clock clock;

    /** Mặc định chỉ liệt kê các yêu cầu đang chờ. */
    @Transactional(readOnly = true)
    public PageResponse<CancelRequestResponse> list(Long vendorId, Set<CancelRequestStatus> statuses,
                                                    int page, int size) {
        access.requireMember(vendorId);
        Collection<CancelRequestStatus> filter = statuses == null || statuses.isEmpty()
                ? EnumSet.of(CancelRequestStatus.PENDING) : statuses;
        Page<CancelRequest> result = requests.findByVendor(vendorId, filter, PageParams.of(page, size));
        Map<Long, String> codeMap = codes.codes(result.getContent().stream().map(CancelRequest::getOrderId).toList());
        return PageResponse.of(result, r -> CancelRequestResponse.from(r, codeMap.get(r.getOrderId())));
    }

    @Transactional
    public CancelRequestResponse approve(Long requestId, DecisionRequest decision) {
        CancelRequest r = load(requestId);
        Order order = orders.findDetailById(r.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        access.requireMember(order.getVendor().getId());
        requirePending(r);
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED,
                    "Đơn không còn ở trạng thái Confirmed nên không duyệt hủy được. Hãy từ chối yêu cầu và ghi lý do.");
        }
        Actor actor = Actor.of(CurrentUser.get());
        // Không gửi thông báo "đơn bị hủy" riêng: khách nhận một thông báo quyết định bên dưới.
        orderService.transition(order, OrderStatus.CANCELLED, actor,
                "Khách xin hủy, quầy đồng ý: " + decision.reason().trim(), false);
        close(r, CancelRequestStatus.APPROVED, decision);
        notifyDecision(order, r, true, decision);
        return CancelRequestResponse.from(r, order.getOrderCode());
    }

    /** Từ chối thì đơn giữ nguyên trạng thái và tiếp tục được phục vụ. */
    @Transactional
    public CancelRequestResponse reject(Long requestId, DecisionRequest decision) {
        CancelRequest r = load(requestId);
        Order order = orders.findById(r.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        access.requireMember(order.getVendor().getId());
        requirePending(r);
        close(r, CancelRequestStatus.REJECTED, decision);
        notifyDecision(order, r, false, decision);
        return CancelRequestResponse.from(r, order.getOrderCode());
    }

    /** Báo khách quyết định của quầy kèm lý do, có email nếu email trường đã xác minh (BR19, BR28). */
    private void notifyDecision(Order order, CancelRequest r, boolean approved, DecisionRequest decision) {
        String code = order.getOrderCode();
        notifications.notifyUser(order.getCustomerId(), NotificationType.CANCEL_REQUEST_DECIDED,
                approved ? "Yêu cầu hủy đơn " + code + " đã được chấp nhận"
                        : "Yêu cầu hủy đơn " + code + " bị từ chối",
                "Quầy phản hồi: " + decision.reason().trim()
                        + (approved ? ". Suất và ưu đãi (nếu có) đã được trả lại." : ". Đơn của bạn vẫn được phục vụ."),
                "CANCEL_REQ:" + r.getId() + ":" + r.getStatus(), "ORDER", order.getId(), true);
    }

    private CancelRequest load(Long requestId) {
        return requests.findById(requestId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy yêu cầu hủy."));
    }

    private static void requirePending(CancelRequest r) {
        if (r.getStatus() != CancelRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.REQUEST_ALREADY_HANDLED);
        }
    }

    private void close(CancelRequest r, CancelRequestStatus status, DecisionRequest decision) {
        r.setStatus(status);
        r.setHandledBy(CurrentUser.id());
        r.setHandledAt(LocalDateTime.now(clock));
        r.setHandleReason(decision.reason().trim());
        r.setHandleChannel(decision.channelOrDefault());
    }
}
