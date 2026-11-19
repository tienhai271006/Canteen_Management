package vn.edu.phenikaa.canteen.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.phenikaa.canteen.order.OrderTestFixtures.createMenu;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.edu.phenikaa.canteen.common.config.TimeConfig;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.model.OrderStatus;
import vn.edu.phenikaa.canteen.order.OrderTestFixtures.MenuFixture;
import vn.edu.phenikaa.canteen.repository.OrderRepository;
import vn.edu.phenikaa.canteen.repository.UserAccountRepository;
import vn.edu.phenikaa.canteen.service.Actor;
import vn.edu.phenikaa.canteen.service.OrderService;
import vn.edu.phenikaa.canteen.service.PromotionService;

/**
 * UC8: cấp quyền, giảm giá lúc đặt, giữ cho đúng một đơn, chỉ Đã dùng khi Completed và Paid, trả quyền khi hủy.
 * Dùng chiến dịch mặc định do DevAccountSeeder tạo (50%, tối đa 30.000đ, đơn từ 25.000đ, hạn 7 ngày).
 * Cần Docker (Testcontainers MySQL); không có Docker thì test tự bỏ qua.
 */
@SpringBootTest(properties = "app.jobs.enabled=false")   // job nền chạy tay trong test, không chen vào kịch bản
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class PromotionFlowTest {

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
    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired TransactionTemplate tx;
    @MockBean JavaMailSender mailSender;

    record Customer(String token, long userId, String email) {
    }

    // ---- helpers ----

    /** Khách đã xác minh email trường và đã được cấp quyền (qua cùng hàm mà UC1.2 gọi). */
    private Customer customerWithEntitlement() throws Exception {
        Customer c = register("sv." + UUID.randomUUID().toString().substring(0, 8) + "@phenikaa-uni.edu.vn");
        verify(c);
        promotions.grantIfEligible(users.findById(c.userId()).orElseThrow());
        return c;
    }

    private Customer register(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Khách thử\",\"email\":\"" + email + "\",\"password\":\"Matkhau123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = jdbc.sql("SELECT id FROM user_account WHERE email = :e").param("e", email).query(Long.class).single();
        return new Customer(om.readTree(body).get("accessToken").asText(), id, email);
    }

    private void verify(Customer c) {
        jdbc.sql("UPDATE user_account SET email_verified = TRUE, email_verified_at = NOW(6) WHERE id = :i")
                .param("i", c.userId()).update();
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

    private void addToCart(String token, MenuFixture fx, int qty) throws Exception {
        call(withJson(post("/api/cart/items"), token,
                "{\"menuItemId\":" + fx.menuItemId() + ",\"quantity\":" + qty + "}"), 200);
    }

    /** Đặt đơn Pending; {@code useVoucher} null nghĩa là không gửi trường này. */
    private JsonNode placeOrder(Customer c, MenuFixture fx, int qty, Boolean useVoucher) throws Exception {
        addToCart(c.token(), fx, qty);
        String body = "{\"pickupSlotId\":" + fx.slotId()
                + (useVoucher == null ? "" : ",\"useVoucher\":" + useVoucher) + "}";
        return call(withJson(post("/api/orders"), c.token(), body)
                .header("Idempotency-Key", UUID.randomUUID().toString()), 201);
    }

    private JsonNode counter(String staff, long orderId, String action, String json, int expected) throws Exception {
        String url = "/api/counter/orders/" + orderId + "/" + action;
        return call(json == null ? auth(post(url), staff) : withJson(post(url), staff, json), expected);
    }

    private String entitlementStatus(Customer c) {
        return jdbc.sql("SELECT status FROM promotion_entitlement WHERE user_id = :u").param("u", c.userId())
                .query(String.class).single();
    }

    private Long entitlementOrderId(Customer c) {
        return jdbc.sql("SELECT order_id FROM promotion_entitlement WHERE user_id = :u").param("u", c.userId())
                .query((rs, n) -> rs.getObject("order_id", Long.class)).single();
    }

    private long entitlementId(Customer c) {
        return jdbc.sql("SELECT id FROM promotion_entitlement WHERE user_id = :u").param("u", c.userId())
                .query(Long.class).single();
    }

    // ---- cấp quyền (UC1.2, BR05, BR06) ----

    @Test
    void grantNeedsVerifiedSchoolEmailAndHappensOnce() throws Exception {
        Customer unverified = register("sv." + UUID.randomUUID().toString().substring(0, 8) + "@phenikaa-uni.edu.vn");
        promotions.grantIfEligible(users.findById(unverified.userId()).orElseThrow());
        assertThat(count(unverified)).as("chưa xác minh thì chưa có quyền (BR05)").isZero();

        Customer outsider = register("khach." + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com");
        verify(outsider);
        promotions.grantIfEligible(users.findById(outsider.userId()).orElseThrow());
        assertThat(count(outsider)).as("email ngoài trường không nhận quyền (BR05)").isZero();

        verify(unverified);
        promotions.grantIfEligible(users.findById(unverified.userId()).orElseThrow());
        promotions.grantIfEligible(users.findById(unverified.userId()).orElseThrow());
        assertThat(count(unverified)).as("cấp hai lần vẫn một lượt (BR06)").isEqualTo(1);
        assertThat(entitlementStatus(unverified)).isEqualTo("AVAILABLE");
        LocalDateTime expires = jdbc.sql("SELECT expires_at FROM promotion_entitlement WHERE user_id = :u")
                .param("u", unverified.userId()).query((rs, n) -> rs.getObject(1, LocalDateTime.class)).single();
        assertThat(expires).isAfter(LocalDateTime.now(TimeConfig.ZONE).plusDays(6));
    }

    private int count(Customer c) {
        return jdbc.sql("SELECT COUNT(*) FROM promotion_entitlement WHERE user_id = :u").param("u", c.userId())
                .query(Integer.class).single();
    }

    // ---- UC8.2 ----

    @Test
    void overviewShowsConditionsAndEffectiveStatus() throws Exception {
        Customer c = customerWithEntitlement();
        JsonNode o = call(auth(get("/api/me/promotion"), c.token()), 200);
        assertThat(o.get("status").asText()).isEqualTo("AVAILABLE");
        assertThat(o.get("terms").get("discountPercent").asInt()).isEqualTo(50);
        assertThat(o.get("terms").get("maxDiscountAmount").asLong()).isEqualTo(30_000);
        assertThat(o.get("terms").get("minOrderAmount").asLong()).isEqualTo(25_000);

        jdbc.sql("UPDATE promotion_entitlement SET expires_at = :t WHERE user_id = :u")
                .param("t", LocalDateTime.now(TimeConfig.ZONE).minusMinutes(1)).param("u", c.userId()).update();
        assertThat(call(auth(get("/api/me/promotion"), c.token()), 200).get("status").asText())
                .as("quá hạn là Hết hạn dù job dọn dẹp chưa chạy").isEqualTo("EXPIRED");

        Customer none = register("sv." + UUID.randomUUID().toString().substring(0, 8) + "@phenikaa-uni.edu.vn");
        JsonNode n = call(auth(get("/api/me/promotion"), none.token()), 200);
        assertThat(n.has("status")).isFalse();
        assertThat(n.get("notice").asText()).isNotBlank();
    }

    // ---- UC8.3, BR05, BR07 ----

    /** TC01, TC02 và BR07: giữ khi đặt, chỉ Đã dùng khi Completed và Paid. */
    @Test
    void voucherIsHeldAtOrderAndUsedOnlyWhenCompletedAndPaid() throws Exception {
        Customer c = customerWithEntitlement();
        String staff = login("staff@canteen.local");
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);

        addToCart(c.token(), fx, 2);
        JsonNode cart = call(auth(get("/api/cart"), c.token()), 200);
        assertThat(cart.get("subtotal").asLong()).isEqualTo(60_000);
        assertThat(cart.get("estimatedDiscount").asLong()).as("50% của 60.000đ bị chặn ở 30.000đ").isEqualTo(30_000);
        assertThat(cart.get("estimatedTotal").asLong()).isEqualTo(30_000);

        JsonNode order = call(withJson(post("/api/orders"), c.token(), "{\"pickupSlotId\":" + fx.slotId() + "}")
                .header("Idempotency-Key", UUID.randomUUID().toString()), 201);
        long id = order.get("id").asLong();
        assertThat(order.get("discountAmount").asLong()).isEqualTo(30_000);
        assertThat(order.get("totalAmount").asLong()).isEqualTo(30_000);
        assertThat(entitlementStatus(c)).as("mới Pending: chưa Đã dùng").isEqualTo("HELD");
        assertThat(entitlementOrderId(c)).isEqualTo(id);

        counter(staff, id, "confirm", null, 200);
        counter(staff, id, "prepare", null, 200);
        counter(staff, id, "ready", null, 200);
        assertThat(entitlementStatus(c)).as("Ready vẫn chưa Đã dùng").isEqualTo("HELD");

        String code = order.get("orderCode").asText();
        counter(staff, id, "complete",
                "{\"orderCode\":\"" + code + "\",\"amountReceived\":30000,\"handedOver\":true}", 200);
        assertThat(entitlementStatus(c)).isEqualTo("USED");
        assertThat(jdbc.sql("SELECT used_at FROM promotion_entitlement WHERE user_id = :u").param("u", c.userId())
                .query((rs, n) -> rs.getObject(1, LocalDateTime.class)).single()).isNotNull();

        // Quyền đã dùng thì đơn sau không còn giảm.
        MenuFixture fx2 = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        assertThat(placeOrder(c, fx2, 2, null).get("discountAmount").asLong()).isZero();
        assertThat(entitlementStatus(c)).isEqualTo("USED");
    }

    @Test
    void belowMinimumOrderGetsNoDiscountAndKeepsEntitlement() throws Exception {
        Customer c = customerWithEntitlement();
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 20_000);
        JsonNode order = placeOrder(c, fx, 1, null);
        assertThat(order.get("discountAmount").asLong()).isZero();
        assertThat(order.get("totalAmount").asLong()).isEqualTo(20_000);
        assertThat(entitlementStatus(c)).isEqualTo("AVAILABLE");
    }

    @Test
    void customerCanChooseNotToUseVoucher() throws Exception {
        Customer c = customerWithEntitlement();
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        addToCart(c.token(), fx, 2);
        assertThat(call(auth(get("/api/cart?useVoucher=false"), c.token()), 200).get("estimatedDiscount").asLong())
                .isZero();

        JsonNode without = call(withJson(post("/api/orders"), c.token(),
                "{\"pickupSlotId\":" + fx.slotId() + ",\"useVoucher\":false}")
                .header("Idempotency-Key", UUID.randomUUID().toString()), 201);
        assertThat(without.get("discountAmount").asLong()).isZero();
        assertThat(entitlementStatus(c)).as("để dành cho đơn sau").isEqualTo("AVAILABLE");

        MenuFixture fx2 = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        assertThat(placeOrder(c, fx2, 2, true).get("discountAmount").asLong()).isEqualTo(30_000);
        assertThat(entitlementStatus(c)).isEqualTo("HELD");
    }

    /** Quyền đang giữ cho một đơn thì đơn thứ hai không được giảm lần nữa (BR07). */
    @Test
    void heldVoucherIsNotOfferedToSecondOrder() throws Exception {
        Customer c = customerWithEntitlement();
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        MenuFixture fx2 = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        JsonNode first = placeOrder(c, fx, 2, null);
        JsonNode second = placeOrder(c, fx2, 2, null);
        assertThat(first.get("discountAmount").asLong()).isEqualTo(30_000);
        assertThat(second.get("discountAmount").asLong()).isZero();
        assertThat(entitlementOrderId(c)).isEqualTo(first.get("id").asLong());
    }

    /** TC04: hai đơn cùng xin giữ một quyền thì chỉ một bên được giữ; bên còn lại nhận lỗi để khách chọn lại. */
    @Test
    void holdIsExclusive() throws Exception {
        Customer c = customerWithEntitlement();
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        MenuFixture fx2 = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        long a = placeOrder(c, fx, 2, false).get("id").asLong();
        long b = placeOrder(c, fx2, 2, false).get("id").asLong();
        long ent = entitlementId(c);

        promotions.hold(ent, a);
        assertThatThrownBy(() -> promotions.hold(ent, b))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PROMOTION_NOT_ELIGIBLE));
        assertThat(entitlementOrderId(c)).isEqualTo(a);
    }

    @Test
    void expiredEntitlementGivesNoDiscount() throws Exception {
        Customer c = customerWithEntitlement();
        jdbc.sql("UPDATE promotion_entitlement SET expires_at = :t WHERE user_id = :u")
                .param("t", LocalDateTime.now(TimeConfig.ZONE).minusMinutes(1)).param("u", c.userId()).update();
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        assertThat(placeOrder(c, fx, 2, null).get("discountAmount").asLong()).isZero();
    }

    // ---- trả quyền (BR07) ----

    @Test
    void cancellingPendingOrReceivingRejectReturnsEntitlement() throws Exception {
        Customer c = customerWithEntitlement();
        String staff = login("staff@canteen.local");

        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        long id = placeOrder(c, fx, 2, null).get("id").asLong();
        assertThat(entitlementStatus(c)).isEqualTo("HELD");
        call(auth(post("/api/orders/" + id + "/cancel"), c.token()), 200);
        assertThat(entitlementStatus(c)).isEqualTo("AVAILABLE");
        assertThat(entitlementOrderId(c)).isNull();
        call(auth(post("/api/orders/" + id + "/cancel"), c.token()), 200);   // hủy lặp không đổi gì
        assertThat(entitlementStatus(c)).isEqualTo("AVAILABLE");

        MenuFixture fx2 = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        JsonNode again = placeOrder(c, fx2, 2, null);
        assertThat(again.get("discountAmount").asLong()).as("quyền dùng lại được").isEqualTo(30_000);
        counter(staff, again.get("id").asLong(), "reject", "{\"reason\":\"Hết nguyên liệu\"}", 200);
        assertThat(entitlementStatus(c)).isEqualTo("AVAILABLE");
    }

    @Test
    void cancelAfterEntitlementExpiredMarksItExpired() throws Exception {
        Customer c = customerWithEntitlement();
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        long id = placeOrder(c, fx, 2, null).get("id").asLong();
        jdbc.sql("UPDATE promotion_entitlement SET expires_at = :t WHERE user_id = :u")
                .param("t", LocalDateTime.now(TimeConfig.ZONE).minusMinutes(1)).param("u", c.userId()).update();
        call(auth(post("/api/orders/" + id + "/cancel"), c.token()), 200);
        assertThat(entitlementStatus(c)).as("hủy hợp lệ chỉ trả quyền nếu còn hạn").isEqualTo("EXPIRED");
    }

    /** BR07: đơn không nhận món thì quyền sang Chờ xem xét. Pha 10 sẽ gọi transition này; ở đây gọi trực tiếp. */
    @Test
    void notCollectedMovesEntitlementToReview() throws Exception {
        Customer c = customerWithEntitlement();
        String staff = login("staff@canteen.local");
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        long id = placeOrder(c, fx, 2, null).get("id").asLong();
        counter(staff, id, "confirm", null, 200);
        counter(staff, id, "prepare", null, 200);
        counter(staff, id, "ready", null, 200);

        tx.executeWithoutResult(s -> orderService.transition(
                orderRepository.findDetailById(id).orElseThrow(), OrderStatus.NOT_COLLECTED, Actor.system(),
                "Quá hạn nhận"));
        assertThat(entitlementStatus(c)).isEqualTo("UNDER_REVIEW");
    }

    // ---- UC8.1 ----

    @Test
    void adminManagesCampaignsAndOthersCannot() throws Exception {
        String admin = login("admin@canteen.local");
        String customer = register("sv." + UUID.randomUUID().toString().substring(0, 8) + "@phenikaa-uni.edu.vn").token();
        String staff = login("staff@canteen.local");
        call(auth(get("/api/admin/campaigns"), customer), 403);
        call(auth(get("/api/admin/campaigns"), staff), 403);

        String farFrom = LocalDateTime.now(TimeConfig.ZONE).plusYears(5).withNano(0).toString();
        String farTo = LocalDateTime.now(TimeConfig.ZONE).plusYears(6).withNano(0).toString();
        String valid = "{\"name\":\"Chiến dịch thử\",\"discountPercent\":50,\"maxDiscountAmount\":30000,"
                + "\"minOrderAmount\":25000,\"entitlementValidDays\":7,\"startsAt\":\"" + farFrom
                + "\",\"endsAt\":\"" + farTo + "\"}";

        call(withJson(post("/api/admin/campaigns"), admin, valid.replace("\"discountPercent\":50", "\"discountPercent\":0")), 400);
        call(withJson(post("/api/admin/campaigns"), admin,
                valid.replace(farTo, farFrom)), 400);   // kết thúc không sau bắt đầu

        String nowStr = LocalDateTime.now(TimeConfig.ZONE).withNano(0).toString();
        String overlapping = valid.replace(farFrom, nowStr).replace(farTo, LocalDateTime.now(TimeConfig.ZONE).plusDays(10).withNano(0).toString());
        assertThat(call(withJson(post("/api/admin/campaigns"), admin, overlapping), 409).get("code").asText())
                .as("trùng thời gian với chiến dịch mặc định đang hiệu lực").isEqualTo("CAMPAIGN_OVERLAP");

        JsonNode created = call(withJson(post("/api/admin/campaigns"), admin, valid), 201);
        long id = created.get("id").asLong();
        assertThat(created.get("active").asBoolean()).isTrue();

        JsonNode updated = call(withJson(put("/api/admin/campaigns/" + id), admin,
                valid.replace("\"maxDiscountAmount\":30000", "\"maxDiscountAmount\":20000")), 200);
        assertThat(updated.get("maxDiscountAmount").asLong()).isEqualTo(20_000);

        assertThat(call(auth(post("/api/admin/campaigns/" + id + "/deactivate"), admin), 200).get("active").asBoolean())
                .isFalse();
        assertThat(call(auth(post("/api/admin/campaigns/" + id + "/activate"), admin), 200).get("active").asBoolean())
                .isTrue();
        call(auth(post("/api/admin/campaigns/" + id + "/deactivate"), admin), 200);
        assertThat(call(auth(get("/api/admin/campaigns"), admin), 200).get("content").size()).isGreaterThanOrEqualTo(2);
    }
}
