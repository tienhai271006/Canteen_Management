package vn.edu.phenikaa.canteen.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class OcrImageReaderTest {

    private final OcrImageReader reader = new OcrImageReader(1_000_000);

    private static byte[] encode(int w, int h, int type, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, type), format, out);
        return out.toByteArray();
    }

    @Test
    void readsPngAndJpeg() throws Exception {
        assertThat(reader.read(encode(200, 100, BufferedImage.TYPE_INT_RGB, "png")).getWidth()).isEqualTo(200);
        assertThat(reader.read(encode(200, 100, BufferedImage.TYPE_INT_RGB, "jpg")).getHeight()).isEqualTo(100);
    }

    @Test
    void rejectsOtherFormatsAndNonImagesByContentNotByName() throws Exception {
        assertThat(problem(encode(50, 50, BufferedImage.TYPE_INT_RGB, "gif")))
                .isEqualTo(OcrImageReader.Problem.UNSUPPORTED_TYPE);
        assertThat(problem("<svg onload=alert(1)></svg>".getBytes()))
                .isEqualTo(OcrImageReader.Problem.NOT_AN_IMAGE);
    }

    @Test
    void rejectsTruncatedFile() throws Exception {
        byte[] png = encode(200, 100, BufferedImage.TYPE_INT_RGB, "png");
        assertThat(problem(Arrays.copyOf(png, 40))).isEqualTo(OcrImageReader.Problem.CORRUPT);
    }

    @Test
    void rejectsHugeResolutionBeforeDecoding() throws Exception {
        byte[] big = encode(2000, 1000, BufferedImage.TYPE_BYTE_GRAY, "png");   // 2 triệu điểm ảnh > 1 triệu
        assertThat(problem(big)).isEqualTo(OcrImageReader.Problem.TOO_MANY_PIXELS);
    }

    private OcrImageReader.Problem problem(byte[] data) {
        return catchThrowableOfType(() -> reader.read(data)).problem();
    }

    private static OcrImageReader.ImageRejected catchThrowableOfType(ThrowingRead r) {
        try {
            r.run();
        } catch (OcrImageReader.ImageRejected e) {
            return e;
        }
        throw new AssertionError("Đáng ra ảnh phải bị từ chối");
    }

    private interface ThrowingRead {
        void run() throws OcrImageReader.ImageRejected;
    }

    @Test
    void tooShortInputIsNotAnImage() {
        assertThatThrownBy(() -> reader.read(new byte[3])).isInstanceOf(OcrImageReader.ImageRejected.class);
    }
}
