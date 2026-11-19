package vn.edu.phenikaa.canteen.ocr;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PreDestroy;
import vn.edu.phenikaa.canteen.common.config.OcrProperties;
import vn.edu.phenikaa.canteen.common.config.TimeConfig;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.dto.OcrConfirmRequest;
import vn.edu.phenikaa.canteen.dto.OcrDraftResponse;
import vn.edu.phenikaa.canteen.dto.ProfileResponse;
import vn.edu.phenikaa.canteen.dto.UpdateProfileRequest;
import vn.edu.phenikaa.canteen.ocr.OcrDraftStore.Draft;
import vn.edu.phenikaa.canteen.service.ProfileService;

/**
 * UC1.3 (quét thẻ) và UC1.4 (xác nhận). Luồng: ảnh vào bộ nhớ, nhận dạng, vứt ảnh, chỉ giữ chữ đã rút ra trong một bản
 * nháp có hạn. Hồ sơ chỉ đổi khi người dùng xác nhận (BR09), và việc lưu đi qua {@link ProfileService} nên dùng chung
 * quy tắc trùng mã sinh viên và đổi email (không tự coi email là đã xác minh).
 * <p>
 * Không ghi chữ OCR vào log: đó là dữ liệu cá nhân.
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final OcrEngine engine;
    private final OcrImageReader images;
    private final StudentCardParser parser;
    private final OcrDraftStore drafts;
    private final ProfileService profiles;
    private final OcrProperties props;

    private final Semaphore slots;
    private final ExecutorService pool;

    public OcrService(OcrEngine engine, OcrImageReader images, StudentCardParser parser, OcrDraftStore drafts,
                      ProfileService profiles, OcrProperties props) {
        this.engine = engine;
        this.images = images;
        this.parser = parser;
        this.drafts = drafts;
        this.profiles = profiles;
        this.props = props;
        this.slots = new Semaphore(Math.max(1, props.maxConcurrent()));
        this.pool = Executors.newFixedThreadPool(Math.max(1, props.maxConcurrent()), r -> {
            Thread t = new Thread(r, "ocr-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /** Công khai để giao diện biết có nên hiện nút "Quét thẻ" hay chỉ cho nhập tay. */
    public boolean available() {
        return engine.available();
    }

    // ------------------------------------------------------------------ UC1.3

    public OcrDraftResponse scan(long userId, MultipartFile file) {
        if (!engine.available()) {
            throw new BusinessException(ErrorCode.OCR_UNAVAILABLE, "Tính năng quét thẻ chưa được bật. Hãy nhập thông tin bằng tay.");
        }
        byte[] data = readBytes(file);
        if (!drafts.tryAcquireScan(userId)) {
            throw new BusinessException(ErrorCode.OCR_RATE_LIMITED);
        }

        BufferedImage image = decode(data);
        data = null;                                           // ảnh nén không còn cần: để GC thu hồi sớm
        String text = recognize(image);
        image = null;

        ParsedCard card = parser.parse(text);
        if (!card.usable()) {
            throw new BusinessException(ErrorCode.OCR_NO_TEXT);
        }
        Draft d = drafts.create(userId, card).orElseThrow(() -> new BusinessException(ErrorCode.OCR_BUSY));
        return view(d);
    }

    public OcrDraftResponse getDraft(long userId, String draftId) {
        return view(drafts.find(userId, draftId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OCR_DRAFT_NOT_FOUND)));
    }

    // ------------------------------------------------------------------ UC1.4

    /**
     * Lưu giá trị NGƯỜI DÙNG đã kiểm tra (có thể khác bản nháp), rồi xóa bản nháp. Lưu lỗi (ví dụ trùng mã sinh viên)
     * thì hồ sơ giữ nguyên và bản nháp còn để sửa lại. Email chỉ đổi khi người dùng gửi lên, theo đúng luồng xác minh.
     */
    public ProfileResponse confirm(long userId, String draftId, OcrConfirmRequest req) {
        drafts.find(userId, draftId).orElseThrow(() -> new BusinessException(ErrorCode.OCR_DRAFT_NOT_FOUND));

        ProfileResponse current = profiles.getProfile(userId);
        String email = req.email() == null || req.email().isBlank() ? null : req.email().trim();
        ProfileResponse saved = profiles.updateProfile(userId, new UpdateProfileRequest(
                req.fullName().trim(), req.studentCode().trim(), current.phone(), email));

        drafts.discard(userId, draftId);
        return saved;
    }

    /** Người dùng hủy: không ghi gì vào hồ sơ. Hủy bản nháp không tồn tại cũng coi như đã xong. */
    public void cancel(long userId, String draftId) {
        drafts.discard(userId, draftId);
    }

    /** Dọn bản nháp hết hạn (BR10). @return số bản nháp đã xóa. */
    public int purgeExpired() {
        return drafts.purgeExpired();
    }

    // ------------------------------------------------------------------ chi tiết

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "Chưa chọn ảnh thẻ.");
        }
        long max = props.maxImageSize().toBytes();
        if (file.getSize() > max) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        try (InputStream in = file.getInputStream()) {
            byte[] data = in.readNBytes((int) max + 1);
            if (data.length > max) {
                throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
            }
            return data;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
    }

    private BufferedImage decode(byte[] data) {
        try {
            return images.read(data);
        } catch (OcrImageReader.ImageRejected e) {
            if (e.problem() == OcrImageReader.Problem.TOO_MANY_PIXELS) {
                throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "Ảnh có độ phân giải quá lớn, hãy chụp lại ở mức thấp hơn.");
            }
            throw new BusinessException(ErrorCode.FILE_INVALID, "Chỉ nhận ảnh JPEG hoặc PNG đọc được.");
        }
    }

    /**
     * Chạy engine ở luồng riêng có hạn giờ. Suất đồng thời chỉ được trả khi tác vụ THỰC SỰ kết thúc (kể cả sau khi quá
     * giờ), nên một lần gọi native bị treo không làm vượt giới hạn đồng thời.
     */
    private String recognize(BufferedImage image) {
        if (!slots.tryAcquire()) {
            throw new BusinessException(ErrorCode.OCR_BUSY);
        }
        Future<String> future;
        try {
            future = pool.submit(() -> {
                try {
                    return engine.recognize(image);
                } finally {
                    slots.release();
                }
            });
        } catch (RuntimeException e) {                           // pool đã đóng hoặc từ chối
            slots.release();
            throw new BusinessException(ErrorCode.OCR_UNAVAILABLE);
        }
        try {
            String text = future.get(props.engineTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return text == null ? "" : text;
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("OCR quá {} nên bị hủy", props.engineTimeout());
            throw new BusinessException(ErrorCode.OCR_UNAVAILABLE);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.OCR_UNAVAILABLE);
        } catch (ExecutionException e) {
            log.warn("Engine OCR lỗi: {}", e.getCause() == null ? "?" : e.getCause().getClass().getSimpleName());
            throw new BusinessException(ErrorCode.OCR_UNAVAILABLE);
        }
    }

    private static OcrDraftResponse view(Draft d) {
        return new OcrDraftResponse(d.id(), d.card().fullName(), d.card().studentCode(), d.card().email(),
                LocalDateTime.ofInstant(d.expiresAt(), TimeConfig.ZONE));
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}
