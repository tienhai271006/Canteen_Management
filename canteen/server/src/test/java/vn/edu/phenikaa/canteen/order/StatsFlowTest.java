package vn.edu.phenikaa.canteen.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.phenikaa.canteen.order.OrderTestFixtures.createMenu;
import static vn.edu.phenikaa.canteen.order.OrderTestFixtures.registerCustomer;
import static vn.edu.phenikaa.canteen.order.OrderTestFixtures.withJson;

import java.time.LocalDate;
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
 * Pha 11: thống kê quầy (UC9) và tổng hợp admin (UC10.7). Dựng một quầy mới với đủ các kết cục của đơn rồi đối chiếu
 * từng con số. Cần Docker; không có Docker thì test tự bỏ qua.
 */
@SpringBootTest(properties = "app.jobs.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class StatsFlowTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    private static final String LUNCH_VENDOR = "Cơm Nhà Cô Lan";
    private static final String DEV_PASSWORD = "Canteen@123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcClient jdbc;
    @MockBean JavaMailSender mailSender;

    // ---- helpers ----

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("accessToken").asText();
    }

    private String login(String email) throws Exception {
        return login(email, DEV_PASSWORD);
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

    private long placeOrder(String customer, MenuFixture fx, int qty) throws Exception {
        postJson("/api/cart/items", customer, "{\"menuItemId\":" + fx.menuItemId() + ",\"quantity\":" + qty + "}", 200);
        return call(withJson(post("/api/orders"), customer, "{\"pickupSlotId\":" + fx.slotId() + "}")
                .header("Idempotency-Key", UUID.randomUUID().toString()), 201).get("id").asLong();
    }

    private void counter(String token, long orderId, String action) throws Exception {
        call(auth(post("/api/counter/orders/" + orderId + "/" + action), token), 200);
    }

    private void toReady(String staff, long id) throws Exception {
        counter(staff, id, "confirm");
        counter(staff, id, "prepare");
        counter(staff, id, "ready");
    }

    private JsonNode stats(String token, String query, int expected) throws Exception {
        return call(auth(get("/api/manager/stats?" + query), token), expected);
    }

    /**
     * Dựng một thực đơn với bốn kết cục (giá 30.000đ/suất, 10 suất):
     * A: 2 suất Completed và Paid, giảm 10.000đ; B: 1 suất Cancelled; C: 3 suất NotCollected (bán lại 1, hao hụt 1);
     * D: 1 suất còn Pending.
     */
    private MenuFixture seedScenario(String staff) throws Exception {
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 30_000);

        String a = registerCustomer(mvc, om);
        long orderA = placeOrder(a, fx, 2);
        toReady(staff, orderA);
        jdbc.sql("UPDATE orders SET discount_amount = 10000, total_amount = 50000 WHERE id = :id")
                .param("id", orderA).update();   // mô phỏng đơn có ưu đãi 10.000đ (CHECK total = subtotal - discount)
        String code = call(auth(get("/api/orders/" + orderA), a), 200).get("orderCode").asText();
        postJson("/api/counter/orders/" + orderA + "/complete", staff,
                "{\"orderCode\":\"" + code + "\",\"amountReceived\":50000,\"handedOver\":true}", 200);

        long orderB = placeOrder(registerCustomer(mvc, om), fx, 1);
        postJson("/api/counter/orders/" + orderB + "/reject", staff, "{\"reason\":\"Hết nguyên liệu\"}", 200);

        long orderC = placeOrder(registerCustomer(mvc, om), fx, 3);
        toReady(staff, orderC);
        jdbc.sql("UPDATE orders SET pickup_deadline = :d WHERE id = :id")
                .param("d", LocalDateTime.now(TimeConfig.ZONE).minusHours(1)).param("id", orderC).update();
        postJson("/api/counter/orders/" + orderC + "/not-collected", staff, "{\"verifiedAtCounter\":true}", 200);
        long itemC = call(auth(get("/api/counter/orders/" + orderC + "/waste"), staff), 200)
                .get("items").get(0).get("orderItemId").asLong();
        postJson("/api/counter/orders/" + orderC + "/waste", staff,
                "{\"items\":[{\"orderItemId\":" + itemC + ",\"resoldQuantity\":1,\"wastedQuantity\":1}]}", 200);

        placeOrder(registerCustomer(mvc, om), fx, 1);   // D: Pending
        return fx;
    }

    private LocalDate menuDate(MenuFixture fx) {
        return jdbc.sql("SELECT menu_date FROM menu WHERE id = :i").param("i", fx.menuId())
                .query(LocalDate.class).single();
    }

    // ---- UC9 ----

    @Test
    void vendorStatsSeparateOrdersRevenueDishesAndWaste() throws Exception {
        String staff = login("staff@canteen.local");
        String manager = login("manager@canteen.local");
        MenuFixture fx = seedScenario(staff);
        LocalDate day = menuDate(fx);
        String q = "vendorId=" + fx.vendorId() + "&from=" + day + "&to=" + day;

        JsonNode s = stats(manager, q, 200);

        // UC9.1: đếm theo trạng thái, đủ mọi trạng thái
        JsonNode by = s.get("orders").get("byStatus");
        assertThat(s.get("orders").get("total").asLong()).isEqualTo(4);
        assertThat(by.get("COMPLETED").asLong()).isEqualTo(1);
        assertThat(by.get("CANCELLED").asLong()).isEqualTo(1);
        assertThat(by.get("NOT_COLLECTED").asLong()).isEqualTo(1);
        assertThat(by.get("PENDING").asLong()).isEqualTo(1);
        assertThat(by.get("EXPIRED").asLong()).as("trạng thái không có thì bằng 0").isZero();
        assertThat(s.get("orders").get("breakdown").size()).isEqualTo(4);
        assertThat(s.get("orders").get("breakdown").get(0).get("session").asText()).isEqualTo("LUNCH");

        // UC9.2, BR34: chỉ Completed và Paid; tách trước giảm, ưu đãi, thực thu
        JsonNode rev = s.get("revenue");
        assertThat(rev.get("completedPaidOrders").asLong()).isEqualTo(1);
        assertThat(rev.get("grossAmount").asLong()).isEqualTo(60_000);
        assertThat(rev.get("discountAmount").asLong()).isEqualTo(10_000);
        assertThat(rev.get("netRevenue").asLong()).as("đơn không nhận chưa thanh toán không tạo doanh thu")
                .isEqualTo(50_000);
        assertThat(rev.get("discountedOrders").asLong()).isEqualTo(1);
        assertThat(rev.get("grossAmount").asLong() - rev.get("discountAmount").asLong())
                .isEqualTo(rev.get("netRevenue").asLong());
        assertThat(rev.get("byDay").size()).isEqualTo(1);

        // UC9.3: đặt = bán + hủy + không nhận + đang xử lý
        JsonNode dish = s.get("dishes").get(0);
        assertThat(s.get("dishes").size()).isEqualTo(1);
        assertThat(dish.get("ordered").asLong()).isEqualTo(7);
        assertThat(dish.get("sold").asLong()).isEqualTo(2);
        assertThat(dish.get("cancelled").asLong()).isEqualTo(1);
        assertThat(dish.get("notCollected").asLong()).isEqualTo(3);
        assertThat(dish.get("inProgress").asLong()).isEqualTo(1);
        assertThat(dish.get("soldGrossAmount").asLong()).isEqualTo(60_000);

        // UC9.4: không nhận, bán lại, hao hụt, còn chưa xử lý; sự việc của quầy
        JsonNode w = s.get("waste");
        assertThat(w.get("notCollectedOrders").asLong()).isEqualTo(1);
        assertThat(w.get("notCollectedPortions").asLong()).isEqualTo(3);
        assertThat(w.get("resold").asLong()).isEqualTo(1);
        assertThat(w.get("wasted").asLong()).isEqualTo(1);
        assertThat(w.get("unprocessed").asLong()).isEqualTo(1);
        assertThat(w.get("incidentsByStatus").get("PENDING_REVIEW").asLong()).isEqualTo(1);
        assertThat(w.get("incidentsByStatus").get("CONFIRMED").asLong()).isZero();
    }

    @Test
    void dateAndSessionFiltersApplyToServiceDay() throws Exception {
        String staff = login("staff@canteen.local");
        String manager = login("manager@canteen.local");
        MenuFixture fx = seedScenario(staff);
        LocalDate day = menuDate(fx);
        String vendor = "vendorId=" + fx.vendorId();

        assertThat(stats(manager, vendor + "&from=" + day + "&to=" + day + "&session=LUNCH", 200)
                .get("orders").get("total").asLong()).isEqualTo(4);
        assertThat(stats(manager, vendor + "&from=" + day + "&to=" + day + "&session=MORNING", 200)
                .get("orders").get("total").asLong()).as("buổi khác thì không có").isZero();
        JsonNode other = stats(manager, vendor + "&from=" + day.plusDays(1) + "&to=" + day.plusDays(1), 200);
        assertThat(other.get("orders").get("total").asLong()).as("ngày khác thì không có").isZero();
        assertThat(other.get("revenue").get("netRevenue").asLong()).isZero();
        assertThat(other.get("dishes").size()).isZero();
        // mặc định 7 ngày gần nhất đến hôm nay không chứa ngày phục vụ ở tương lai
        assertThat(stats(manager, vendor, 200).get("orders").get("total").asLong()).isZero();
    }

    @Test
    void invalidRangeIsRejected() throws Exception {
        String manager = login("manager@canteen.local");
        long vendor = jdbc.sql("SELECT id FROM vendor WHERE name = :n").param("n", LUNCH_VENDOR)
                .query(Long.class).single();
        assertThat(stats(manager, "vendorId=" + vendor + "&from=2026-02-02&to=2026-02-01", 400)
                .get("code").asText()).isEqualTo("VALIDATION_FAILED");
        stats(manager, "vendorId=" + vendor + "&from=2020-01-01&to=2026-01-01", 400);
    }

    /** NFR01, BR03: nhân viên không xem được; quản lý không được phân công quầy cũng không xem được. */
    @Test
    void statsAreLimitedToTheVendorsOwnManager() throws Exception {
        long vendor = jdbc.sql("SELECT id FROM vendor WHERE name = :n").param("n", LUNCH_VENDOR)
                .query(Long.class).single();
        String staff = login("staff@canteen.local");
        stats(staff, "vendorId=" + vendor, 403);

        String email = "mgr." + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        call(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"fullName\":\"QL khác\",\"email\":\"" + email + "\",\"password\":\"Matkhau123\"}"), 201);
        postJson("/api/admin/users/grant-manager", login("admin@canteen.local"), "{\"email\":\"" + email + "\"}", 200);
        String stranger = login(email, "Matkhau123");
        assertThat(stats(stranger, "vendorId=" + vendor, 403).get("code").asText()).isEqualTo("FORBIDDEN");
    }

    // ---- UC10.7 ----

    @Test
    void adminOverviewAggregatesAllVendorsAndMatchesVendorStats() throws Exception {
        String staff = login("staff@canteen.local");
        String admin = login("admin@canteen.local");
        MenuFixture fx = seedScenario(staff);
        LocalDate day = menuDate(fx);

        JsonNode overview = call(auth(get("/api/admin/stats/overview?from=" + day + "&to=" + day), admin), 200);
        JsonNode row = null;
        for (JsonNode v : overview.get("vendors")) {
            if (v.get("vendorId").asLong() == fx.vendorId()) {
                row = v;
            }
        }
        assertThat(row).as("quầy có trong báo cáo").isNotNull();
        assertThat(row.get("orders").asLong()).isEqualTo(4);
        assertThat(row.get("completedPaid").asLong()).isEqualTo(1);
        assertThat(row.get("cancelledOrExpired").asLong()).isEqualTo(1);
        assertThat(row.get("notCollected").asLong()).isEqualTo(1);
        assertThat(row.get("netRevenue").asLong()).isEqualTo(50_000);
        assertThat(overview.get("totals").get("netRevenue").asLong()).isGreaterThanOrEqualTo(50_000);
        assertThat(overview.get("indicators").get("activeVendors").asLong()).isGreaterThanOrEqualTo(2);

        JsonNode detail = call(auth(get("/api/admin/stats/vendors/" + fx.vendorId() + "?from=" + day + "&to=" + day),
                admin), 200);
        assertThat(detail.get("revenue").get("netRevenue").asLong()).isEqualTo(row.get("netRevenue").asLong());

        // quản lý không vào được báo cáo toàn hệ thống
        call(auth(get("/api/admin/stats/overview"), login("manager@canteen.local")), 403);
    }
}
