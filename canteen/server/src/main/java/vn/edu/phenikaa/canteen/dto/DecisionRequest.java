package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import vn.edu.phenikaa.canteen.model.HandleChannel;

/**
 * Quyết định duyệt hoặc từ chối của quầy (UC7.6, UC7.7). Lý do bắt buộc cả khi duyệt lẫn khi từ chối (BR19).
 * {@code channel} bỏ trống nghĩa là APP; thỏa thuận qua điện thoại phải gửi PHONE để ghi vào đơn.
 */
public record DecisionRequest(
        @NotBlank @Size(max = 255) String reason,
        HandleChannel channel) {

    public HandleChannel channelOrDefault() {
        return channel == null ? HandleChannel.APP : channel;
    }
}
