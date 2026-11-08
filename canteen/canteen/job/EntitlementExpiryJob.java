package vn.edu.phenikaa.canteen.job;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.phenikaa.canteen.common.config.JobProperties;
import vn.edu.phenikaa.canteen.model.EntitlementStatus;
import vn.edu.phenikaa.canteen.repository.PromotionEntitlementRepository;

/**
 * Quyền ưu đãi khả dụng đã quá hạn thì chuyển Hết hạn (BR07). Chỉ là việc dọn trạng thái: việc áp ưu đãi vẫn tự kiểm
 * tra hạn lúc đặt đơn, nên job chậm hay tắt không làm ai dùng được quyền quá hạn. Một câu UPDATE theo trạng thái cũ
 * nên chạy lặp không đổi gì thêm.
 */
@Component
public class EntitlementExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(EntitlementExpiryJob.class);

    private final PromotionEntitlementRepository entitlements;
    private final JobProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;

    public EntitlementExpiryJob(PromotionEntitlementRepository entitlements, JobProperties props, Clock clock,
                                TransactionTemplate tx) {
        this.entitlements = entitlements;
        this.props = props;
        this.clock = clock;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.jobs.entitlement-expiry-interval:PT10M}")
    public void scheduled() {
        if (props.enabled()) {
            run();
        }
    }

    /** Trả về số quyền vừa chuyển Hết hạn. */
    public int run() {
        Integer n = tx.execute(s -> entitlements.expireDue(LocalDateTime.now(clock), EntitlementStatus.AVAILABLE,
                EntitlementStatus.EXPIRED));
        int expired = n == null ? 0 : n;
        if (expired > 0) {
            log.info("Đã chuyển {} quyền ưu đãi quá hạn sang Hết hạn.", expired);
        }
        return expired;
    }
}
