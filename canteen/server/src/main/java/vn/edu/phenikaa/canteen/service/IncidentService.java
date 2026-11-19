package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.config.IncidentProperties;
import vn.edu.phenikaa.canteen.common.dto.PageParams;
import vn.edu.phenikaa.canteen.common.dto.PageResponse;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.security.CurrentUser;
import vn.edu.phenikaa.canteen.dto.IncidentDecision;
import vn.edu.phenikaa.canteen.dto.IncidentResponse;
import vn.edu.phenikaa.canteen.model.FeedbackStatus;
import vn.edu.phenikaa.canteen.model.Incident;
import vn.edu.phenikaa.canteen.model.IncidentStatus;
import vn.edu.phenikaa.canteen.model.NotificationType;
import vn.edu.phenikaa.canteen.model.RestrictionStatus;
import vn.edu.phenikaa.canteen.model.UserAccount;
import vn.edu.phenikaa.canteen.repository.FeedbackRepository;
import vn.edu.phenikaa.canteen.repository.IncidentRepository;
import vn.edu.phenikaa.canteen.repository.RestrictionRepository;
import vn.edu.phenikaa.canteen.repository.UserAccountRepository;

/**
 * UC10.3, UC10.4, UC10.6: kết luận sự việc và các thông báo kéo theo.
 * <ul>
 *   <li>Chỉ người có thẩm quyền (quản lý đúng quầy, hoặc quản trị viên) kết luận Xác nhận hoặc Miễn (BR24, BR25).</li>
 *   <li>Xác nhận mới làm số đếm 30 ngày tăng; mức nhắc, cảnh báo, chuyển xem xét suy ra từ số đếm (BR26, BR27) và
 *       mỗi sự kiện đúng một thông báo (khóa sự kiện duy nhất, BR29, BR36). Không bao giờ tự khóa tài khoản.</li>
 *   <li>Sự việc đang có phản hồi chờ xử lý thì không được xác nhận (quyết định bất lợi, BR35).</li>
 *   <li>Hủy kết luận chỉ quản trị viên làm; sau đó tính lại ngưỡng và báo xem lại hạn chế đang có (BR32).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class IncidentService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");
    private static final String FEEDBACK_HINT =
            " Nếu thấy ghi nhận chưa đúng, bạn có thể gửi phản hồi trong mục Sự việc của tôi.";

    private final IncidentRepository incidents;
    private final FeedbackRepository feedbacks;
    private final RestrictionRepository restrictions;
    private final UserAccountRepository users;
    private final ViolationCounterService counter;
    private final NotificationService notifications;
    private final ObjectProvider<FirstOrderDiscountProvider> promotion;
    private final IncidentAssembler assembler;
    private final IncidentAccess access;
    private final JdbcClient jdbc;
    private final IncidentProperties props;
    private final Clock clock;

    // ---- danh sách ----

    /** Quản lý xem sự việc của chính quầy mình; mặc định hàng đợi chờ xem xét. */
    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> listForVendor(Long vendorId, Set<IncidentStatus> statuses, int page, int size) {
        access.requireReviewer(vendorId);
        Set<IncidentStatus> filter = statuses == null || statuses.isEmpty()
                ? EnumSet.of(IncidentStatus.PENDING_REVIEW) : statuses;
        return toPage(incidents.findByVendorIdAndStatusInOrderByIdDesc(vendorId, filter, PageParams.of(page, size)));
    }

    /** Quản trị viên: tất cả sự việc, hoặc của một khách (khi xét hồ sơ, mặc định mọi trạng thái). */
    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> listForAdmin(Long userId, Set<IncidentStatus> statuses, int page, int size) {
        access.requireAdmin();
        boolean empty = statuses == null || statuses.isEmpty();
        if (userId != null) {
            return toPage(incidents.findByUserIdAndStatusInOrderByIdDesc(userId,
                    empty ? EnumSet.allOf(IncidentStatus.class) : statuses, PageParams.of(page, size)));
        }
        return toPage(incidents.findByStatusInOrderByIdDesc(
                empty ? EnumSet.of(IncidentStatus.PENDING_REVIEW) : statuses, PageParams.of(page, size)));
    }

    /** NFR07: khách chỉ thấy sự việc của chính mình. */
    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> listMine(Long userId, int page, int size) {
        return toPage(incidents.findByUserIdAndStatusInOrderByIdDesc(userId, EnumSet.allOf(IncidentStatus.class),
                PageParams.of(page, size)));
    }

    // ---- kết luận ----

    @Transactional
    public IncidentResponse decide(Long incidentId, IncidentDecision decision, String note) {
        Incident incident = load(incidentId);
        access.requireReviewer(incident.getVendorId());
        if (decision == IncidentDecision.CONFIRM) {
            confirm(incident, note);
        } else {
            exempt(incident, note);
        }
        return assembler.toResponse(incident);
    }

    /** BR25: miễn cho lỗi quầy, lỗi hệ thống, ngoại lệ. Người gọi (hoặc FeedbackService) phải là người có thẩm quyền. */
    @Transactional
    public void exempt(Incident incident, String note) {
        access.requireReviewer(incident.getVendorId());
        requireStatus(incident, IncidentStatus.PENDING_REVIEW);
        conclude(incident, IncidentStatus.EXEMPTED, note);
        promotion.ifAvailable(p -> p.resolveReview(incident.getOrderId(), true));
        notifications.notifyUser(incident.getUserId(), NotificationType.INCIDENT_EXEMPTED,
                "Sự việc đơn " + orderCode(incident.getOrderId()) + " được miễn",
                "Sự việc không được tính là vi phạm. Lý do: " + note + ".",
                "INCIDENT:" + incident.getId() + ":EXEMPTED", "INCIDENT", incident.getId(), false);
    }

    @Transactional
    public IncidentResponse revoke(Long incidentId, String reason) {
        Incident incident = load(incidentId);
        revoke(incident, reason);
        return assembler.toResponse(incident);
    }

    /** UC10.6, BR32: hủy kết luận xác nhận sai. Giữ lịch sử, loại khỏi số đếm, xem lại hạn chế phát sinh. */
    @Transactional
    public void revoke(Incident incident, String reason) {
        access.requireAdmin();
        requireStatus(incident, IncidentStatus.CONFIRMED);
        String before = incident.getDecisionNote();
        conclude(incident, IncidentStatus.REVOKED,
                "[Hủy kết luận] " + reason + (before == null ? "" : " | Trước đó: " + before));
        promotion.ifAvailable(p -> p.reinstate(incident.getOrderId()));

        String code = orderCode(incident.getOrderId());
        notifications.notifyUser(incident.getUserId(), NotificationType.INCIDENT_REVOKED,
                "Đã hủy kết luận vi phạm đơn " + code,
                "Kết luận vi phạm cho đơn " + code + " đã được hủy và không còn được tính. Lý do: " + reason + ".",
                "INCIDENT:" + incident.getId() + ":REVOKED", "INCIDENT", incident.getId(), true);

        int remaining = counter.countValid(incident.getUserId());
        if (remaining < props.escalateAt()
                && restrictions.existsByUserIdAndStatus(incident.getUserId(), RestrictionStatus.ACTIVE)) {
            notifications.notifyAdmins(NotificationType.RESTRICTION_REVIEW_NEEDED,
                    "Cần xem lại hạn chế đặt trước",
                    "Sự việc đơn " + code + " vừa bị hủy kết luận, khách còn " + remaining
                            + " vi phạm hợp lệ trong " + props.windowDays() + " ngày nhưng đang có hạn chế. "
                            + "Hãy xem lại hạn chế này.",
                    "INCIDENT:" + incident.getId() + ":REVIEWRESTRICTION", "INCIDENT", incident.getId());
        }
    }

    // ---- nội bộ ----

    private void confirm(Incident incident, String note) {
        requireStatus(incident, IncidentStatus.PENDING_REVIEW);
        if (feedbacks.existsByIncidentIdAndStatus(incident.getId(), FeedbackStatus.OPEN)) {
            throw new BusinessException(ErrorCode.INCIDENT_DISPUTED);   // BR35
        }
        conclude(incident, IncidentStatus.CONFIRMED, note);
        promotion.ifAvailable(p -> p.resolveReview(incident.getOrderId(), false));

        int count = counter.countValid(incident.getUserId());   // đã gồm sự việc vừa xác nhận
        notifyConfirmed(incident, count, counter.levelFor(count), note);
    }

    private void notifyConfirmed(Incident incident, int count, ViolationCounterService.Level level, String note) {
        String code = orderCode(incident.getOrderId());
        UserAccount user = users.findById(incident.getUserId()).orElseThrow();
        String base = "Đơn " + code + " đã quá hạn nhận lúc " + incident.getOccurredAt().format(WHEN)
                + " mà chưa được nhận. Lý do ghi nhận: " + note + ". Đây là vi phạm thứ " + count + " trong "
                + props.windowDays() + " ngày gần nhất.";
        String verify = user.isEmailVerified() ? ""
                : " Hãy xác minh email trường để nhận thông báo qua email.";   // BR28
        String key = "INCIDENT:" + incident.getId() + ":CONFIRMED";

        switch (level) {
            case REMINDER -> notifications.notifyUser(user.getId(), NotificationType.INCIDENT_REMINDER,
                    "Nhắc nhở: đơn " + code + " không được nhận", base + FEEDBACK_HINT + verify,
                    key, "INCIDENT", incident.getId(), true);
            case WARNING -> notifications.notifyUser(user.getId(), NotificationType.INCIDENT_WARNING,
                    "Cảnh báo: đơn " + code + " không được nhận",
                    base + " Nếu tiếp tục, hồ sơ sẽ được chuyển cho quản trị viên xem xét." + FEEDBACK_HINT + verify,
                    key, "INCIDENT", incident.getId(), true);
            case ESCALATED -> {
                notifications.notifyUser(user.getId(), NotificationType.INCIDENT_ESCALATED,
                        "Hồ sơ của bạn được chuyển xem xét",
                        base + " Hồ sơ đã được chuyển cho quản trị viên xem xét; tài khoản của bạn chưa bị khóa."
                                + FEEDBACK_HINT + verify,
                        key, "INCIDENT", incident.getId(), true);
                notifications.notifyAdmins(NotificationType.INCIDENT_ESCALATED,
                        "Cần xem xét hạn chế: " + user.getFullName(),
                        "Khách " + user.getFullName() + " đạt " + count + " vi phạm đã xác nhận trong "
                                + props.windowDays() + " ngày (sự việc đơn " + code + ").",
                        "INCIDENT:" + incident.getId() + ":ESCALATED", "INCIDENT", incident.getId());
            }
            default -> {
            }
        }
    }

    private void conclude(Incident incident, IncidentStatus status, String note) {
        incident.setStatus(status);
        incident.setDecidedBy(CurrentUser.id());
        incident.setDecidedAt(LocalDateTime.now(clock));
        incident.setDecisionNote(note == null ? null : note.length() <= 255 ? note : note.substring(0, 255));
    }

    private void requireStatus(Incident incident, IncidentStatus expected) {
        if (incident.getStatus() != expected) {
            throw new BusinessException(ErrorCode.INCIDENT_STATE_INVALID,
                    "Sự việc đang ở trạng thái " + incident.getStatus() + ", cần " + expected + ".");
        }
    }

    private Incident load(Long id) {
        return incidents.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.INCIDENT_NOT_FOUND));
    }

    private String orderCode(Long orderId) {
        return jdbc.sql("SELECT order_code FROM orders WHERE id = :id").param("id", orderId)
                .query(String.class).single();
    }

    private PageResponse<IncidentResponse> toPage(Page<Incident> p) {
        return new PageResponse<>(assembler.toResponses(p.getContent()), p.getNumber(), p.getSize(),
                p.getTotalElements(), p.getTotalPages(), p.isFirst(), p.isLast());
    }
}
