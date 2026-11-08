package vn.edu.phenikaa.canteen.common.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * OCR thẻ sinh viên (UC1.3, UC1.4, BR09, BR10). Mặc định TẮT: khi tắt, API quét trả "OCR chưa khả dụng" và người dùng
 * nhập tay như bình thường. Bật cần cài Tesseract và dữ liệu ngôn ngữ trên máy chạy server (xem README).
 */
@ConfigurationProperties(prefix = "app.ocr")
public record OcrProperties(
        @DefaultValue("false") boolean enabled,
        /** Thư mục chứa *.traineddata; để trống thì dùng biến môi trường TESSDATA_PREFIX hoặc đường dẫn hệ thống. */
        @DefaultValue("") String tessdataPath,
        @DefaultValue("vie+eng") String language,
        /** Bản nháp (chỉ gồm chữ đã trích xuất, KHÔNG có ảnh) sống tối đa chừng này (BR10). */
        @DefaultValue("PT10M") Duration draftTtl,
        @DefaultValue("PT1M") Duration cleanupInterval,
        @DefaultValue("3MB") DataSize maxImageSize,
        /** Chặn ảnh có số điểm ảnh quá lớn trước khi giải mã, tránh cạn bộ nhớ. */
        @DefaultValue("12000000") long maxPixels,
        @DefaultValue("10") int maxScansPerHour,
        /** Số lượt OCR chạy đồng thời tối đa; vượt thì trả "hệ thống bận" thay vì xếp hàng vô hạn. */
        @DefaultValue("2") int maxConcurrent,
        @DefaultValue("PT20S") Duration engineTimeout,
        @DefaultValue("5000") int maxDrafts,
        /** Mẫu mã sinh viên trên thẻ (regex, không kèm ranh giới). Chỉnh theo thẻ thật của trường. */
        @DefaultValue("\\d{8,10}") String studentCodePattern) {
}
