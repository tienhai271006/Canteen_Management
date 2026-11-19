package vn.edu.phenikaa.canteen.ocr;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import vn.edu.phenikaa.canteen.common.config.OcrProperties;

/**
 * Engine Tesseract qua Tess4J, chạy hoàn toàn trên ảnh trong bộ nhớ. Chỉ được tạo khi {@code app.ocr.enabled=true},
 * nên môi trường không cài Tesseract vẫn khởi động bình thường.
 * <p>
 * Tesseract không an toàn đa luồng nên mỗi lượt dùng một đối tượng riêng; số lượt chạy đồng thời đã được giới hạn
 * ở {@link vn.edu.phenikaa.canteen.ocr.OcrService}.
 */
@Component
@ConditionalOnProperty(prefix = "app.ocr", name = "enabled", havingValue = "true")
public class Tess4jOcrEngine implements OcrEngine {

    private static final int MIN_WIDTH = 1600;     // thẻ chụp nhỏ thì phóng lên để Tesseract đọc rõ hơn
    private static final int MAX_WIDTH = 3200;

    private final OcrProperties props;

    public Tess4jOcrEngine(OcrProperties props) {
        this.props = props;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String recognize(BufferedImage image) throws OcrEngineException {
        Tesseract t = new Tesseract();
        if (!props.tessdataPath().isBlank()) {
            t.setDatapath(props.tessdataPath());
        }
        t.setLanguage(props.language());
        t.setPageSegMode(3);                         // tự phân tích bố cục: thẻ có nhiều khối chữ
        try {
            return t.doOCR(prepare(image));
        } catch (TesseractException | UnsatisfiedLinkError e) {
            throw new OcrEngineException("Tesseract không chạy được: " + e.getClass().getSimpleName(), e);
        }
    }

    /** Chuyển xám và co giãn về chiều ngang hợp lý; không làm gì nặng hơn vì không có thư viện xử lý ảnh riêng. */
    static BufferedImage prepare(BufferedImage src) {
        int w = src.getWidth();
        int target = Math.max(MIN_WIDTH, Math.min(w, MAX_WIDTH));
        int h = (int) Math.round((double) src.getHeight() * target / w);
        BufferedImage out = new BufferedImage(target, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(src, 0, 0, target, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
