package vn.edu.phenikaa.canteen.job;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.phenikaa.canteen.common.config.JobProperties;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.model.Order;
import vn.edu.phenikaa.canteen.model.OrderStatus;
import vn.edu.phenikaa.canteen.repository.OrderRepository;
import vn.edu.phenikaa.canteen.service.Actor;
import vn.edu.phenikaa.canteen.service.OrderService;

/**
 * BR13: Pending quá hạn xác nhận (mặc định 5 phút) chuyển Expired và trả suất, ưu đãi (qua {@link OrderService#transition}).
 * <p>
 * An toàn khi chạy lặp và khi tranh với quầy bấm xác nhận (TC07, BR36): mỗi đơn xử lý trong transaction riêng, nạp lại
 * và chỉ đổi khi VẪN còn Pending và vẫn quá hạn; nếu quầy xác nhận ngay lúc đó, khóa lạc quan làm một bên thua và
 * job bỏ qua đơn này. Một đơn lỗi không chặn các đơn khác.
 */
@Component
public class PendingExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(PendingExpiryJob.class);

    private final OrderRepository orders;
    private final OrderService orderService;
    private final JobProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;

    public PendingExpiryJob(OrderRepository orders, OrderService orderService, JobProperties props, Clock clock,
                            PlatformTransactionManager txManager) {
        this.orders = orders;
        this.orderService = orderService;
        this.props = props;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${app.jobs.pending-expiry-interval:PT30S}")
    public void scheduled() {
        if (props.enabled()) {
            run();
        }
    }

    /** Trả về số đơn đã chuyển Expired trong lần chạy này. */
    public int run() {
        List<Long> ids = orders.findExpiredPendingIds(OrderStatus.PENDING, LocalDateTime.now(clock),
                PageRequest.of(0, props.batchSize()));
        int expired = 0;
        for (Long id : ids) {
            try {
                if (Boolean.TRUE.equals(tx.execute(s -> expireOne(id)))) {
                    expired++;
                }
            } catch (OptimisticLockingFailureException | BusinessException e) {
                log.debug("Bỏ qua đơn {} vì vừa có thao tác khác: {}", id, e.getMessage());
            } catch (RuntimeException e) {
                log.warn("Không hết hạn được đơn {}: {}", id, e.getMessage());
            }
        }
        if (expired > 0) {
            log.info("Đã chuyển {} đơn Pending quá hạn sang Expired.", expired);
        }
        return expired;
    }

    private boolean expireOne(Long id) {
        Order order = orders.findDetailById(id).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (order == null || order.getStatus() != OrderStatus.PENDING || order.getPendingExpiresAt() == null
                || now.isBefore(order.getPendingExpiresAt())) {
            return false;
        }
        orderService.transition(order, OrderStatus.EXPIRED, Actor.system(), "Quá hạn xác nhận");
        return true;
    }
}
