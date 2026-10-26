package vn.edu.phenikaa.canteen.common.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * NFR08: toàn hệ thống dùng múi giờ Việt Nam.
 * Mọi nơi cần giờ đều tiêm {@link Clock} để test được hạn 5 phút (BR13) và cửa sổ 30 ngày (BR26).
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class TimeConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }

    /** createdAt/updatedAt của BaseEntity lấy giờ từ Clock, nên test có thể thay Clock cố định. */
    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
