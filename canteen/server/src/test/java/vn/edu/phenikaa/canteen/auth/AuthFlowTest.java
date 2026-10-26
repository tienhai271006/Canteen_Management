package vn.edu.phenikaa.canteen.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.security.VendorAccessChecker;

/** Cần Docker (Testcontainers MySQL). Không có Docker thì test tự bỏ qua. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class AuthFlowTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcClient jdbc;
    @Autowired VendorAccessChecker vendorAccess;
    @MockBean JavaMailSender mailSender;

    private JsonNode call(String url, String token, String json, int expectedStatus) throws Exception {
        var req = post(url).contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        String body = mvc.perform(req).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return body.isBlank() ? null : om.readTree(body);
    }

    @Test
    void registerVerifyOtpLoginAndProfile() throws Exception {
        String email = "sv." + UUID.randomUUID().toString().substring(0, 8) + "@phenikaa-uni.edu.vn";
        JsonNode reg = call("/api/auth/register", null, """
                {"fullName":"Nguyễn Văn A","email":"%s","password":"Matkhau123"}""".formatted(email), 201);
        String token = reg.get("accessToken").asText();

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(mail.getValue().getText());
        assertThat(m.find()).isTrue();
        String code = m.group(1);

        // Chưa xác minh
        mvc.perform(get("/api/me/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(false));

        // Mã sai bị từ chối
        String wrong = code.equals("000000") ? "111111" : "000000";
        JsonNode err = call("/api/auth/verify-email", token, "{\"code\":\"" + wrong + "\"}", 400);
        assertThat(err.get("code").asText()).isEqualTo("OTP_INVALID");

        // Mã đúng thì xác minh, dùng lại lần hai thì bị từ chối (dùng một lần)
        call("/api/auth/verify-email", token, "{\"code\":\"" + code + "\"}", 204);
        mvc.perform(get("/api/me/profile").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.emailVerified").value(true));
        call("/api/auth/verify-email", token, "{\"code\":\"" + code + "\"}", 400);

        // Đăng nhập lại, rồi làm mới và đăng xuất
        JsonNode login = call("/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"Matkhau123\"}", 200);
        String refresh = login.get("refreshToken").asText();
        JsonNode refreshed = call("/api/auth/refresh", null, "{\"refreshToken\":\"" + refresh + "\"}", 200);
        call("/api/auth/refresh", null, "{\"refreshToken\":\"" + refresh + "\"}", 401);   // token cũ đã bị xoay vòng
        call("/api/auth/logout", null, "{\"refreshToken\":\"" + refreshed.get("refreshToken").asText() + "\"}", 204);
    }

    @Test
    void wrongPasswordAndUnauthenticatedAndForbidden() throws Exception {
        JsonNode bad = call("/api/auth/login", null,
                "{\"email\":\"sv@phenikaa-uni.edu.vn\",\"password\":\"saimatkhau1\"}", 401);
        assertThat(bad.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");

        mvc.perform(get("/api/me/profile")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        JsonNode login = call("/api/auth/login", null,
                "{\"email\":\"sv@phenikaa-uni.edu.vn\",\"password\":\"Canteen@123\"}", 200);
        mvc.perform(get("/api/admin/anything").header("Authorization", "Bearer " + login.get("accessToken").asText()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void changePasswordRequiresCurrentPassword() throws Exception {
        JsonNode login = call("/api/auth/login", null,
                "{\"email\":\"guest@gmail.com\",\"password\":\"Canteen@123\"}", 200);
        String token = login.get("accessToken").asText();
        JsonNode err = call("/api/auth/change-password", token,
                "{\"currentPassword\":\"sai\",\"newPassword\":\"Moi12345\",\"confirmPassword\":\"Moi12345\"}", 400);
        assertThat(err.get("code").asText()).isEqualTo("CURRENT_PASSWORD_INCORRECT");
    }

    /** Tiêu chí "xong" của Pha 3: đổi mã quầy thì bị từ chối (NFR01, BR03). */
    @Test
    void vendorAccessIsLimitedToAssignedVendors() {
        Long staff = jdbc.sql("SELECT id FROM user_account WHERE email = 'staff@canteen.local'")
                .query(Long.class).single();
        Long own = jdbc.sql("SELECT id FROM vendor WHERE name = 'Cơm Nhà Cô Lan'").query(Long.class).single();
        Long other = jdbc.sql("SELECT id FROM vendor WHERE name = 'Bún Phở Ba Sao'").query(Long.class).single();

        vendorAccess.requireMember(staff, own);                                   // không ném lỗi
        assertThatThrownBy(() -> vendorAccess.requireMember(staff, other))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> vendorAccess.requireManager(staff, own))        // staff không phải manager
                .isInstanceOf(BusinessException.class);
    }
}
