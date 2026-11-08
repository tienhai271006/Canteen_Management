package vn.edu.phenikaa.canteen.ocr;

import java.awt.image.BufferedImage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Engine mặc định khi {@code app.ocr.enabled=false}: OCR là tùy chọn (BR09), tắt thì người dùng nhập tay. */
@Component
@ConditionalOnProperty(prefix = "app.ocr", name = "enabled", havingValue = "false", matchIfMissing = true)
public class UnavailableOcrEngine implements OcrEngine {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String recognize(BufferedImage image) throws OcrEngineException {
        throw new OcrEngineException("OCR chưa được bật", null);
    }
}
