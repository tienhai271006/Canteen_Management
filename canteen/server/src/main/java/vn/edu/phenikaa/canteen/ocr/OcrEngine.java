package vn.edu.phenikaa.canteen.ocr;

import java.awt.image.BufferedImage;

/** Bộ nhận dạng chữ. Tách thành interface để thay engine (Tesseract, API thị giác...) mà không đụng nghiệp vụ. */
public interface OcrEngine {

    /** Lỗi của engine (thiếu dữ liệu ngôn ngữ, thư viện native, ảnh không đọc nổi...). */
    class OcrEngineException extends Exception {
        public OcrEngineException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** @return true nếu engine thật sự chạy được. Engine "tắt" trả false để API báo OCR chưa khả dụng. */
    boolean available();

    /** Trả chữ thô đọc được. Không được ghi ảnh ra đĩa hay giữ lại tham chiếu tới ảnh sau khi trả về. */
    String recognize(BufferedImage image) throws OcrEngineException;
}
