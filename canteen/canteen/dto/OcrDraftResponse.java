package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDateTime;

/**
 * Kết quả quét tạm thời (UC1.3). Trường null nghĩa là không đọc được, người dùng tự điền.
 * Mọi giá trị chỉ là gợi ý: chưa có gì được lưu vào hồ sơ cho tới khi xác nhận (BR09).
 */
public record OcrDraftResponse(
        String draftId,
        String fullName,
        String studentCode,
        String email,
        LocalDateTime expiresAt) {
}
