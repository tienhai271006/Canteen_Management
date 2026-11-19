package vn.edu.phenikaa.canteen.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** UC1.3, UC1.4, BR09, BR10 qua HTTP. Engine OCR được giả lập, nên không cần cài Tesseract. Cần Docker cho MySQL. */
@SpringBootTest(properties = "app.jobs.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class StudentCardFlowTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcClient jdbc;
    @MockBean JavaMailSender mailSender;
    @MockBean OcrEngine engine;

    @BeforeEach
    void engineReads() throws Exception {
        when(engine.available()).thenReturn(true);
        when(engine.recognize(any())).thenReturn("Họ và tên: NGUYỄN VĂN AN\nMã SV: " + uniqueCode());
    }

    private static String uniqueCode() {
        return String.valueOf(10_000_000 + (int) (Math.abs(UUID.randomUUID().getMostSignificantBits()) % 80_000_000));
    }

    private String register() throws Exception {
        String email = "sv." + UUID.randomUUID().toString().substring(0, 8) + "@phenikaa-uni.edu.vn";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Khách thử","email":"%s","password":"Matkhau123"}""".formatted(email)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("accessToken").asText();
    }

    private static MockMultipartFile card() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile("image", "the.png", "image/png", out.toByteArray());
    }

    private JsonNode scan(String token) throws Exception {
        String body = mvc.perform(multipart("/api/me/student-card/scan").file(card()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body);
    }

    private String confirmBody(String code) {
        return """
                {"fullName":"Nguyễn Văn An","studentCode":"%s"}""".formatted(code);
    }

    @Test
    void scanThenConfirmSavesProfileAndDeletesDraft() throws Exception {
        String token = register();
        JsonNode draft = scan(token);
        String id = draft.get("draftId").asText();
        String code = draft.get("studentCode").asText();

        // Chưa xác nhận thì hồ sơ chưa có gì (BR09).
        mvc.perform(get("/api/me/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentCode").doesNotExist());

        mvc.perform(post("/api/me/student-card/drafts/{id}/confirm", id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentCode").value(code))
                .andExpect(jsonPath("$.emailVerified").value(false));       // OCR không làm email thành đã xác minh

        mvc.perform(get("/api/me/student-card/drafts/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OCR_DRAFT_NOT_FOUND"));
    }

    @Test
    void otherUserCannotSeeOrConfirmMyDraft() throws Exception {
        String mine = register();
        String other = register();
        String id = scan(mine).get("draftId").asText();

        mvc.perform(get("/api/me/student-card/drafts/{id}", id).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/me/student-card/drafts/{id}/confirm", id).header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody("21010123")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/me/student-card/drafts/{id}", id).header("Authorization", "Bearer " + mine))
                .andExpect(status().isOk());
    }

    @Test
    void cancelLeavesProfileUntouched() throws Exception {
        String token = register();
        String id = scan(token).get("draftId").asText();

        mvc.perform(delete("/api/me/student-card/drafts/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/me/profile").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.studentCode").doesNotExist());
        mvc.perform(post("/api/me/student-card/drafts/{id}/confirm", id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody("21010123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateStudentCodeKeepsOldProfileAndDraft() throws Exception {
        String first = register();
        String second = register();
        String code1 = scan(first).get("studentCode").asText();
        String id1 = scan(first).get("draftId").asText();
        mvc.perform(post("/api/me/student-card/drafts/{id}/confirm", id1).header("Authorization", "Bearer " + first)
                .contentType(MediaType.APPLICATION_JSON).content(confirmBody(code1))).andExpect(status().isOk());

        String id2 = scan(second).get("draftId").asText();
        mvc.perform(post("/api/me/student-card/drafts/{id}/confirm", id2).header("Authorization", "Bearer " + second)
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(code1)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STUDENT_CODE_ALREADY_USED"));

        mvc.perform(get("/api/me/profile").header("Authorization", "Bearer " + second))
                .andExpect(jsonPath("$.studentCode").doesNotExist());
        mvc.perform(get("/api/me/student-card/drafts/{id}", id2).header("Authorization", "Bearer " + second))
                .andExpect(status().isOk());
    }

    @Test
    void fakeImageIsRejectedAndConfirmValidatesInput() throws Exception {
        String token = register();
        mvc.perform(multipart("/api/me/student-card/scan")
                        .file(new MockMultipartFile("image", "the.png", "image/png", "<svg/>".getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_INVALID"));

        String id = scan(token).get("draftId").asText();
        mvc.perform(post("/api/me/student-card/drafts/{id}/confirm", id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"fullName":"","studentCode":"!!"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresLoginAndReportsAvailability() throws Exception {
        mvc.perform(get("/api/me/student-card/ocr-status")).andExpect(status().isUnauthorized());
        String token = register();
        mvc.perform(get("/api/me/student-card/ocr-status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM user_account").query(Long.class).single()).isPositive();
    }
}
