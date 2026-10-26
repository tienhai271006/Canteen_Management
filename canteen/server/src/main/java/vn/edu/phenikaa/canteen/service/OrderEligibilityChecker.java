package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;

/**
 * Khách đang bị hạn chế tạo đơn thì không được đặt (BR31, BR32): vẫn đăng nhập và xem được, chỉ không đặt trước được.
 * Đọc bảng restriction (V6) bằng JDBC để Pha 6 không phụ thuộc entity của Pha 10.
 */
@Component
@RequiredArgsConstructor
public class OrderEligibilityChecker {

    private final JdbcClient jdbc;
    private final Clock clock;

    public void requireCanOrder(Long customerId) {
        Long active = jdbc.sql("SELECT COUNT(*) FROM restriction "
                        + "WHERE user_id = :u AND status = 'ACTIVE' AND starts_at <= :now AND ends_at > :now")
                .param("u", customerId)
                .param("now", LocalDateTime.now(clock))
                .query(Long.class)
                .single();
        if (active != null && active > 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_RESTRICTED);
        }
    }
}
