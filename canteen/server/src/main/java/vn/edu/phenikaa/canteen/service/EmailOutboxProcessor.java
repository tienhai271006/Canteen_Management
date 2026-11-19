package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.phenikaa.canteen.common.config.JobProperties;
import vn.edu.phenikaa.canteen.common.config.NotificationProperties;
import vn.edu.phenikaa.canteen.model.EmailOutbox;
import vn.edu.phenikaa.canteen.model.EmailOutboxStatus;
import vn.edu.phenikaa.canteen.repository.EmailOutboxRepository;

/**
 * Gửi email trong hàng đợi và thử lại khi lỗi (NFR05, BR29). Lỗi email chỉ ghi vào {@code last_error}, không bao giờ
 * làm hỏng nghiệp vụ đã commit. Thử lại dùng đúng bản ghi cũ (cùng {@code event_key}) nên không gửi trùng.
 * <p>
 * Mỗi bước ghi chạy trong transaction RIÊNG (REQUIRES_NEW) vì {@link #trySend} còn được gọi từ {@code afterCommit}
 * của transaction nghiệp vụ, lúc đó transaction cũ đã xong và không dùng lại được. Việc gửi SMTP nằm ngoài mọi
 * transaction để không giữ kết nối DB trong lúc chờ mạng.
 */
@Service
public class EmailOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxProcessor.class);
    /** Chờ giữa các lần thử: 1, 5, 15 rồi 60 phút. */
    private static final int[] BACKOFF_MINUTES = {1, 5, 15, 60};
    static final String OTP_PREFIX = "OTP:";
    static final String REDACTED = "[Nội dung đã được xóa sau khi gửi.]";

    private final EmailOutboxRepository outbox;
    private final EmailSender sender;
    private final NotificationProperties props;
    private final JobProperties jobs;
    private final Clock clock;
    private final TransactionTemplate tx;

    public EmailOutboxProcessor(EmailOutboxRepository outbox, EmailSender sender, NotificationProperties props,
                                JobProperties jobs, Clock clock, PlatformTransactionManager txManager) {
        this.outbox = outbox;
        this.sender = sender;
        this.props = props;
        this.jobs = jobs;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${app.notification.poll-interval:PT15S}")
    public void scheduled() {
        if (jobs.enabled()) {
            processDue();
        }
    }

    /** Gửi các email đã đến hạn. Trả về số email gửi thành công. */
    public int processDue() {
        List<Long> due = outbox.findDueIds(EmailOutboxStatus.PENDING, LocalDateTime.now(clock),
                PageRequest.of(0, props.outboxBatchSize()));
        int sent = 0;
        for (Long id : due) {
            if (trySend(id)) {
                sent++;
            }
        }
        return sent;
    }

    /**
     * Thử gửi một email. Trả false nếu không nhận được (đã gửi, chưa đến hạn, hoặc nơi khác đang gửi) hoặc gửi lỗi.
     * Không bao giờ ném lỗi ra ngoài.
     */
    public boolean trySend(Long id) {
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            Integer claimed = tx.execute(s -> outbox.claim(id, now, now.plus(props.lease()), EmailOutboxStatus.PENDING));
            if (claimed == null || claimed == 0) {
                return false;
            }
            // Đọc trong transaction riêng để lấy bản mới nhất (attempts vừa được claim tăng), không dính cache của nơi gọi.
            EmailOutbox mail = tx.execute(s -> outbox.findById(id).orElse(null));
            if (mail == null) {
                return false;
            }
            try {
                sender.send(mail.getToEmail(), mail.getSubject(), mail.getBody());
            } catch (RuntimeException e) {
                recordFailure(mail, e);
                return false;
            }
            LocalDateTime done = LocalDateTime.now(clock);
            tx.executeWithoutResult(s -> {
                outbox.markSent(id, done, EmailOutboxStatus.PENDING, EmailOutboxStatus.SENT);
                if (mail.getEventKey().startsWith(OTP_PREFIX)) {
                    outbox.redactBody(id, REDACTED, done);
                }
            });
            return true;
        } catch (RuntimeException e) {
            log.warn("Không xử lý được email outbox {}: {}", id, e.getMessage());
            return false;
        }
    }

    private void recordFailure(EmailOutbox mail, RuntimeException e) {
        LocalDateTime now = LocalDateTime.now(clock);
        String error = truncate(e.getClass().getSimpleName() + ": " + e.getMessage(), 500);
        // attempts đã được claim() tăng, nên mail.getAttempts() là số lần đã thử kể cả lần vừa lỗi.
        int attempts = mail.getAttempts();
        log.warn("Gửi email {} lần {} thất bại: {}", mail.getId(), attempts, error);
        tx.executeWithoutResult(s -> {
            if (attempts >= props.maxAttempts()) {
                outbox.markFailed(mail.getId(), error, now, EmailOutboxStatus.PENDING, EmailOutboxStatus.FAILED);
            } else {
                int idx = Math.min(attempts - 1, BACKOFF_MINUTES.length - 1);
                outbox.scheduleRetry(mail.getId(), now.plus(Duration.ofMinutes(BACKOFF_MINUTES[idx])), error, now,
                        EmailOutboxStatus.PENDING);
            }
        });
    }

    private static String truncate(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }
}
