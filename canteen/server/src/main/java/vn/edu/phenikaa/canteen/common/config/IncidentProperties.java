package vn.edu.phenikaa.canteen.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tham số xử lý không nhận món và vi phạm (Bảng tham số trong tài liệu). Lần 1 nhắc, lần 2 cảnh báo, từ escalateAt chuyển xem xét. */
@ConfigurationProperties(prefix = "app.incident")
public record IncidentProperties(
        @DefaultValue("30") int windowDays,
        @DefaultValue("3") int escalateAt,
        @DefaultValue("7") int restrictionDays,
        @DefaultValue("30") int maxRestrictionDays) {
}
