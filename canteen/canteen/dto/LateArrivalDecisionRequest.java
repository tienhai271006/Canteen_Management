package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.edu.phenikaa.canteen.model.HandleChannel;

/** Quầy chấp thuận gia hạn và chốt hạn nhận mới, thay hạn cũ (UC7.7, BR22). */
public record LateArrivalDecisionRequest(
        @NotNull LocalDateTime newDeadline,
        @NotBlank @Size(max = 255) String reason,
        HandleChannel channel) {

    public HandleChannel channelOrDefault() {
        return channel == null ? HandleChannel.APP : channel;
    }
}
