package vn.edu.phenikaa.canteen.job;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.phenikaa.canteen.common.config.JobProperties;
import vn.edu.phenikaa.canteen.model.NotificationType;
import vn.edu.phenikaa.canteen.service.NotificationService;

/**
 * BR32: hết hạn hạn chế đặt trước thì khôi phục quyền, trừ khi có quyết định khác. Việc chặn đặt đơn đã tự kiểm tra
 * {@code ends_at > now} (OrderEligibilityChecker) nên job chỉ dọn trạng thái ACTIVE sang EXPIRED và báo cho người dùng.
 * Mỗi hạn chế chỉ đổi khi VẪN còn ACTIVE, thông báo theo event_key nên chạy lặp không báo hai lần.
 */
@Component
public class RestrictionExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(RestrictionExpiryJob.class);

    private record Due(Long id, Long userId) {
    }

    private final JdbcClient jdbc;
    private final NotificationService notifications;
    private final JobProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;

    public RestrictionExpiryJob(JdbcClient jdbc, NotificationService notifications, JobProperties props, Clock clock,
                                TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.props = props;
        this.clock = clock;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.jobs.restriction-expiry-interval:PT5M}")
    public void scheduled() {
        if (props.enabled()) {
            run();
        }
    }

    /** Trả về số hạn chế vừa chuyển EXPIRED. */
    public int run() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Due> due = jdbc.sql("""
                        SELECT id, user_id FROM restriction
                         WHERE status = 'ACTIVE' AND ends_at <= :now
                         ORDER BY ends_at, id LIMIT :limit""")
                .param("now", now).param("limit", props.batchSize())
                .query((rs, n) -> new Due(rs.getLong("id"), rs.getLong("user_id"))).list();
        int expired = 0;
        for (Due d : due) {
            try {
                if (Boolean.TRUE.equals(tx.execute(s -> expireOne(d, now)))) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.warn("Không kết thúc được hạn chế {}: {}", d.id(), e.getMessage());
            }
        }
        if (expired > 0) {
            log.info("Đã kết thúc {} hạn chế đặt trước hết hạn.", expired);
        }
        return expired;
    }

    private boolean expireOne(Due d, LocalDateTime now) {
        int updated = jdbc.sql("UPDATE restriction SET status = 'EXPIRED', updated_at = :now, version = version + 1 "
                        + "WHERE id = :id AND status = 'ACTIVE' AND ends_at <= :now")
                .param("now", now).param("id", d.id()).update();
        if (updated == 0) {
            return false;   // đã bị thu hồi hoặc xử lý ở nơi khác
        }
        notifications.notifyUser(d.userId(), NotificationType.RESTRICTION_ENDED,
                "Hạn chế đặt trước đã kết thúc",
                "Hạn chế tạo đơn đặt trước của bạn đã hết hạn. Bạn có thể đặt món trở lại.",
                "RESTRICTION:" + d.id() + ":ENDED", "RESTRICTION", d.id(), true);
        return true;
    }
}
