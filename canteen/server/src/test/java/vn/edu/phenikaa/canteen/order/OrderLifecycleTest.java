package vn.edu.phenikaa.canteen.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.phenikaa.canteen.order.OrderTestFixtures.createMenu;
import static vn.edu.phenikaa.canteen.order.OrderTestFixtures.registerCustomer;
import static vn.edu.phenikaa.canteen.order.OrderTestFixtures.withJson;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
 * UC6 và UC7: vòng đời đơn qua API, từ đặt đến bàn giao, hủy trực tiếp, xin hủy có duyệt và đến muộn.
 * Cần Docker (Testcontainers MySQL); không có Docker thì test tự bỏ qua.
 */
@SpringBootTest(properties = "app.jobs.enabled=false")   // job nền chạy tay trong test, không chen vào kịch bản
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class OrderLifecycleTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    private static final String LUNCH_VENDOR = "Cơm Nhà Cô Lan";   // staff@canteen.local thuộc quầy này
    private static final String OTHER_VENDOR = "Bún Phở Ba Sao";    // staff@canteen.local KHÔNG thuộc quầy này
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

    /** Đặt một đơn Pending và trả về id. */
    private long placeOrder(String customer, MenuFixture fx, int qty) throws Exception {
        call(withJson(post("/api/cart/items"), customer,
                "{\"menuItemId\":" + fx.menuItemId() + ",\"quantity\":" + qty + "}"), 200);
        return call(withJson(post("/api/orders"), customer, "{\"pickupSlotId\":" + fx.slotId() + "}")
                .header("Idempotency-Key", UUID.randomUUID().toString()), 201).get("id").asLong();
    }

    private JsonNode counter(String staff, long orderId, String action, String json, int expected) throws Exception {
        String url = "/api/counter/orders/" + orderId + "/" + action;
        return call(json == null ? auth(post(url), staff) : withJson(post(url), staff, json), expected);
    }

    private JsonNode order(String customer, long orderId) throws Exception {
        return call(auth(get("/api/orders/" + orderId), customer), 200);
    }

    private int reserved(long menuItemId) {
        return jdbc.sql("SELECT reserved_slots FROM quota WHERE menu_item_id = :i").param("i", menuItemId)
                .query(Integer.class).single();
    }

    private long historyCount(long orderId) {
        return jdbc.sql("SELECT COUNT(*) FROM order_status_history WHERE order_id = :o").param("o", orderId)
                .query(Long.class).single();
    }

    // ---- UC7.1 đến UC7.4 ----

    /** Luồng chính đến Completed; BR20: chỉ Completed khi đúng mã, đúng số tiền và đã bàn giao. */
    @Test
    void mainFlowCompletesOnlyWithCodeAmountAndHandover() throws Exception {
        String customer = registerCustomer(mvc, om);
        String staff = login("staff@canteen.local");
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);
        long id = placeOrder(customer, fx, 2);
        String code = order(customer, id).get("orderCode").asText();

        assertThat(counter(staff, id, "confirm", null, 200).get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(reserved(fx.menuItemId())).as("xác nhận không giữ suất lần hai (BR14)").isEqualTo(2);
        assertThat(counter(staff, id, "prepare", null, 200).get("status").asText()).isEqualTo("PREPARING");

        JsonNode ready = counter(staff, id, "ready", null, 200);
        assertThat(ready.get("status").asText()).isEqualTo("READY");
        LocalDate menuDate = LocalDate.parse(ready.get("menuDate").asText());
        assertThat(LocalDateTime.parse(ready.get("pickupDeadline").asText()))
                .as("hạn nhận = cuối khung 11:30 cộng 15 phút (BR22)")
                .isEqualTo(LocalDateTime.of(menuDate, LocalTime.of(11, 45)));

        String ok = "{\"orderCode\":\"" + code + "\",\"amountReceived\":60000,\"handedOver\":true}";
        counter(staff, id, "complete",
                "{\"orderCode\":\"SAI-MA\",\"amountReceived\":60000,\"handedOver\":true}", 400);
        assertThat(counter(staff, id, "complete",
                "{\"orderCode\":\"" + code + "\",\"amountReceived\":50000,\"handedOver\":true}", 409)
                .get("code").asText()).isEqualTo("PAYMENT_AMOUNT_MISMATCH");
        assertThat(counter(staff, id, "complete",
                "{\"orderCode\":\"" + code + "\",\"amountReceived\":60000,\"handedOver\":false}", 409)
                .get("code").asText()).isEqualTo("HANDOVER_NOT_CONFIRMED");
        assertThat(order(customer, id).get("status").asText()).as("thiếu điều kiện thì chưa Completed")
                .isEqualTo("READY");

        JsonNode done = counter(staff, id, "complete", ok, 200);
        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(historyCount(id)).isEqualTo(5);

        JsonNode list = call(auth(get("/api/orders?status=COMPLETED"), customer), 200);
        assertThat(list.get("content").size()).isEqualTo(1);
        JsonNode history = call(auth(get("/api/orders/" + id + "/history"), customer), 200);
        assertThat(history.size()).isEqualTo(5);
        assertThat(history.get(1).get("actorType").asText()).isEqualTo("STAFF");
    }

    /** BR13: đơn quá hạn xác nhận thì quầy không nhận lại được. */
    @Test
    void overduePendingCannotBeConfirmed() throws Exception {
        String customer = registerCustomer(mvc, om);
        String staff = login("staff@canteen.local");
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 5, 0, 20_000);
        long id = placeOrder(customer, fx, 1);
        jdbc.sql("UPDATE orders SET pending_expires_at = :t WHERE id = :o")
                .param("t", LocalDateTime.now(TimeConfig.ZONE).minusMinutes(1)).param("o", id).update();

        assertThat(counter(staff, id, "confirm", null, 409).get("code").asText()).isEqualTo("ORDER_EXPIRED");
        assertThat(order(customer, id).get("status").asText()).isEqualTo("PENDING");
    }

    /** NFR01, BR03: nhân viên quầy này không thao tác được đơn của quầy khác. */
    @Test
    void staffCannotHandleOtherVendorsOrder() throws Exception {
        String customer = registerCustomer(mvc, om);
        String staff = login("staff@canteen.local");
        MenuFixture other = createMenu(jdbc, OTHER_VENDOR, 5, 0, 20_000);
        long id = placeOrder(customer, other, 1);

        assertThat(counter(staff, id, "confirm", null, 403).get("code").asText()).isEqualTo("FORBIDDEN");
        call(auth(get("/api/counter/orders?vendorId=" + other.vendorId()), staff), 403);
        call(auth(get("/api/counter/orders/" + id), customer), 403);   // khách không vào được API quầy
    }

    // ---- UC6.2 ----

    /** BR14, BR16: khách hủy Pending thì trả suất đúng một lần, hủy lại không trả thêm. */
    @Test
    void customerCancelsPendingOnceAndRepeatIsHarmless() throws Exception {
        String customer = registerCustomer(mvc, om);
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 20_000);
        long id = placeOrder(customer, fx, 3);
        assertThat(reserved(fx.menuItemId())).isEqualTo(3);

        JsonNode first = call(auth(post("/api/orders/" + id + "/cancel"), customer), 200);
        assertThat(first.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(reserved(fx.menuItemId())).isZero();

        JsonNode again = call(auth(post("/api/orders/" + id + "/cancel"), customer), 200);
        assertThat(again.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(reserved(fx.menuItemId())).as("không cộng suất lần hai").isZero();
        assertThat(historyCount(id)).isEqualTo(2);
    }

    // ---- UC6.3, UC7.6 ----

    /** BR16, BR17, BR19: xin hủy Confirmed phải được quầy duyệt, quầy phải xử lý trước khi chuẩn bị. */
    @Test
    void cancelRequestOnConfirmedOrderNeedsApproval() throws Exception {
        String customer = registerCustomer(mvc, om);
        String staff = login("staff@canteen.local");
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 20_000);
        long id = placeOrder(customer, fx, 2);
        counter(staff, id, "confirm", null, 200);

        assertThat(call(auth(post("/api/orders/" + id + "/cancel"), customer), 409).get("code").asText())
                .isEqualTo("CANCEL_NOT_ALLOWED");

        String url = "/api/orders/" + id + "/cancel-requests";
        JsonNode req = call(withJson(post(url), customer, "{\"reason\":\"Em có việc đột xuất\"}"), 201);
        long requestId = req.get("id").asLong();
        assertThat(req.get("status").asText()).isEqualTo("PENDING");
        assertThat(order(customer, id).get("status").asText()).as("gửi yêu cầu không đổi trạng thái").isEqualTo("CONFIRMED");
        assertThat(reserved(fx.menuItemId())).isEqualTo(2);

        assertThat(call(withJson(post(url), customer, "{\"reason\":\"Gửi lần hai\"}"), 409).get("code").asText())
                .isEqualTo("CANCEL_REQUEST_PENDING");
        assertThat(counter(staff, id, "prepare", null, 409).get("code").asText()).isEqualTo("CANCEL_REQUEST_PENDING");

        JsonNode pending = call(auth(get("/api/counter/cancel-requests?vendorId=" + fx.vendorId()), staff), 200);
        assertThat(pending.get("content").size()).isGreaterThanOrEqualTo(1);

        call(withJson(post("/api/counter/cancel-requests/" + requestId + "/approve"), staff, "{\"reason\":\"\"}"), 400);
        JsonNode approved = call(withJson(post("/api/counter/cancel-requests/" + requestId + "/approve"), staff,
                "{\"reason\":\"Khách gọi điện, bếp chưa làm\",\"channel\":\"PHONE\"}"), 200);
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("handleChannel").asText()).isEqualTo("PHONE");
        assertThat(order(customer, id).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(reserved(fx.menuItemId())).as("duyệt hủy trả suất đúng một lần").isZero();

        assertThat(call(withJson(post("/api/counter/cancel-requests/" + requestId + "/approve"), staff,
                "{\"reason\":\"Lần hai\"}"), 409).get("code").asText()).isEqualTo("REQUEST_ALREADY_HANDLED");
        assertThat(reserved(fx.menuItemId())).isZero();
        Long handler = jdbc.sql("SELECT handled_by FROM cancel_request WHERE id = :i").param("i", requestId)
                .query(Long.class).single();
        assertThat(handler).as("lưu người xử lý (BR19)").isNotNull();
    }

    @Test
    void rejectedCancelRequestLetsOrderContinueAndCustomerCanWithdraw() throws Exception {
        String customer = registerCustomer(mvc, om);
        String staff = login("staff@canteen.local");
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 20_000);
        long id = placeOrder(customer, fx, 1);
        counter(staff, id, "confirm", null, 200);

        String url = "/api/orders/" + id + "/cancel-requests";
        long first = call(withJson(post(url), customer, "{\"reason\":\"Muốn đổi ý\"}"), 201).get("id").asLong();
        JsonNode rejected = call(withJson(post("/api/counter/cancel-requests/" + first + "/reject"), staff,
                "{\"reason\":\"Bếp đã sẵn nguyên liệu\"}"), 200);
        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
        assertThat(counter(staff, id, "prepare", null, 200).get("status").asText()).isEqualTo("PREPARING");
        assertThat(reserved(fx.menuItemId())).isEqualTo(1);

        // Đơn đã Preparing: không xin hủy được nữa, chỉ xin hỗ trợ ngoại lệ.
        assertThat(call(withJson(post(url), customer, "{\"reason\":\"Xin hủy\"}"), 409).get("code").asText())
                .isEqualTo("CANCEL_NOT_ALLOWED");

        // Rút yêu cầu: dùng đơn khác.
        MenuFixture fx2 = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 20_000);
        long id2 = placeOrder(customer, fx2, 1);
        counter(staff, id2, "confirm", null, 200);
        long second = call(withJson(post("/api/orders/" + id2 + "/cancel-requests"), customer,
                "{\"reason\":\"Có thể hủy\"}"), 201).get("id").asLong();
        JsonNode withdrawn = call(auth(delete("/api/orders/" + id2 + "/cancel-requests/" + second), customer), 200);
        assertThat(withdrawn.get("status").asText()).isEqualTo("WITHDRAWN");
        assertThat(counter(staff, id2, "prepare", null, 200).get("status").asText()).isEqualTo("PREPARING");
    }

    /** UC7.5: quầy không thể phục vụ thì hủy kèm lý do và trả suất. */
    @Test
    void counterRejectCancelsWithReasonAndReleasesQuota() throws Exception {
        String customer = registerCustomer(mvc, om);
        String staff = login("staff@canteen.local");
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 20_000);
        long id = placeOrder(customer, fx, 2);

        counter(staff, id, "reject", "{\"reason\":\"\"}", 400);
        JsonNode r = counter(staff, id, "reject", "{\"reason\":\"Hết nguyên liệu\"}", 200);
        assertThat(r.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(r.get("closedReason").asText()).contains("Hết nguyên liệu");
        assertThat(reserved(fx.menuItemId())).isZero();
    }

    // ---- UC6.4, UC7.7 ----

    /** BR22: gia hạn được duyệt thay hạn cũ và không bị tính lại khi báo Ready. */
    @Test
    void approvedLateArrivalSetsDeadlineThatReadyKeeps() throws Exception {
        String customer = registerCustomer(mvc, om);
        String staff = login("staff@canteen.local");
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 20_000);
        long id = placeOrder(customer, fx, 1);
        counter(staff, id, "confirm", null, 200);

        LocalDateTime now = LocalDateTime.now(TimeConfig.ZONE).withNano(0);
        String url = "/api/orders/" + id + "/late-arrivals";
        call(withJson(post(url), customer, "{\"reason\":\"Kẹt xe\",\"proposedArrivalAt\":\""
                + now.minusMinutes(5) + "\"}"), 400);
        JsonNode req = call(withJson(post(url), customer, "{\"reason\":\"Kẹt xe\",\"proposedArrivalAt\":\""
                + now.plusHours(1) + "\"}"), 201);
        long requestId = req.get("id").asLong();
        assertThat(call(withJson(post(url), customer, "{\"reason\":\"Lần hai\",\"proposedArrivalAt\":\""
                + now.plusHours(1) + "\"}"), 409).get("code").asText()).isEqualTo("LATE_ARRIVAL_PENDING");
        assertThat(order(customer, id).has("pickupDeadline")).as("yêu cầu đang chờ không tự gia hạn").isFalse();

        LocalDateTime agreed = now.plusHours(2);
        call(withJson(post("/api/counter/late-arrivals/" + requestId + "/approve"), staff,
                "{\"newDeadline\":\"" + now.minusMinutes(1) + "\",\"reason\":\"Trễ\"}"), 400);
        JsonNode approved = call(withJson(post("/api/counter/late-arrivals/" + requestId + "/approve"), staff,
                "{\"newDeadline\":\"" + agreed + "\",\"reason\":\"Khách gọi, đồng ý giữ món\",\"channel\":\"PHONE\"}"), 200);
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(LocalDateTime.parse(order(customer, id).get("pickupDeadline").asText())).isEqualTo(agreed);

        counter(staff, id, "prepare", null, 200);
        JsonNode ready = counter(staff, id, "ready", null, 200);
        assertThat(LocalDateTime.parse(ready.get("pickupDeadline").asText()))
                .as("Ready giữ hạn đã thỏa thuận").isEqualTo(agreed);
    }

    @Test
    void lateArrivalRejectedLeavesDeadlineUntouched() throws Exception {
        String customer = registerCustomer(mvc, om);
        String staff = login("staff@canteen.local");
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 20_000);
        long id = placeOrder(customer, fx, 1);
        counter(staff, id, "confirm", null, 200);

        LocalDateTime now = LocalDateTime.now(TimeConfig.ZONE).withNano(0);
        long requestId = call(withJson(post("/api/orders/" + id + "/late-arrivals"), customer,
                "{\"reason\":\"Kẹt xe\",\"proposedArrivalAt\":\"" + now.plusHours(1) + "\"}"), 201).get("id").asLong();
        JsonNode rejected = call(withJson(post("/api/counter/late-arrivals/" + requestId + "/reject"), staff,
                "{\"reason\":\"Quầy đóng cửa sớm\"}"), 200);
        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
        assertThat(order(customer, id).has("pickupDeadline")).isFalse();
    }
}
