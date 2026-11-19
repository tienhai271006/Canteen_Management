package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.dto.PageParams;
import vn.edu.phenikaa.canteen.common.dto.PageResponse;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.dto.NotificationResponse;
import vn.edu.phenikaa.canteen.model.Notification;
import vn.edu.phenikaa.canteen.model.NotificationType;
import vn.edu.phenikaa.canteen.model.UserAccount;
import vn.edu.phenikaa.canteen.repository.NotificationRepository;
import vn.edu.phenikaa.canteen.repository.UserAccountRepository;

/**
 * Thông báo trong ứng dụng và hàng đợi email (UC10.x, NFR05, BR28 đến BR30, BR36).
 * <p>
 * Thông báo và dòng outbox được ghi trong CÙNG transaction với nghiệp vụ gây ra nó (hoặc cùng cả hai hoặc không có gì).
 * Cả hai bảng đều UNIQUE theo {@code event_key}, ghi bằng INSERT ... ON DUPLICATE KEY nên chạy lặp cùng một sự kiện
 * (job chạy hai lần, thử lại) không tạo thông báo hay email thứ hai, và cũng không làm hỏng transaction đang chạy.
 * Email chỉ gửi tới email trường ĐÃ xác minh; chưa xác minh thì chỉ có thông báo trong ứng dụng (BR28).
 * Gửi thật xảy ra sau commit, lỗi gửi chỉ để lại dòng outbox chờ thử lại và không ảnh hưởng nghiệp vụ.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JdbcClient jdbc;
    private final NotificationRepository notifications;
    private final UserAccountRepository users;
    private final SchoolEmailPolicy emailPolicy;
    private final EmailOutboxProcessor outboxProcessor;
    private final Clock clock;

    // ---- tạo thông báo ----

    /** Thông báo cho một người; {@code alsoEmail} thì thêm email nếu người đó có email trường đã xác minh. */
    @Transactional
    public void notifyUser(Long userId, NotificationType type, String title, String body, String eventKey,
                           String refType, Long refId, boolean alsoEmail) {
        jdbc.sql("""
                INSERT INTO notification (user_id, type, title, body, event_key, ref_type, ref_id)
                VALUES (:user, :type, :title, :body, :key, :refType, :refId)
                ON DUPLICATE KEY UPDATE id = id""")
                .param("user", userId)
                .param("type", type.name())
                .param("title", cut(title, 160))
                .param("body", cut(body, 1000))
                .param("key", eventKey)
                .param("refType", refType)
                .param("refId", refId)
                .update();
        if (!alsoEmail) {
            return;
        }
        UserAccount u = users.findById(userId).orElse(null);
        if (u == null || !u.isEmailVerified() || !emailPolicy.isSchoolEmail(u.getEmail())) {
            return;   // BR28: chưa xác minh thì chỉ thông báo trong ứng dụng
        }
        Long notificationId = jdbc.sql("SELECT id FROM notification WHERE event_key = :key")
                .param("key", eventKey).query(Long.class).optional().orElse(null);
        queueEmail(userId, notificationId, u.getEmail(), "[Canteen] " + title, body, eventKey);
    }

    /** Thông báo cho mọi nhân sự đang hoạt động của quầy (không gửi email). */
    @Transactional
    public void notifyVendorMembers(Long vendorId, NotificationType type, String title, String body,
                                    String eventKeyPrefix, String refType, Long refId) {
        List<Long> memberIds = jdbc.sql("SELECT user_id FROM vendor_member WHERE vendor_id = :v AND active = TRUE")
                .param("v", vendorId).query(Long.class).list();
        for (Long memberId : memberIds) {
            notifyUser(memberId, type, title, body, eventKeyPrefix + ":U" + memberId, refType, refId, false);
        }
    }

    /** Thông báo cho mọi quản trị viên đang hoạt động (không gửi email). Khóa sự kiện kèm id từng người nhận. */
    @Transactional
    public void notifyAdmins(NotificationType type, String title, String body, String eventKeyPrefix,
                             String refType, Long refId) {
        List<Long> adminIds = jdbc.sql("SELECT id FROM user_account WHERE role = 'ADMIN' AND status = 'ACTIVE'")
                .query(Long.class).list();
        for (Long adminId : adminIds) {
            notifyUser(adminId, type, title, body, eventKeyPrefix + ":A" + adminId, refType, refId, false);
        }
    }

    /**
     * Xếp một email vào hàng đợi rồi gửi ngay sau khi transaction hiện tại commit; lỗi thì để lịch chạy nền thử lại.
     * Dùng trực tiếp cho email không đi kèm thông báo (mã OTP, UC1.2). {@code eventKey} bắt đầu bằng "OTP:" thì nội
     * dung bị xóa khỏi DB sau khi gửi.
     */
    @Transactional
    public void queueEmail(Long userId, Long notificationId, String to, String subject, String body, String eventKey) {
        jdbc.sql("""
                INSERT INTO email_outbox (notification_id, user_id, to_email, subject, body, event_key, status,
                                          attempts, next_attempt_at)
                VALUES (:notification, :user, :to, :subject, :body, :key, 'PENDING', 0, :now)
                ON DUPLICATE KEY UPDATE id = id""")
                .param("notification", notificationId)
                .param("user", userId)
                .param("to", to)
                .param("subject", cut(subject, 255))
                .param("body", body)
                .param("key", eventKey)
                .param("now", LocalDateTime.now(clock))
                .update();
        Long outboxId = jdbc.sql("SELECT id FROM email_outbox WHERE event_key = :key")
                .param("key", eventKey).query(Long.class).single();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    outboxProcessor.trySend(outboxId);
                }
            });
        } else {
            outboxProcessor.trySend(outboxId);
        }
    }

    // ---- đọc và đánh dấu đã đọc (người dùng chỉ thấy thông báo của mình, BR03) ----

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, boolean unreadOnly, int page, int size) {
        var pageable = PageParams.of(page, size);
        Page<Notification> result = unreadOnly
                ? notifications.findByUserIdAndReadAtIsNullOrderByIdDesc(userId, pageable)
                : notifications.findByUserIdOrderByIdDesc(userId, pageable);
        return PageResponse.of(result, NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    /** Đánh dấu đã đọc một thông báo. Đã đọc rồi thì không đổi gì; không có hoặc không phải của mình thì 404. */
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        if (notifications.markRead(notificationId, userId, LocalDateTime.now(clock)) > 0) {
            return;
        }
        boolean mine = notifications.findById(notificationId).map(n -> n.getUserId().equals(userId)).orElse(false);
        if (!mine) {
            throw BusinessException.notFound("Không tìm thấy thông báo.");
        }
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notifications.markAllRead(userId, LocalDateTime.now(clock));
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }
}
