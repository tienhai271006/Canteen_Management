package vn.edu.phenikaa.canteen.common.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("PT15M") Duration accessTtl,
        @DefaultValue("P7D") Duration refreshTtl) {
}
