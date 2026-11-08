package vn.edu.phenikaa.canteen.ocr;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.edu.phenikaa.canteen.common.config.OcrProperties;

/** Dựng các thành phần thuần Java của OCR từ cấu hình. */
@Configuration
public class OcrConfig {

    @Bean
    OcrDraftStore ocrDraftStore(Clock clock, OcrProperties p) {
        return new OcrDraftStore(clock, p.draftTtl(), p.maxDrafts(), p.maxScansPerHour());
    }

    @Bean
    StudentCardParser studentCardParser(OcrProperties p) {
        return new StudentCardParser(p.studentCodePattern());
    }

    @Bean
    OcrImageReader ocrImageReader(OcrProperties p) {
        return new OcrImageReader(p.maxPixels());
    }
}
