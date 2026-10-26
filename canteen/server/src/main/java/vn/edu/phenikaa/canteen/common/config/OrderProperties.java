package vn.edu.phenikaa.canteen.common.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.order")
public record OrderProperties(
        /** Hạn quầy phải xác nhận đơn Pending, quá hạn thì chuyển Expired (BR13). */
        @DefaultValue("PT5M") Duration pendingTtl,
        @DefaultValue("20") int maxQuantityPerItem) {
}
