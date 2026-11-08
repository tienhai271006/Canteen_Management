package vn.edu.phenikaa.canteen.common.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @DefaultValue("phenikaa-uni.edu.vn") List<String> schoolEmailDomains,
        @DefaultValue("PT10M") Duration otpTtl,
        @DefaultValue("5") int otpMaxAttempts,
        @DefaultValue("PT1M") Duration otpResendCooldown,
        @DefaultValue("5") int otpMaxSendsPerHour,
        @DefaultValue("dev-only-otp-pepper-change-me") String otpPepper) {
}
