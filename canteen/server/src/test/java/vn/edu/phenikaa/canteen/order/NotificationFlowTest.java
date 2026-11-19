package vn.edu.phenikaa.canteen.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.phenikaa.canteen.order.OrderTestFixtures.createMenu;
import static vn.edu.phenikaa.canteen.order.OrderTestFixtures.withJson;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.edu.phenikaa.canteen.common.config.TimeConfig;
import vn.edu.phenikaa.canteen.job.EntitlementExpiryJob;
import vn.edu.phenikaa.canteen.job.PendingExpiryJob;
import vn.edu.phenikaa.canteen.job.RestrictionExpiryJob;
import vn.edu.phenikaa.canteen.order.OrderTestFixtures.MenuFixture;
import vn.edu.phenikaa.canteen.repository.UserAccountRepository;
import vn.edu.phenikaa.canteen.service.EmailOutboxProcessor;
import vn.edu.phenikaa.canteen.service.PromotionService;

/**
 * Pha 9: thông báo trong ứng dụng, email outbox (gửi lại không trùng) và các job nền (hết hạn Pending, quyền ưu đãi,
 * hạn chế). Job và processor được gọi tay; lịch tự chạy bị tắt bằng app.jobs.enabled=false.
 * Cần Docker (Testcontainers MySQL); không có Docker thì test tự bỏ qua.
 */
@SpringBootTest(properties = "app.jobs.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class NotificationFlowTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    private static final String LUNCH_VENDOR = "Cơm Nhà Cô Lan";
    private static final String DEV_PASSWORD = "Canteen@123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcClient jdbc;
    @Autowired UserAccountRepository users;
    @Autowired PromotionService promotions;
    @Autowired PendingExpiryJob pendingExpiryJob;
    @Autowired EntitlementExpiryJob entitlementExpiryJob;
    @Autowired RestrictionExpiryJob restrictionExpiryJob;
    @Autowired EmailOutboxProcessor outboxProcessor;
    @MockBean JavaMailSender mailSender;

    record Customer(String token, long userId, String email) {
    }

    @BeforeEach
    void resetMail() {
        reset(mailSender);
    }

    // ---- helpers ----

    private Customer register(boolean verified) throws Exception {
        String email = "sv." + UUID.randomUUID().toString().substring(0, 8) + "@phenikaa-uni.edu.vn";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Khách thử\",\"email\":\"" + email + "\",\"password\":\"Matkhau123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = jdbc.sql("SELECT id FROM user_account WHERE email = :e").param("e", email).query(Long.class).single();
        if (verified) {
            jdbc.sql("UPDATE user_account SET email_verified = TRUE, email_verified_at = NOW(6) WHERE id = :i")
                    .param("i", id).update();
        }
        return new Customer(om.readTree(body).get("accessToken").asText(), id, email);
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DEV_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("accessToken").asText();
    }

    private JsonNode call(MockHttpServletRequestBuilder req, int expected) throws Exception {
        String body = mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return body.isBlank() ? om.createObjectNode() : om.readTree(body);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", "Bearer " + token);
    }

    private long placeOrder(Customer c, MenuFixture fx, int qty) throws Exception {
        call(withJson(post("/api/cart/items"), c.token(),
                "{\"menuItemId\":" + fx.menuItemId() + ",\"quantity\":" + qty + "}"), 200);
        return call(withJson(post("/api/orders"), c.token(), "{\"pickupSlotId\":" + fx.slotId() + "}")
                .header("Idempotency-Key", UUID.randomUUID().toString()), 201).get("id").asLong();
    }

    private JsonNode counter(String staff, long orderId, String action, String json, int expected) throws Exception {
        String url = "/api/counter/orders/" + orderId + "/" + action;
        return call(json == null ? auth(post(url), staff) : withJson(post(url), staff, json), expected);
    }

    private JsonNode notificationsOf(String token) throws Exception {
        return call(auth(get("/api/notifications"), token), 200).get("content");
    }

    private int countNotifications(long userId, String type) {
        return jdbc.sql("SELECT COUNT(*) FROM notification WHERE user_id = :u AND type = :t")
                .param("u", userId).param("t", type).query(Integer.class).single();
    }

    private int reserved(long menuItemId) {
        return jdbc.sql("SELECT reserved_slots FROM quota WHERE menu_item_id = :i").param("i", menuItemId)
                .query(Integer.class).single();
    }

    private String orderStatus(long id) {
        return jdbc.sql("SELECT status FROM orders WHERE id = :i").param("i", id).query(String.class).single();
    }

    private void makeOverdue(long orderId) {
        jdbc.sql("UPDATE orders SET pending_expires_at = :t WHERE id = :o")
                .param("t", LocalDateTime.now(TimeConfig.ZONE).minusMinutes(1)).param("o", orderId).update();
    }

    private String outboxStatus(String eventKey) {
        return jdbc.sql("SELECT status FROM email_outbox WHERE event_key = :k").param("k", eventKey)
                .query(String.class).single();
    }

    // ---- thông báo trong ứng dụng ----

    @Test
    void customerIsNotifiedWhenCounterConfirmsAndReadyAndCanMarkRead() throws Exception {
        Customer c = register(false);
        String staff = login("staff@canteen.local");
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        long id = placeOrder(c, fx, 1);

        counter(staff, id, "confirm", null, 200);
        counter(staff, id, "prepare", null, 200);
        counter(staff, id, "ready", null, 200);

        JsonNode list = notificationsOf(c.token());
        assertThat(list).extracting(n -> n.get("type").asText()).containsExactly("ORDER_READY", "ORDER_CONFIRMED");
        assertThat(list.get(0).get("body").asText()).as("thông báo Ready nêu hạn nhận").contains("trước");
        assertThat(call(auth(get("/api/notifications/unread-count"), c.token()), 200).get("count").asLong())
                .isEqualTo(2);

        long first = list.get(0).get("id").asLong();
        mvc.perform(auth(post("/api/notifications/" + first + "/read"), c.token())).andExpect(status().isNoContent());
        mvc.perform(auth(post("/api/notifications/" + first + "/read"), c.token())).andExpect(status().isNoContent());
        assertThat(call(auth(get("/api/notifications/unread-count"), c.token()), 200).get("count").asLong())
                .isEqualTo(1);
        assertThat(call(auth(get("/api/notifications?unreadOnly=true"), c.token()), 200).get("content").size())
                .isEqualTo(1);

        // Khách chưa xác minh email trường thì chỉ có thông báo trong ứng dụng, không có email (BR28).
        assertThat(jdbc.sql("SELECT COUNT(*) FROM email_outbox WHERE user_id = :u").param("u", c.userId())
                .query(Integer.class).single()).isZero();

        // Không đọc được thông báo của người khác (BR03).
        Customer other = register(false);
        call(auth(post("/api/notifications/" + first + "/read"), other.token()), 404);
        assertThat(notificationsOf(other.token()).size()).isZero();
    }

    @Test
    void customerOwnCancelDoesNotNotifyThemselvesButCounterRejectDoes() throws Exception {
        Customer c = register(false);
        String staff = login("staff@canteen.local");
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 20_000);
        long mine = placeOrder(c, fx, 1);
        call(auth(post("/api/orders/" + mine + "/cancel"), c.token()), 200);
        assertThat(countNotifications(c.userId(), "ORDER_CANCELLED")).isZero();

        MenuFixture fx2 = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 20_000);
        long rejected = placeOrder(c, fx2, 1);
        counter(staff, rejected, "reject", "{\"reason\":\"Hết nguyên liệu\"}", 200);
        JsonNode list = notificationsOf(c.token());
        assertThat(list.get(0).get("type").asText()).isEqualTo("ORDER_CANCELLED");
        assertThat(list.get(0).get("body").asText()).contains("Hết nguyên liệu");
    }

    /** Khách xin hủy thì nhân sự quầy được báo; quầy quyết định thì khách chỉ nhận MỘT thông báo (không thêm "đơn bị hủy"). */
    @Test
    void cancelRequestNotifiesVendorThenCustomerOnce() throws Exception {
        Customer c = register(false);
        String staff = login("staff@canteen.local");
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 20_000);
        long id = placeOrder(c, fx, 1);
        counter(staff, id, "confirm", null, 200);

        long requestId = call(withJson(post("/api/orders/" + id + "/cancel-requests"), c.token(),
                "{\"reason\":\"Em có việc đột xuất\"}"), 201).get("id").asLong();
        JsonNode staffList = notificationsOf(staff);
        assertThat(staffList).extracting(n -> n.get("type").asText()).contains("CANCEL_REQUEST_RECEIVED");

        call(withJson(post("/api/counter/cancel-requests/" + requestId + "/approve"), staff,
                "{\"reason\":\"Bếp chưa làm\"}"), 200);
        assertThat(countNotifications(c.userId(), "CANCEL_REQUEST_DECIDED")).isEqualTo(1);
        assertThat(countNotifications(c.userId(), "ORDER_CANCELLED")).isZero();
    }

    // ---- job hết hạn Pending (BR13, BR36, TC07) ----

    @Test
    void pendingExpiryJobExpiresReleasesAndIsIdempotent() throws Exception {
        Customer c = register(true);
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        long overdue = placeOrder(c, fx, 2);
        MenuFixture fx2 = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        long fresh = placeOrder(c, fx2, 1);
        makeOverdue(overdue);
        assertThat(reserved(fx.menuItemId())).isEqualTo(2);

        assertThat(pendingExpiryJob.run()).isGreaterThanOrEqualTo(1);
        assertThat(orderStatus(overdue)).isEqualTo("EXPIRED");
        assertThat(orderStatus(fresh)).as("đơn chưa quá hạn không bị đụng").isEqualTo("PENDING");
        assertThat(reserved(fx.menuItemId())).as("trả suất").isZero();
        assertThat(reserved(fx2.menuItemId())).isEqualTo(1);
        assertThat(countNotifications(c.userId(), "ORDER_EXPIRED")).isEqualTo(1);

        pendingExpiryJob.run();   // chạy lần hai: không đổi gì thêm
        assertThat(reserved(fx.menuItemId())).as("không trả suất hai lần").isZero();
        assertThat(countNotifications(c.userId(), "ORDER_EXPIRED")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM order_status_history WHERE order_id = :o").param("o", overdue)
                .query(Integer.class).single()).isEqualTo(2);

        // Quầy xác nhận trước thì job không làm gì đơn đó (chỉ một kết quả thắng).
        String staff = login("staff@canteen.local");
        MenuFixture fx3 = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        long confirmed = placeOrder(c, fx3, 1);
        counter(staff, confirmed, "confirm", null, 200);
        makeOverdue(confirmed);
        pendingExpiryJob.run();
        assertThat(orderStatus(confirmed)).isEqualTo("CONFIRMED");
    }

    @Test
    void expiredOrderReturnsVoucherToCustomer() throws Exception {
        Customer c = register(true);
        promotions.grantIfEligible(users.findById(c.userId()).orElseThrow());
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        long id = placeOrder(c, fx, 2);
        assertThat(jdbc.sql("SELECT status FROM promotion_entitlement WHERE user_id = :u").param("u", c.userId())
                .query(String.class).single()).isEqualTo("HELD");
        makeOverdue(id);
        pendingExpiryJob.run();
        assertThat(jdbc.sql("SELECT status FROM promotion_entitlement WHERE user_id = :u").param("u", c.userId())
                .query(String.class).single()).isEqualTo("AVAILABLE");
    }

    // ---- email outbox (NFR05, BR29) ----

    /** Gửi lỗi: thông báo trong ứng dụng vẫn còn, email chờ thử lại và gửi đúng một lần khi hết lỗi, không tạo dòng thứ hai. */
    @Test
    void emailFailureKeepsNotificationAndRetriesWithoutDuplicate() throws Exception {
        Customer c = register(true);
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        long id = placeOrder(c, fx, 1);
        makeOverdue(id);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        pendingExpiryJob.run();   // Expired: có email, gửi lần đầu lỗi
        String key = "ORDER:" + id + ":EXPIRED";
        assertThat(orderStatus(id)).as("lỗi email không làm mất kết quả xử lý đơn").isEqualTo("EXPIRED");
        assertThat(countNotifications(c.userId(), "ORDER_EXPIRED")).isEqualTo(1);
        assertThat(outboxStatus(key)).isEqualTo("PENDING");
        assertThat(jdbc.sql("SELECT attempts FROM email_outbox WHERE event_key = :k").param("k", key)
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT last_error FROM email_outbox WHERE event_key = :k").param("k", key)
                .query(String.class).single()).contains("smtp down");

        // Chưa đến hạn thử lại thì processor không gửi.
        assertThat(outboxProcessor.processDue()).isZero();

        reset(mailSender);
        jdbc.sql("UPDATE email_outbox SET next_attempt_at = :t WHERE event_key = :k")
                .param("t", LocalDateTime.now(TimeConfig.ZONE).minusMinutes(1)).param("k", key).update();
        assertThat(outboxProcessor.processDue()).isEqualTo(1);
        assertThat(outboxStatus(key)).isEqualTo("SENT");
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));

        outboxProcessor.processDue();   // đã SENT thì không gửi lại
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM email_outbox WHERE event_key = :k").param("k", key)
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void emailBecomesFailedAfterMaxAttemptsButNotificationStays() throws Exception {
        Customer c = register(true);
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        long id = placeOrder(c, fx, 1);
        makeOverdue(id);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));
        pendingExpiryJob.run();
        String key = "ORDER:" + id + ":EXPIRED";

        for (int i = 0; i < 6 && "PENDING".equals(outboxStatus(key)); i++) {
            jdbc.sql("UPDATE email_outbox SET next_attempt_at = :t WHERE event_key = :k")
                    .param("t", LocalDateTime.now(TimeConfig.ZONE).minusMinutes(1)).param("k", key).update();
            outboxProcessor.processDue();
        }
        assertThat(outboxStatus(key)).isEqualTo("FAILED");
        assertThat(jdbc.sql("SELECT attempts FROM email_outbox WHERE event_key = :k").param("k", key)
                .query(Integer.class).single()).isEqualTo(5);
        assertThat(countNotifications(c.userId(), "ORDER_EXPIRED")).isEqualTo(1);
    }

    @Test
    void verifiedCustomerGetsEmailAfterCommitAndUnverifiedDoesNot() throws Exception {
        String staff = login("staff@canteen.local");
        Customer verified = register(true);
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 20_000);
        long id = placeOrder(verified, fx, 1);
        reset(mailSender);   // bỏ email OTP lúc đăng ký, chỉ đếm email do việc quầy hủy đơn
        counter(staff, id, "reject", "{\"reason\":\"Hết nguyên liệu\"}", 200);
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        assertThat(outboxStatus("ORDER:" + id + ":CANCELLED")).isEqualTo("SENT");

        Customer unverified = register(false);
        MenuFixture fx2 = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 20_000);
        long id2 = placeOrder(unverified, fx2, 1);
        reset(mailSender);
        counter(staff, id2, "reject", "{\"reason\":\"Hết nguyên liệu\"}", 200);
        verify(mailSender, times(0)).send(any(SimpleMailMessage.class));
        assertThat(countNotifications(unverified.userId(), "ORDER_CANCELLED")).isEqualTo(1);
    }

    // ---- job quyền ưu đãi và hạn chế ----

    @Test
    void entitlementExpiryJobExpiresAvailableOnlyAndIsIdempotent() throws Exception {
        Customer c = register(true);
        promotions.grantIfEligible(users.findById(c.userId()).orElseThrow());
        jdbc.sql("UPDATE promotion_entitlement SET expires_at = :t WHERE user_id = :u")
                .param("t", LocalDateTime.now(TimeConfig.ZONE).minusMinutes(1)).param("u", c.userId()).update();

        assertThat(entitlementExpiryJob.run()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.sql("SELECT status FROM promotion_entitlement WHERE user_id = :u").param("u", c.userId())
                .query(String.class).single()).isEqualTo("EXPIRED");
        assertThat(entitlementExpiryJob.run()).isZero();
    }

    @Test
    void restrictionExpiryJobEndsRestrictionAndNotifiesOnce() throws Exception {
        Customer c = register(false);
        long adminId = jdbc.sql("SELECT id FROM user_account WHERE email = 'admin@canteen.local'")
                .query(Long.class).single();
        LocalDateTime now = LocalDateTime.now(TimeConfig.ZONE);
        jdbc.sql("""
                INSERT INTO restriction (user_id, status, starts_at, ends_at, reason, decided_by)
                VALUES (:u, 'ACTIVE', :s, :e, 'Thử nghiệm', :d)""")
                .param("u", c.userId()).param("s", now.minusDays(8)).param("e", now.minusMinutes(1))
                .param("d", adminId).update();

        assertThat(restrictionExpiryJob.run()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.sql("SELECT status FROM restriction WHERE user_id = :u").param("u", c.userId())
                .query(String.class).single()).isEqualTo("EXPIRED");
        assertThat(countNotifications(c.userId(), "RESTRICTION_ENDED")).isEqualTo(1);

        restrictionExpiryJob.run();
        assertThat(countNotifications(c.userId(), "RESTRICTION_ENDED")).isEqualTo(1);
    }
}
