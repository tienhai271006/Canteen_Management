package vn.edu.phenikaa.canteen.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình việc chạy nền (Pha 9). {@code enabled=false} tắt cả các lịch tự chạy (dùng trong test để các job không
 * chen vào kịch bản); các hàm {@code run()} vẫn gọi tay được.
 */
@ConfigurationProperties(prefix = "app.jobs")
public record JobProperties(
        @DefaultValue("true") boolean enabled,
        /** Số bản ghi tối đa mỗi lần chạy, để một lần chạy không giữ DB quá lâu. */
        @DefaultValue("100") int batchSize) {
}
