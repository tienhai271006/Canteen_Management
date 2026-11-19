package vn.edu.phenikaa.canteen.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * UC7.8 bước 2. {@code verifiedAtCounter} phải là true: nhân viên đã kiểm tra thực tế (khách có đang xếp hàng không,
 * quầy có giao được không, thông báo Ready có phát được không) trước khi ghi nhận (BR23).
 */
public record NotCollectedRequest(@NotNull Boolean verifiedAtCounter, @Size(max = 255) String note) {
}
