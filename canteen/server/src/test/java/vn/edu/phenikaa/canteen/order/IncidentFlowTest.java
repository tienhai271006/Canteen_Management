package vn.edu.phenikaa.canteen.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.phenikaa.canteen.order.OrderTestFixtures.createMenu;
import static vn.edu.phenikaa.canteen.order.OrderTestFixtures.registerCustomer;
import static vn.edu.phenikaa.canteen.order.OrderTestFixtures.withJson;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
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
import vn.edu.phenikaa.canteen.order.OrderTestFixtures.MenuFixture;

/**
 * Pha 10: không nhận món (UC7.8), kết luận sự việc (UC10.3), nhắc nhở và cảnh báo (UC10.4), hạn chế đặt trước
 * (UC10.5), phản hồi (UC10.6) và xử lý món đã làm (UC3.7). Cần Docker; không có Docker thì test tự bỏ qua.
 */
@SpringBootTest(properties = "app.jobs.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class IncidentFlowTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    private static final String LUNCH_VENDOR = "Cơm Nhà Cô Lan";   // staff và manager thuộc quầy này
    private static final String OTHER_VENDOR = "Bún Phở Ba Sao";    // staff KHÔNG thuộc quầy này
    private static final String DEV_PASSWORD = "Canteen@123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcClient jdbc;
    @MockBean JavaMailSender mailSender;

    // ---- helpers ----

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

    private JsonNode postJson(String url, String token, String json, int expected) throws Exception {
        return call(withJson(post(url), token, json), expected);
    }

    private long customerId(String token) throws Exception {
        return call(auth(get("/api/me/profile"), token), 200).get("id").asLong();
    }

    private long placeOrder(String customer, MenuFixture fx, int qty) throws Exception {
        postJson("/api/cart/items", customer, "{\"menuItemId\":" + fx.menuItemId() + ",\"quantity\":" + qty + "}", 200);
        return call(withJson(post("/api/orders"), customer, "{\"pickupSlotId\":" + fx.slotId() + "}")
                .header("Idempotency-Key", UUID.randomUUID().toString()), 201).get("id").asLong();
    }

    private JsonNode counter(String token, long orderId, String action, int expected) throws Exception {
        return call(auth(post("/api/counter/orders/" + orderId + "/" + action), token), expected);
    }

    /** Đặt, xác nhận, chuẩn bị, Ready rồi đẩy hạn nhận về quá khứ (đơn tới hạn cần kiểm tra). */
    private long readyOverdueOrder(String customer, String staff, MenuFixture fx, int qty) throws Exception {
        long id = placeOrder(customer, fx, qty);
        counter(staff, id, "confirm", 200);
        counter(staff, id, "prepare", 200);
        counter(staff, id, "ready", 200);
        jdbc.sql("UPDATE orders SET pickup_deadline = :d WHERE id = :id")
                .param("d", LocalDateTime.now(TimeConfig.ZONE).minusHours(1)).param("id", id).update();
        return id;
    }

    /** Ghi nhận không nhận món và trả id sự việc. */
    private long noShow(String customer, String staff, MenuFixture fx, int qty) throws Exception {
        long id = readyOverdueOrder(customer, staff, fx, qty);
        return postJson("/api/counter/orders/" + id + "/not-collected", staff,
                "{\"verifiedAtCounter\":true,\"note\":\"Khách không đến\"}", 200).get("incidentId").asLong();
    }

    private JsonNode decide(String token, String area, long incidentId, String decision, int expected) throws Exception {
        return postJson("/api/" + area + "/incidents/" + incidentId + "/decision", token,
                "{\"decision\":\"" + decision + "\",\"note\":\"Đã kiểm tra\"}", expected);
    }

    private long countNotifications(long userId, String type) {
        return jdbc.sql("SELECT COUNT(*) FROM notification WHERE user_id = :u AND type = :t")
                .param("u", userId).param("t", type).query(Long.class).single();
    }

    private int reserved(long menuItemId) {
        return jdbc.sql("SELECT reserved_slots FROM quota WHERE menu_item_id = :i").param("i", menuItemId)
                .query(Integer.class).single();
    }

    private JsonNode summary(String admin, long userId) throws Exception {
        return call(auth(get("/api/admin/users/" + userId + "/violations"), admin), 200);
    }

    // ---- UC7.8 ----

    /** BR23, BR24, BR15: chỉ ghi khi quá hạn và đã kiểm tra; ghi xong chưa tính vi phạm và chưa trả suất. */
    @Test
    void noShowNeedsOverdueAndVerification_andDoesNotAutoPunishOrReleaseQuota() throws Exception {
        String customer = registerCustomer(mvc, om);
        String staff = login("staff@canteen.local");
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        long id = placeOrder(customer, fx, 2);
        counter(staff, id, "confirm", 200);
        counter(staff, id, "prepare", 200);
        counter(staff, id, "ready", 200);
        String url = "/api/counter/orders/" + id + "/not-collected";

        assertThat(postJson(url, staff, "{\"verifiedAtCounter\":true}", 409).get("code").asText())
                .as("hạn nhận còn ở tương lai").isEqualTo("NO_SHOW_NOT_OVERDUE");

        jdbc.sql("UPDATE orders SET pickup_deadline = :d WHERE id = :id")
                .param("d", LocalDateTime.now(TimeConfig.ZONE).minusHours(1)).param("id", id).update();
        JsonNode overdue = call(auth(get("/api/counter/orders/overdue?vendorId=" + fx.vendorId()), staff), 200);
        assertThat(overdue.get("content").toString()).contains("\"id\":" + id);

        assertThat(postJson(url, staff, "{\"verifiedAtCounter\":false}", 409).get("code").asText())
                .isEqualTo("NO_SHOW_NOT_VERIFIED");

        JsonNode done = postJson(url, staff, "{\"verifiedAtCounter\":true,\"note\":\"Khách không đến\"}", 200);
        assertThat(done.get("order").get("status").asText()).isEqualTo("NOT_COLLECTED");
        assertThat(done.get("incidentStatus").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(reserved(fx.menuItemId())).as("BR15: NotCollected không tự trả suất").isEqualTo(2);
        assertThat(countNotifications(customerId(customer), "NO_SHOW_RECORDED")).isEqualTo(1);

        JsonNode summary = summary(login("admin@canteen.local"), customerId(customer));
        assertThat(summary.get("validCount").asInt()).as("BR24: chưa tự cộng vi phạm").isZero();

        assertThat(postJson(url, staff, "{\"verifiedAtCounter\":true}", 409).get("code").asText())
                .as("không ghi hai lần").isEqualTo("INVALID_ORDER_TRANSITION");
    }

    @Test
    void staffOfAnotherVendorCannotRecordNoShow() throws Exception {
        String customer = registerCustomer(mvc, om);
        String manager = login("manager@canteen.local");
        String staff = login("staff@canteen.local");
        MenuFixture other = createMenu(jdbc, OTHER_VENDOR, 5, 0, 20_000);
        long id = readyOverdueOrder(customer, manager, other, 1);

        assertThat(postJson("/api/counter/orders/" + id + "/not-collected", staff,
                "{\"verifiedAtCounter\":true}", 403).get("code").asText()).isEqualTo("FORBIDDEN");
        call(auth(get("/api/counter/orders/overdue?vendorId=" + other.vendorId()), staff), 403);
    }

    // ---- UC10.3, UC10.4, UC10.5 ----

    /** BR27: lần 1 nhắc, lần 2 cảnh báo, lần 3 chuyển quản trị viên; hạn chế do admin quyết, chặn đặt nhưng không chặn đăng nhập. */
    @Test
    void escalationLadderThenRestrictionBlocksOrderingButNotLogin() throws Exception {
        String customer = registerCustomer(mvc, om);
        long userId = customerId(customer);
        String staff = login("staff@canteen.local");
        String manager = login("manager@canteen.local");
        String admin = login("admin@canteen.local");

        long first = noShow(customer, staff, createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000), 1);
        decide(manager, "manager", first, "CONFIRM", 200);
        assertThat(countNotifications(userId, "INCIDENT_REMINDER")).isEqualTo(1);

        long second = noShow(customer, staff, createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000), 1);
        decide(manager, "manager", second, "CONFIRM", 200);
        assertThat(countNotifications(userId, "INCIDENT_WARNING")).isEqualTo(1);

        assertThat(postJson("/api/admin/restrictions", admin,
                "{\"userId\":" + userId + ",\"reason\":\"Thử sớm\"}", 409).get("code").asText())
                .as("chưa đủ 3 vi phạm thì chưa có căn cứ").isEqualTo("RESTRICTION_NOT_JUSTIFIED");

        long third = noShow(customer, staff, createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000), 1);
        decide(manager, "manager", third, "CONFIRM", 200);
        assertThat(countNotifications(userId, "INCIDENT_ESCALATED")).isEqualTo(1);
        JsonNode summary = summary(admin, userId);
        assertThat(summary.get("validCount").asInt()).isEqualTo(3);
        assertThat(summary.get("level").asText()).as("không tự khóa tài khoản").isEqualTo("ESCALATED");

        JsonNode r = postJson("/api/admin/restrictions", admin,
                "{\"userId\":" + userId + ",\"reason\":\"Ba lần không nhận món\"}", 200);
        assertThat(r.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(postJson("/api/admin/restrictions", admin,
                "{\"userId\":" + userId + ",\"reason\":\"Lặp\"}", 409).get("code").asText())
                .isEqualTo("RESTRICTION_ALREADY_ACTIVE");

        MenuFixture next = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        postJson("/api/cart/items", customer, "{\"menuItemId\":" + next.menuItemId() + ",\"quantity\":1}", 200);
        JsonNode blocked = call(withJson(post("/api/orders"), customer, "{\"pickupSlotId\":" + next.slotId() + "}")
                .header("Idempotency-Key", UUID.randomUUID().toString()), 403);
        assertThat(blocked.get("code").asText()).isEqualTo("ACCOUNT_RESTRICTED");
        call(auth(get("/api/me/profile"), customer), 200);                       // vẫn đăng nhập và xem được
        call(auth(get("/api/me/incidents"), customer), 200);                     // vẫn xem sự việc của mình

        postJson("/api/admin/restrictions/" + r.get("id").asLong() + "/revoke", admin,
                "{\"reason\":\"Xem lại hồ sơ\"}", 200);
        call(withJson(post("/api/orders"), customer, "{\"pickupSlotId\":" + next.slotId() + "}")
                .header("Idempotency-Key", UUID.randomUUID().toString()), 201);   // gỡ xong thì đặt lại được
    }

    /** BR26: sự việc xảy ra ngoài 30 ngày không được cộng; hủy kết luận loại khỏi số đếm và chỉ quản trị viên được làm. */
    @Test
    void windowSlidesAndRevokeRemovesFromCount() throws Exception {
        String customer = registerCustomer(mvc, om);
        long userId = customerId(customer);
        String staff = login("staff@canteen.local");
        String manager = login("manager@canteen.local");
        String admin = login("admin@canteen.local");

        long a = noShow(customer, staff, createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000), 1);
        decide(manager, "manager", a, "CONFIRM", 200);
        assertThat(summary(admin, userId).get("validCount").asInt()).isEqualTo(1);
        jdbc.sql("UPDATE incident SET occurred_at = :t WHERE id = :id")
                .param("t", LocalDateTime.now(TimeConfig.ZONE).minusDays(31)).param("id", a).update();
        assertThat(summary(admin, userId).get("validCount").asInt()).as("quá 30 ngày thì không tính").isZero();

        long b = noShow(customer, staff, createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000), 1);
        decide(manager, "manager", b, "CONFIRM", 200);
        assertThat(summary(admin, userId).get("validCount").asInt()).isEqualTo(1);
        assertThat(postJson("/api/admin/incidents/" + b + "/revoke", manager,
                "{\"reason\":\"Ghi nhận sai\"}", 403).get("code").asText()).isEqualTo("FORBIDDEN");
        JsonNode revoked = postJson("/api/admin/incidents/" + b + "/revoke", admin, "{\"reason\":\"Ghi nhận sai\"}", 200);
        assertThat(revoked.get("status").asText()).isEqualTo("REVOKED");
        assertThat(summary(admin, userId).get("validCount").asInt()).isZero();
        assertThat(countNotifications(userId, "INCIDENT_REVOKED")).isEqualTo(1);
    }

    /** Quản lý quầy khác không xét được sự việc của quầy này (R10.3). */
    @Test
    void managerCannotDecideAnotherVendorsIncident() throws Exception {
        String customer = registerCustomer(mvc, om);
        String manager = login("manager@canteen.local");
        String staff = login("staff@canteen.local");
        long incident = noShow(customer, staff, createMenu(jdbc, LUNCH_VENDOR, 5, 0, 20_000), 1);

        String other = "mgr." + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        call(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"fullName\":\"QL khác\",\"email\":\"" + other + "\",\"password\":\"Matkhau123\"}"), 201);
        postJson("/api/admin/users/grant-manager", login("admin@canteen.local"), "{\"email\":\"" + other + "\"}", 200);
        String stranger = om.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + other + "\",\"password\":\"Matkhau123\"}"))
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();

        assertThat(decide(stranger, "manager", incident, "CONFIRM", 403).get("code").asText()).isEqualTo("FORBIDDEN");
        decide(manager, "manager", incident, "EXEMPT", 200);
    }

    // ---- UC10.6 ----

    /** BR35: phản hồi đang mở thì sự việc không xác nhận được, cho tới khi phản hồi được giải quyết. */
    @Test
    void openFeedbackBlocksConfirmationUntilResolved() throws Exception {
        String customer = registerCustomer(mvc, om);
        String stranger = registerCustomer(mvc, om);
        String staff = login("staff@canteen.local");
        String manager = login("manager@canteen.local");
        long incident = noShow(customer, staff, createMenu(jdbc, LUNCH_VENDOR, 5, 0, 20_000), 1);

        postJson("/api/me/incidents/" + incident + "/feedback", stranger, "{\"content\":\"Không phải của tôi\"}", 404);
        JsonNode fb = postJson("/api/me/incidents/" + incident + "/feedback", customer,
                "{\"content\":\"Tôi đã đến nhưng quầy đóng cửa\"}", 201);
        postJson("/api/me/incidents/" + incident + "/feedback", customer, "{\"content\":\"Gửi lại\"}", 409);

        assertThat(decide(manager, "manager", incident, "CONFIRM", 409).get("code").asText())
                .isEqualTo("INCIDENT_DISPUTED");

        postJson("/api/manager/feedback/" + fb.get("id").asLong() + "/resolve", manager,
                "{\"response\":\"Quầy xác nhận đóng sớm\",\"action\":\"EXEMPT\"}", 200);
        JsonNode mine = call(auth(get("/api/me/incidents"), customer), 200);
        assertThat(mine.get("content").get(0).get("status").asText()).isEqualTo("EXEMPTED");
        assertThat(mine.get("content").get(0).get("disputed").asBoolean()).isFalse();
        assertThat(countNotifications(customerId(customer), "FEEDBACK_RESOLVED")).isEqualTo(1);
    }

    // ---- UC3.7 ----

    /** BR15: chỉ phần bán lại mới tăng lượng có thể bán; tổng xử lý không vượt số đã đặt; có thể xử lý nhiều đợt. */
    @Test
    void wasteHandlingReleasesOnlyResoldPortion() throws Exception {
        String customer = registerCustomer(mvc, om);
        String staff = login("staff@canteen.local");
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        long ready = readyOverdueOrder(customer, staff, fx, 3);

        postJson("/api/counter/orders/" + ready + "/waste", staff,
                "{\"items\":[{\"orderItemId\":1,\"wastedQuantity\":1}]}", 409);   // chưa NotCollected

        postJson("/api/counter/orders/" + ready + "/not-collected", staff, "{\"verifiedAtCounter\":true}", 200);
        long itemId = call(auth(get("/api/counter/orders/" + ready + "/waste"), staff), 200)
                .get("items").get(0).get("orderItemId").asLong();
        assertThat(reserved(fx.menuItemId())).isEqualTo(3);

        JsonNode step1 = postJson("/api/counter/orders/" + ready + "/waste", staff,
                "{\"items\":[{\"orderItemId\":" + itemId + ",\"resoldQuantity\":1}],\"note\":\"Còn nóng\"}", 200);
        assertThat(step1.get("items").get(0).get("remaining").asInt()).isEqualTo(2);
        assertThat(reserved(fx.menuItemId())).as("bán lại 1 suất thì trả đúng 1 suất").isEqualTo(2);

        JsonNode step2 = postJson("/api/counter/orders/" + ready + "/waste", staff,
                "{\"items\":[{\"orderItemId\":" + itemId + ",\"wastedQuantity\":1}]}", 200);
        assertThat(step2.get("items").get(0).get("remaining").asInt()).isEqualTo(1);
        assertThat(reserved(fx.menuItemId())).as("hao hụt không đổi hạn mức").isEqualTo(2);

        assertThat(postJson("/api/counter/orders/" + ready + "/waste", staff,
                "{\"items\":[{\"orderItemId\":" + itemId + ",\"resoldQuantity\":1,\"wastedQuantity\":1}]}", 409)
                .get("code").asText()).isEqualTo("WASTE_EXCEEDS_REMAINING");
        assertThat(reserved(fx.menuItemId())).as("yêu cầu bị từ chối không để lại thay đổi").isEqualTo(2);

        postJson("/api/counter/orders/" + ready + "/waste", staff,
                "{\"items\":[{\"orderItemId\":" + itemId + ",\"wastedQuantity\":1}]}", 200);
        JsonNode pending = call(auth(get("/api/counter/waste?vendorId=" + fx.vendorId()), staff), 200);
        assertThat(pending.toString()).as("xử lý hết thì rời danh sách chờ").doesNotContain("\"orderId\":" + ready);
    }
}
