package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.config.IncidentProperties;
import vn.edu.phenikaa.canteen.common.dto.PageParams;
import vn.edu.phenikaa.canteen.common.dto.PageResponse;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.security.AuthenticatedUser;
import vn.edu.phenikaa.canteen.dto.RestrictionRequest;
import vn.edu.phenikaa.canteen.dto.RestrictionResponse;
import vn.edu.phenikaa.canteen.dto.ViolationSummaryResponse;
import vn.edu.phenikaa.canteen.model.IncidentStatus;
import vn.edu.phenikaa.canteen.model.NotificationType;
import vn.edu.phenikaa.canteen.model.Restriction;
import vn.edu.phenikaa.canteen.model.RestrictionStatus;
import vn.edu.phenikaa.canteen.model.Role;
import vn.edu.phenikaa.canteen.model.UserAccount;
import vn.edu.phenikaa.canteen.repository.IncidentRepository;
import vn.edu.phenikaa.canteen.repository.RestrictionRepository;
import vn.edu.phenikaa.canteen.repository.UserAccountRepository;

/**
 * UC10.5: quản trị viên quyết định hạn chế tạo đơn đặt trước (BR31). Chặn đặt đơn đã nằm ở OrderEligibilityChecker (Pha 6),
 * đăng nhập, xem lịch sử, phản hồi và xử lý đơn đã nhận không bị ảnh hưởng. Hết hạn thì khôi phục (RestrictionExpiryJob, BR32).
 * Chỉ quyết định khi đủ căn cứ: đủ số vi phạm đã xác nhận trong cửa sổ và không có sự việc đang tranh chấp (BR35).
 * Đạt ngưỡng không tự tạo hạn chế và không tự khóa tài khoản (BR27).
 */
@Service
@RequiredArgsConstructor
public class RestrictionService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");

    private final RestrictionRepository restrictions;
    private final IncidentRepository incidents;
    private final UserAccountRepository users;
    private final ViolationCounterService counter;
    private final NotificationService notifications;
    private final IncidentAssembler assembler;
    private final IncidentAccess access;
    private final IncidentProperties props;
    private final Clock clock;

    @Transactional
    public RestrictionResponse decide(RestrictionRequest req) {
        AuthenticatedUser admin = access.requireAdmin();
        UserAccount target = users.findById(req.userId())
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy tài khoản."));
        if (target.getRole() != Role.CUSTOMER) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chỉ hạn chế được tài khoản khách hàng.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        restrictions.expireStale(target.getId(), now, RestrictionStatus.ACTIVE, RestrictionStatus.EXPIRED);
        if (restrictions.existsByUserIdAndStatus(target.getId(), RestrictionStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.RESTRICTION_ALREADY_ACTIVE);
        }
        if (counter.hasDisputed(target.getId())) {
            throw new BusinessException(ErrorCode.INCIDENT_DISPUTED);
        }
        int count = counter.countValid(target.getId());
        if (count < props.escalateAt()) {
            throw new BusinessException(ErrorCode.RESTRICTION_NOT_JUSTIFIED,
                    "Khách mới có " + count + " vi phạm đã xác nhận trong " + props.windowDays()
                            + " ngày, cần từ " + props.escalateAt() + ".");
        }
        int days = req.days() == null ? props.restrictionDays() : req.days();
        if (days < 1 || days > props.maxRestrictionDays()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Thời hạn hạn chế phải từ 1 đến " + props.maxRestrictionDays() + " ngày.");
        }

        Restriction r = new Restriction();
        r.setUserId(target.getId());
        r.setStartsAt(now);
        r.setEndsAt(now.plusDays(days));
        r.setReason(req.reason().trim());
        r.setDecidedBy(admin.id());
        r.setStatus(RestrictionStatus.ACTIVE);
        restrictions.save(r);

        notifications.notifyUser(target.getId(), NotificationType.RESTRICTION_STARTED,
                "Hạn chế đặt trước từ " + r.getStartsAt().format(WHEN),
                "Từ " + r.getStartsAt().format(WHEN) + " đến " + r.getEndsAt().format(WHEN)
                        + " bạn không tạo được đơn đặt trước mới. Bạn vẫn đăng nhập, xem lịch sử và gửi phản hồi được; "
                        + "các đơn đã được chấp nhận vẫn được xử lý bình thường. Lý do: " + r.getReason() + ".",
                "RESTRICTION:" + r.getId() + ":STARTED", "RESTRICTION", r.getId(), true);
        return RestrictionResponse.from(r);
    }

    /** Thu hồi sớm, ví dụ sau khi sửa ghi nhận sai (UC10.6). Khách có thể đặt lại ngay. */
    @Transactional
    public RestrictionResponse revoke(Long restrictionId, String reason) {
        AuthenticatedUser admin = access.requireAdmin();
        Restriction r = restrictions.findById(restrictionId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy hạn chế."));
        if (r.getStatus() != RestrictionStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.RESTRICTION_STATE_INVALID);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        r.setStatus(RestrictionStatus.REVOKED);
        r.setRevokedBy(admin.id());
        r.setRevokedAt(now);
        notifications.notifyUser(r.getUserId(), NotificationType.RESTRICTION_REVOKED,
                "Hạn chế đặt trước đã được gỡ",
                "Hạn chế tạo đơn đặt trước của bạn đã được gỡ. Lý do: " + reason.trim() + ". Bạn có thể đặt món trở lại.",
                "RESTRICTION:" + r.getId() + ":REVOKED", "RESTRICTION", r.getId(), true);
        return RestrictionResponse.from(r);
    }

    @Transactional(readOnly = true)
    public PageResponse<RestrictionResponse> list(Long userId, Set<RestrictionStatus> statuses, int page, int size) {
        access.requireAdmin();
        var pageable = PageParams.of(page, size);
        var result = userId != null
                ? restrictions.findByUserIdOrderByIdDesc(userId, pageable)
                : restrictions.findByStatusInOrderByIdDesc(
                        statuses == null || statuses.isEmpty() ? EnumSet.of(RestrictionStatus.ACTIVE) : statuses, pageable);
        return PageResponse.of(result, RestrictionResponse::from);
    }

    /** Hồ sơ một khách để xét (UC10.5 bước 1): số đếm hợp lệ, mức hiện tại, tranh chấp, hạn chế đang có, sự việc gần đây. */
    @Transactional(readOnly = true)
    public ViolationSummaryResponse summary(Long userId) {
        access.requireAdmin();
        UserAccount u = users.findById(userId).orElseThrow(() -> BusinessException.notFound("Không tìm thấy tài khoản."));
        int count = counter.countValid(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        RestrictionResponse active = restrictions.findFirstByUserIdAndStatusOrderByIdDesc(userId, RestrictionStatus.ACTIVE)
                .filter(r -> r.getEndsAt().isAfter(now)).map(RestrictionResponse::from).orElse(null);
        var recent = incidents.findByUserIdAndStatusInOrderByIdDesc(userId, EnumSet.allOf(IncidentStatus.class),
                PageRequest.of(0, 10));
        return new ViolationSummaryResponse(userId, u.getFullName(), count, props.windowDays(),
                counter.levelFor(count).name(), counter.hasDisputed(userId), active,
                assembler.toResponses(recent.getContent()));
    }
}
