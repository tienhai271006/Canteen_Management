package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;
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
import vn.edu.phenikaa.canteen.dto.FeedbackAction;
import vn.edu.phenikaa.canteen.dto.FeedbackResponse;
import vn.edu.phenikaa.canteen.model.Feedback;
import vn.edu.phenikaa.canteen.model.FeedbackStatus;
import vn.edu.phenikaa.canteen.model.Incident;
import vn.edu.phenikaa.canteen.model.IncidentStatus;
import vn.edu.phenikaa.canteen.model.NotificationType;
import vn.edu.phenikaa.canteen.repository.FeedbackRepository;
import vn.edu.phenikaa.canteen.repository.IncidentRepository;

/**
 * UC10.6, BR35. Phản hồi đang mở KHÔNG xóa sự việc (số đếm giữ nguyên) nhưng sự việc bị coi là tranh chấp:
 * không xác nhận được sự việc đó và không quyết định hạn chế dựa trên nó cho tới khi phản hồi được giải quyết.
 * Mỗi sự việc chỉ một phản hồi đang mở (UNIQUE trong DB).
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbacks;
    private final IncidentRepository incidents;
    private final IncidentService incidentService;
    private final NotificationService notifications;
    private final IncidentAccess access;
    private final Clock clock;

    @Transactional
    public FeedbackResponse submit(Long incidentId, String content) {
        Long userId = CurrentUser.id();
        // Sự việc của người khác trả 404 như không tồn tại, không lộ việc nó có thật (NFR07).
        Incident incident = incidents.findById(incidentId).filter(i -> i.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.INCIDENT_NOT_FOUND));
        if (incident.getStatus() != IncidentStatus.PENDING_REVIEW && incident.getStatus() != IncidentStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INCIDENT_STATE_INVALID,
                    "Sự việc đã được miễn hoặc hủy kết luận nên không cần phản hồi.");
        }
        if (feedbacks.existsByIncidentIdAndStatus(incidentId, FeedbackStatus.OPEN)) {
            throw new BusinessException(ErrorCode.FEEDBACK_ALREADY_OPEN);
        }
        Feedback f = new Feedback();
        f.setIncidentId(incidentId);
        f.setUserId(userId);
        f.setContent(content.trim());
        feedbacks.save(f);

        String title = "Khách phản hồi về sự việc #" + incidentId;
        String body = "Khách gửi phản hồi về một sự việc không nhận món. Sự việc được coi là tranh chấp cho tới khi phản hồi "
                + "được giải quyết.";
        String key = "FEEDBACK:" + f.getId() + ":RECEIVED";
        notifications.notifyVendorMembers(incident.getVendorId(), NotificationType.FEEDBACK_RECEIVED, title, body,
                key, "FEEDBACK", f.getId());
        notifications.notifyAdmins(NotificationType.FEEDBACK_RECEIVED, title, body, key, "FEEDBACK", f.getId());
        return FeedbackResponse.from(f);
    }

    @Transactional(readOnly = true)
    public PageResponse<FeedbackResponse> listForVendor(Long vendorId, Set<FeedbackStatus> statuses, int page, int size) {
        access.requireReviewer(vendorId);
        return PageResponse.of(feedbacks.findByVendor(vendorId, openIfEmpty(statuses), PageParams.of(page, size)),
                FeedbackResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<FeedbackResponse> listForAdmin(Set<FeedbackStatus> statuses, int page, int size) {
        access.requireAdmin();
        return PageResponse.of(feedbacks.findByStatusInOrderByIdDesc(openIfEmpty(statuses), PageParams.of(page, size)),
                FeedbackResponse::from);
    }

    /**
     * Giải quyết phản hồi. {@code EXEMPT}: sự việc đang chờ thì miễn; sự việc đã xác nhận thì hủy kết luận (chỉ quản
     * trị viên, IncidentService kiểm tra). {@code KEEP}: giữ nguyên sự việc, vẫn phải kết luận riêng.
     */
    @Transactional
    public FeedbackResponse resolve(Long feedbackId, String response, FeedbackAction action) {
        Feedback f = feedbacks.findById(feedbackId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy phản hồi."));
        Incident incident = incidents.findById(f.getIncidentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INCIDENT_NOT_FOUND));
        access.requireReviewer(incident.getVendorId());
        if (f.getStatus() != FeedbackStatus.OPEN) {
            throw new BusinessException(ErrorCode.REQUEST_ALREADY_HANDLED);
        }
        if (action == FeedbackAction.EXEMPT) {
            switch (incident.getStatus()) {
                case PENDING_REVIEW -> incidentService.exempt(incident, response);
                case CONFIRMED -> incidentService.revoke(incident, response);
                default -> throw new BusinessException(ErrorCode.INCIDENT_STATE_INVALID);
            }
        }
        f.setStatus(FeedbackStatus.RESOLVED);
        f.setResponse(response.trim());
        f.setHandledBy(CurrentUser.id());
        f.setHandledAt(LocalDateTime.now(clock));

        notifications.notifyUser(f.getUserId(), NotificationType.FEEDBACK_RESOLVED,
                "Phản hồi của bạn đã được xử lý",
                "Phản hồi về sự việc #" + incident.getId() + " đã được xử lý. Trả lời: " + response.trim(),
                "FEEDBACK:" + f.getId() + ":RESOLVED", "FEEDBACK", f.getId(), true);
        return FeedbackResponse.from(f);
    }

    private static Set<FeedbackStatus> openIfEmpty(Set<FeedbackStatus> s) {
        return s == null || s.isEmpty() ? EnumSet.of(FeedbackStatus.OPEN) : s;
    }
}
