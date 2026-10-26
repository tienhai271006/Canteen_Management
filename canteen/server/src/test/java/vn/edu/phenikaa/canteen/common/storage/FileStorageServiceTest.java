package vn.edu.phenikaa.canteen.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;

/** Không cần Spring hay Docker. */
class FileStorageServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @TempDir Path dir;
    FileStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new FileStorageService(new StorageProperties(dir.toString(), DataSize.ofKilobytes(1)));
    }

    @Test
    void storesImageUnderServerGeneratedNameIgnoringClientName() throws Exception {
        String name = storage.storeImage(new MockMultipartFile("file", "../../evil.jsp", "text/html", PNG));
        assertThat(name).matches("[0-9a-f-]{36}\\.png");
        assertThat(Files.exists(dir.resolve(name))).isTrue();
        assertThat(storage.load(name).exists()).isTrue();
        storage.deleteQuietly(name);
        assertThat(Files.exists(dir.resolve(name))).isFalse();
    }

    @Test
    void rejectsNonImageEvenWithImageContentType() {
        byte[] html = "<script>alert(1)</script>".getBytes();
        assertCode(() -> storage.storeImage(new MockMultipartFile("file", "a.png", "image/png", html)),
                ErrorCode.FILE_INVALID);
    }

    @Test
    void rejectsEmptyAndOversizedFiles() {
        assertCode(() -> storage.storeImage(new MockMultipartFile("file", "a.png", "image/png", new byte[0])),
                ErrorCode.FILE_INVALID);
        byte[] big = new byte[2048];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertCode(() -> storage.storeImage(new MockMultipartFile("file", "a.png", "image/png", big)),
                ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void loadRejectsPathTraversalAndUnknownNames() {
        assertCode(() -> storage.load("../secret.txt"), ErrorCode.NOT_FOUND);
        assertCode(() -> storage.load("00000000-0000-0000-0000-000000000000.png"), ErrorCode.NOT_FOUND);
    }

    private static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
