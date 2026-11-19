package vn.edu.phenikaa.canteen.common.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Cấu hình hàng đợi email (NFR05, BR29). */
@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(
        /** Quá số lần thử này thì email chuyển FAILED, thông báo trong ứng dụng vẫn còn. */
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("20") int outboxBatchSize,
        /** Thời gian "giữ chỗ" khi một tiến trình nhận gửi một email, tránh hai nơi cùng gửi. */
        @DefaultValue("PT2M") Duration lease) {
}
