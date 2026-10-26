package vn.edu.phenikaa.canteen.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;

/**
 * Lưu ảnh món trên đĩa. Ba nguyên tắc an toàn:
 * <ol>
 *   <li>Loại ảnh xác định bằng byte đầu của tệp, không tin Content-Type hay đuôi tệp do client gửi.
 *       Chỉ nhận JPEG, PNG, WebP (không nhận SVG vì có thể chứa script).</li>
 *   <li>Tên tệp do server sinh (UUID + đuôi), không dùng tên client nên không có path traversal.</li>
 *   <li>Đọc tệp chỉ qua tên đúng định dạng đó, rồi kiểm tra lại đường dẫn nằm trong thư mục gốc.</li>
 * </ol>
 */
@Service
public class FileStorageService {

    public static final String PUBLIC_URL_PREFIX = "/api/public/images/";

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final Pattern STORED_NAME =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$");

    private final Path root;
    private final long maxBytes;

    public FileStorageService(StorageProperties props) {
        this.root = Paths.get(props.dir()).toAbsolutePath().normalize();
        this.maxBytes = props.maxFileSize().toBytes();
    }

    /** Lưu ảnh và trả tên tệp đã lưu (đây là giá trị đặt vào {@code dish.image_path}). */
    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        byte[] data;
        try (InputStream in = file.getInputStream()) {
            data = in.readNBytes((int) maxBytes + 1);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        if (data.length > maxBytes) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String ext = detectExtension(data);
        if (ext == null) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }

        String name = UUID.randomUUID() + "." + ext;
        try {
            Files.createDirectories(root);
            Files.write(root.resolve(name), data, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            log.error("Không ghi được ảnh vào {}", root, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return name;
    }

    /** Xóa không ném lỗi: ảnh mồ côi chỉ tốn chỗ, không được làm hỏng thao tác nghiệp vụ. */
    public void deleteQuietly(String storedName) {
        Path path = resolve(storedName);
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Không xóa được ảnh {}", storedName, e);
        }
    }

    /** Trả ảnh để phục vụ công khai; tên không hợp lệ hoặc không có tệp đều là 404. */
    public Resource load(String storedName) {
        Path path = resolve(storedName);
        if (path == null || !Files.isRegularFile(path)) {
            throw BusinessException.notFound("Không tìm thấy ảnh.");
        }
        return new PathResource(path);
    }

    public MediaType mediaType(String storedName) {
        if (storedName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (storedName.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }

    public String publicUrl(String storedName) {
        return storedName == null ? null : PUBLIC_URL_PREFIX + storedName;
    }

    /** Trả về null nếu tên không đúng định dạng do server sinh hoặc thoát khỏi thư mục gốc. */
    private Path resolve(String storedName) {
        if (storedName == null || !STORED_NAME.matcher(storedName).matches()) {
            return null;
        }
        Path path = root.resolve(storedName).normalize();
        return path.startsWith(root) ? path : null;
    }

    static String detectExtension(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "png";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
