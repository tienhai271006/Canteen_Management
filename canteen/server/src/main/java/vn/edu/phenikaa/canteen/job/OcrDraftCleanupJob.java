package vn.edu.phenikaa.canteen.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.config.JobProperties;
import vn.edu.phenikaa.canteen.ocr.OcrService;

/** Xóa bản nháp OCR hết hạn (BR10, NFR07). Bản nháp hết hạn vốn đã không truy cập được; job này giải phóng bộ nhớ. */
@Component
@RequiredArgsConstructor
public class OcrDraftCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OcrDraftCleanupJob.class);

    private final OcrService ocr;
    private final JobProperties props;

    @Scheduled(fixedDelayString = "${app.ocr.cleanup-interval:PT1M}")
    public void scheduled() {
        if (props.enabled()) {
            run();
        }
    }

    public int run() {
        int removed = ocr.purgeExpired();
        if (removed > 0) {
            log.debug("Đã xóa {} bản nháp OCR hết hạn", removed);
        }
        return removed;
    }
}
