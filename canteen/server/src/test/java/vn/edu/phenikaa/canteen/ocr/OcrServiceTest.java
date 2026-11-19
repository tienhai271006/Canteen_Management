package vn.edu.phenikaa.canteen.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import vn.edu.phenikaa.canteen.common.config.OcrProperties;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.dto.OcrConfirmRequest;
import vn.edu.phenikaa.canteen.dto.OcrDraftResponse;
import vn.edu.phenikaa.canteen.dto.ProfileResponse;
import vn.edu.phenikaa.canteen.dto.UpdateProfileRequest;
import vn.edu.phenikaa.canteen.model.Role;
import vn.edu.phenikaa.canteen.service.ProfileService;

class OcrServiceTest {

    private static final String CARD_TEXT = "Họ và tên: NGUYỄN VĂN AN\nMã SV: 21010123";

    private final OcrEngine engine = mock(OcrEngine.class);
    private final ProfileService profiles = mock(ProfileService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T01:00:00Z"), ZoneOffset.UTC);
    private OcrProperties props;
    private OcrDraftStore store;
    private OcrService service;
    private byte[] png;

    @BeforeEach
    void setUp() throws Exception {
        props = new OcrProperties(true, "", "vie+eng", Duration.ofMinutes(10), Duration.ofMinutes(1),
                DataSize.ofMegabytes(3), 12_000_000, 10, 2, Duration.ofMillis(300), 100, "\\d{8,10}");
        store = new OcrDraftStore(clock, props.draftTtl(), props.maxDrafts(), props.maxScansPerHour());
        service = new OcrService(engine, new OcrImageReader(props.maxPixels()), new StudentCardParser(props.studentCodePattern()),
                store, profiles, props);
        when(engine.available()).thenReturn(true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB), "png", out);
        png = out.toByteArray();
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("image", "the.png", "image/png", png);
    }

    private static void assertCode(Runnable r, ErrorCode code) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test
    void scanReturnsDraftAndDoesNotTouchProfile() throws Exception {
        when(engine.recognize(any())).thenReturn(CARD_TEXT);

        OcrDraftResponse r = service.scan(1, image());

        assertThat(r.fullName()).isEqualTo("Nguyễn Văn An");
        assertThat(r.studentCode()).isEqualTo("21010123");
        assertThat(service.getDraft(1, r.draftId()).studentCode()).isEqualTo("21010123");
        verify(profiles, never()).updateProfile(any(Long.class), any());
    }

    @Test
    void disabledEngineSaysUnavailableSoUserCanTypeByHand() {
        when(engine.available()).thenReturn(false);
        assertCode(() -> service.scan(1, image()), ErrorCode.OCR_UNAVAILABLE);
    }

    @Test
    void unreadableCardGivesNoDraft() throws Exception {
        when(engine.recognize(any())).thenReturn("@@ ## 12");
        assertCode(() -> service.scan(1, image()), ErrorCode.OCR_NO_TEXT);
        assertThat(store.size()).isZero();
    }

    @Test
    void engineFailureAndTimeoutBothMapToUnavailable() throws Exception {
        when(engine.recognize(any())).thenThrow(new OcrEngine.OcrEngineException("boom", null));
        assertCode(() -> service.scan(1, image()), ErrorCode.OCR_UNAVAILABLE);

        when(engine.recognize(any())).thenAnswer(inv -> {
            Thread.sleep(2000);
            return CARD_TEXT;
        });
        assertCode(() -> service.scan(1, image()), ErrorCode.OCR_UNAVAILABLE);
    }

    @Test
    void nonImageAndEmptyUploadsAreRejectedBeforeOcr() throws Exception {
        assertCode(() -> service.scan(1, new MockMultipartFile("image", "x.png", "image/png", "not an image at all".getBytes())),
                ErrorCode.FILE_INVALID);
        assertCode(() -> service.scan(1, new MockMultipartFile("image", "x.png", "image/png", new byte[0])),
                ErrorCode.FILE_INVALID);
        verify(engine, never()).recognize(any());
    }

    @Test
    void scansPerHourAreLimited() throws Exception {
        when(engine.recognize(any())).thenReturn(CARD_TEXT);
        for (int i = 0; i < props.maxScansPerHour(); i++) {
            service.scan(1, image());
        }
        assertCode(() -> service.scan(1, image()), ErrorCode.OCR_RATE_LIMITED);
    }

    @Test
    void confirmSavesWhatUserCheckedKeepsPhoneAndDeletesDraft() throws Exception {
        when(engine.recognize(any())).thenReturn(CARD_TEXT);
        String id = service.scan(1, image()).draftId();
        ProfileResponse current = new ProfileResponse(1L, "Cũ", "a@phenikaa-uni.edu.vn", null, true, null, "0912345678", Role.CUSTOMER);
        when(profiles.getProfile(1L)).thenReturn(current);
        when(profiles.updateProfile(eq(1L), any())).thenReturn(current);

        // Người dùng sửa lại họ tên so với bản nháp.
        service.confirm(1, id, new OcrConfirmRequest("Nguyễn Văn Anh", "21010123", null));

        ArgumentCaptor<UpdateProfileRequest> sent = ArgumentCaptor.forClass(UpdateProfileRequest.class);
        verify(profiles).updateProfile(eq(1L), sent.capture());
        assertThat(sent.getValue().fullName()).isEqualTo("Nguyễn Văn Anh");
        assertThat(sent.getValue().studentCode()).isEqualTo("21010123");
        assertThat(sent.getValue().phone()).isEqualTo("0912345678");   // không xóa số điện thoại
        assertThat(sent.getValue().email()).isNull();                  // email không tự đổi
        assertCode(() -> service.getDraft(1, id), ErrorCode.OCR_DRAFT_NOT_FOUND);
    }

    @Test
    void failedSaveKeepsProfileAndDraftSoUserCanFixAndRetry() throws Exception {
        when(engine.recognize(any())).thenReturn(CARD_TEXT);
        String id = service.scan(1, image()).draftId();
        when(profiles.getProfile(1L)).thenReturn(
                new ProfileResponse(1L, "Cũ", "a@phenikaa-uni.edu.vn", null, true, null, null, Role.CUSTOMER));
        when(profiles.updateProfile(eq(1L), any())).thenThrow(new BusinessException(ErrorCode.STUDENT_CODE_ALREADY_USED));

        assertCode(() -> service.confirm(1, id, new OcrConfirmRequest("An", "21010123", null)),
                ErrorCode.STUDENT_CODE_ALREADY_USED);
        assertThat(service.getDraft(1, id).draftId()).isEqualTo(id);
    }

    @Test
    void cannotConfirmOrReadSomeoneElsesDraft() throws Exception {
        when(engine.recognize(any())).thenReturn(CARD_TEXT);
        String id = service.scan(1, image()).draftId();

        assertCode(() -> service.confirm(2, id, new OcrConfirmRequest("An", "21010123", null)), ErrorCode.OCR_DRAFT_NOT_FOUND);
        assertCode(() -> service.getDraft(2, id), ErrorCode.OCR_DRAFT_NOT_FOUND);
        verify(profiles, never()).updateProfile(any(Long.class), any());
    }

    @Test
    void cancelDiscardsWithoutSaving() throws Exception {
        when(engine.recognize(any())).thenReturn(CARD_TEXT);
        String id = service.scan(1, image()).draftId();

        service.cancel(1, id);
        service.cancel(1, id);                                         // hủy lần hai vẫn êm

        assertCode(() -> service.getDraft(1, id), ErrorCode.OCR_DRAFT_NOT_FOUND);
        verify(profiles, never()).updateProfile(any(Long.class), any());
    }
}
