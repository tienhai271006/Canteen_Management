package vn.edu.phenikaa.canteen.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.security.CurrentUser;
import vn.edu.phenikaa.canteen.dto.OcrConfirmRequest;
import vn.edu.phenikaa.canteen.dto.OcrDraftResponse;
import vn.edu.phenikaa.canteen.dto.OcrStatusResponse;
import vn.edu.phenikaa.canteen.dto.ProfileResponse;
import vn.edu.phenikaa.canteen.ocr.OcrService;

/**
 * UC1.3 và UC1.4. Chỉ thao tác trên bản nháp của chính người đăng nhập (id lấy từ token, BR03).
 * Phản hồi chứa dữ liệu cá nhân nên cấm cache.
 */
@Tag(name = "Customer - Student card OCR")
@RestController
@RequestMapping("/api/me/student-card")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class StudentCardController {

    private final OcrService ocr;

    @GetMapping("/ocr-status")
    public OcrStatusResponse status() {
        return new OcrStatusResponse(ocr.available());
    }

    /** UC1.3: nhận ảnh, trả bản nháp. Chưa ghi gì vào hồ sơ. */
    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OcrDraftResponse> scan(@RequestPart("image") MultipartFile image) {
        return noStore(ocr.scan(CurrentUser.id(), image));
    }

    @GetMapping("/drafts/{draftId}")
    public ResponseEntity<OcrDraftResponse> draft(@PathVariable String draftId) {
        return noStore(ocr.getDraft(CurrentUser.id(), draftId));
    }

    /** UC1.4: lưu giá trị người dùng đã kiểm tra và xóa bản nháp. */
    @PostMapping("/drafts/{draftId}/confirm")
    public ResponseEntity<ProfileResponse> confirm(@PathVariable String draftId,
                                                   @Valid @RequestBody OcrConfirmRequest req) {
        return noStore(ocr.confirm(CurrentUser.id(), draftId, req));
    }

    /** Hủy: xóa bản nháp, không ghi hồ sơ. */
    @DeleteMapping("/drafts/{draftId}")
    public ResponseEntity<Void> cancel(@PathVariable String draftId) {
        ocr.cancel(CurrentUser.id(), draftId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
