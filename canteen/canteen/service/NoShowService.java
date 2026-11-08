package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.dto.PageParams;
import vn.edu.phenikaa.canteen.common.dto.PageResponse;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.security.CurrentUser;
import vn.edu.phenikaa.canteen.common.security.VendorAccessChecker;
import vn.edu.phenikaa.canteen.dto.NoShowResponse;
import vn.edu.phenikaa.canteen.dto.NotCollectedRequest;
import vn.edu.phenikaa.canteen.dto.OrderResponse;
import vn.edu.phenikaa.canteen.model.Incident;
import vn.edu.phenikaa.canteen.model.IncidentStatus;
import vn.edu.phenikaa.canteen.model.LateArrivalStatus;
import vn.edu.phenikaa.canteen.model.NotificationType;
import vn.edu.phenikaa.canteen.model.Order;
import vn.edu.phenikaa.canteen.model.OrderStatus;
import vn.edu.phenikaa.canteen.repository.IncidentRepository;
import vn.edu.phenikaa.canteen.repository.LateArrivalRequestRepository;
import vn.edu.phenikaa.canteen.repository.OrderRepository;

/**
 * UC7.8: ghi nhận đơn không nhận món. Chỉ được ghi khi đơn Ready, đã QUÁ hạn nhận hiện hành (kể cả hạn đã gia hạn),
 * nhân viên xác nhận đã kiểm tra thực tế và khách không còn yêu cầu đến muộn đang chờ (BR23).
 * Ghi NotCollected KHÔNG tự cộng vi phạm (BR24): chỉ tạo sự việc PENDING_REVIEW để người có thẩm quyền kết luận.
 * Món đã làm không tự mở bán lại và suất không tự trả (BR15, xem {@link WasteHandlingService}); ưu đãi sang Chờ xem xét.
 */
@Service
@RequiredArgsConstructor
public class NoShowService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");

    private final OrderRepository orders;
    private final IncidentRepository incidents;
    private final LateArrivalRequestRepository lateArrivals;
    private final OrderService orderService;
    private final NotificationService notifications;
    private final VendorAccessChecker access;
    private final Clock clock;

    /** Đơn Ready đã quá hạn nhận, cần nhân viên kiểm tra thực tế (UC7.8 bước 1). Đơn quá hạn lâu nhất lên đầu. */
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listOverdue(Long vendorId, int page, int size) {
        access.requireMember(vendorId);
        return PageResponse.of(orders.findOverdueReady(vendorId, OrderStatus.READY, LocalDateTime.now(clock),
                PageParams.of(page, size)), OrderResponse::from);
    }

    @Transactional
    public NoShowResponse markNotCollected(Long orderId, NotCollectedRequest req) {
        Order order = orders.findDetailById(orderId).orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        access.requireMember(order.getVendor().getId());

        if (order.getStatus() != OrderStatus.READY) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_TRANSITION,
                    "Chỉ ghi nhận không nhận món cho đơn đang ở trạng thái Ready.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (order.getPickupDeadline() == null || now.isBefore(order.getPickupDeadline())) {
            throw new BusinessException(ErrorCode.NO_SHOW_NOT_OVERDUE);
        }
        if (lateArrivals.existsByOrderIdAndStatus(orderId, LateArrivalStatus.PENDING)) {
            throw new BusinessException(ErrorCode.LATE_ARRIVAL_PENDING,
                    "Khách đang có yêu cầu hỗ trợ đến muộn chờ xử lý, hãy xử lý yêu cầu đó trước (BR23).");
        }
        if (!Boolean.TRUE.equals(req.verifiedAtCounter())) {
            throw new BusinessException(ErrorCode.NO_SHOW_NOT_VERIFIED);
        }
        if (incidents.existsByOrderId(orderId)) {
            throw new BusinessException(ErrorCode.INCIDENT_ALREADY_EXISTS);
        }

        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        orderService.transition(order, OrderStatus.NOT_COLLECTED, Actor.of(CurrentUser.get()),
                note == null ? "Quá hạn nhận, nhân viên đã kiểm tra tại quầy" : "Không nhận món: " + note);

        Incident incident = new Incident();
        incident.setOrderId(order.getId());
        incident.setUserId(order.getCustomerId());
        incident.setVendorId(order.getVendor().getId());
        incident.setOccurredAt(order.getPickupDeadline());   // BR26: tính theo lúc hạn nhận trôi qua
        incident.setStatus(IncidentStatus.PENDING_REVIEW);
        incidents.save(incident);

        String code = order.getOrderCode();
        notifications.notifyUser(order.getCustomerId(), NotificationType.NO_SHOW_RECORDED,
                "Đơn " + code + " chưa được nhận",
                "Đơn " + code + " tại " + order.getVendor().getName() + " chưa được nhận trước hạn "
                        + order.getPickupDeadline().format(WHEN) + " nên đã được đóng. Đây chưa phải kết luận vi phạm: "
                        + "sự việc sẽ được xem xét và bạn có thể gửi phản hồi trong mục Sự việc của tôi.",
                "INCIDENT:" + incident.getId() + ":RECORDED", "INCIDENT", incident.getId(), false);
        notifications.notifyVendorMembers(order.getVendor().getId(), NotificationType.INCIDENT_PENDING_REVIEW,
                "Sự việc cần xem xét: đơn " + code,
                "Đơn " + code + " đã được ghi nhận không nhận món. Quản lý quầy cần kết luận Xác nhận hoặc Miễn, "
                        + "và nhân viên cần xử lý phần món đã làm (bán lại hoặc hao hụt).",
                "INCIDENT:" + incident.getId() + ":REVIEW", "INCIDENT", incident.getId());

        return new NoShowResponse(OrderResponse.from(order), incident.getId(), incident.getStatus());
    }
}
