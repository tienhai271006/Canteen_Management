package vn.edu.phenikaa.canteen.ocr;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Giải mã ảnh upload một cách phòng thủ, hoàn toàn trong bộ nhớ (BR10: không ghi ra đĩa).
 * Loại ảnh nhận diện bằng byte đầu chứ không tin Content-Type; chỉ nhận JPEG và PNG (ImageIO đọc được sẵn);
 * kích thước điểm ảnh được đọc từ phần đầu tệp và kiểm tra TRƯỚC khi giải mã để chặn ảnh nén cực lớn.
 */
public final class OcrImageReader {

    public enum Problem { NOT_AN_IMAGE, UNSUPPORTED_TYPE, TOO_MANY_PIXELS, CORRUPT }

    /** Ảnh không đọc được, kèm lý do để tầng trên chọn thông báo. */
    public static final class ImageRejected extends Exception {
        private final transient Problem problem;

        public ImageRejected(Problem problem) {
            super(problem.name());
            this.problem = problem;
        }

        public Problem problem() {
            return problem;
        }
    }

    private final long maxPixels;

    public OcrImageReader(long maxPixels) {
        this.maxPixels = maxPixels;
    }

    public BufferedImage read(byte[] data) throws ImageRejected {
        if (data == null || data.length < 12) {
            throw new ImageRejected(Problem.NOT_AN_IMAGE);
        }
        if (!isJpeg(data) && !isPng(data)) {
            throw new ImageRejected(isOtherKnownImage(data) ? Problem.UNSUPPORTED_TYPE : Problem.NOT_AN_IMAGE);
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new ImageRejected(Problem.CORRUPT);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > maxPixels) {
                    throw new ImageRejected(pixels <= 0 ? Problem.CORRUPT : Problem.TOO_MANY_PIXELS);
                }
                BufferedImage img = reader.read(0);
                if (img == null) {
                    throw new ImageRejected(Problem.CORRUPT);
                }
                return img;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            throw new ImageRejected(Problem.CORRUPT);
        }
    }

    static boolean isJpeg(byte[] d) {
        return (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF;
    }

    static boolean isPng(byte[] d) {
        return d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A;
    }

    private static boolean isOtherKnownImage(byte[] d) {
        boolean webp = d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E';
        boolean gif = d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8';
        boolean bmp = d[0] == 'B' && d[1] == 'M';
        return webp || gif || bmp;
    }
}
