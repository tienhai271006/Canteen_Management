package vn.edu.phenikaa.canteen.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.phenikaa.canteen.order.OrderTestFixtures.createMenu;
import static vn.edu.phenikaa.canteen.order.OrderTestFixtures.registerCustomer;
import static vn.edu.phenikaa.canteen.order.OrderTestFixtures.withJson;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.model.Order;
import vn.edu.phenikaa.canteen.model.OrderStatus;
import vn.edu.phenikaa.canteen.order.OrderTestFixtures.MenuFixture;
import vn.edu.phenikaa.canteen.repository.OrderRepository;
import vn.edu.phenikaa.canteen.service.Actor;
import vn.edu.phenikaa.canteen.service.OrderService;

/** Luồng giỏ hàng và đặt đơn (UC5). Cần Docker (Testcontainers MySQL); không có Docker thì test tự bỏ qua. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class OrderFlowTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    private static final String LUNCH_VENDOR = "Cơm Nhà Cô Lan";
    private static final String OTHER_VENDOR = "Bún Phở Ba Sao";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcClient jdbc;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orders;
    @Autowired TransactionTemplate tx;
    @MockBean JavaMailSender mailSender;

    private void addToCart(String token, long menuItemId, int qty, int expectedStatus) throws Exception {
        mvc.perform(withJson(post("/api/cart/items"), token,
                        "{\"menuItemId\":" + menuItemId + ",\"quantity\":" + qty + "}"))
                .andExpect(status().is(expectedStatus));
    }

    private JsonNode place(String token, long slotId, String key, int expectedStatus) throws Exception {
        var req = withJson(post("/api/orders"), token, "{\"pickupSlotId\":" + slotId + "}");
        if (key != null) {
            req.header("Idempotency-Key", key);
        }
        String body = mvc.perform(req).andExpect(status().is(expectedStatus)).andReturn().getResponse()
                .getContentAsString();
        return om.readTree(body);
    }

    private int reserved(long menuItemId) {
        return jdbc.sql("SELECT reserved_slots FROM quota WHERE menu_item_id = :i").param("i", menuItemId)
                .query(Integer.class).single();
    }

    private long historyCount(long orderId) {
        return jdbc.sql("SELECT COUNT(*) FROM order_status_history WHERE order_id = :o").param("o", orderId)
                .query(Long.class).single();
    }

    /** Đặt đơn thành công, giá chốt lúc đặt (BR04), gửi lại cùng khóa thì trả đúng đơn cũ (BR36). */
    @Test
    void placeOrderSnapshotsPriceAndIsIdempotent() throws Exception {
        String token = registerCustomer(mvc, om);
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 2, 30_000);
        addToCart(token, fx.menuItemId(), 2, 200);

        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(60_000))
                .andExpect(jsonPath("$.items.length()").value(1));

        String key = UUID.randomUUID().toString();
        JsonNode created = place(token, fx.slotId(), key, 201);
        long orderId = created.get("id").asLong();
        assertThat(created.get("status").asText()).isEqualTo("PENDING");
        assertThat(created.get("paymentStatus").asText()).isEqualTo("UNPAID");
        assertThat(created.get("totalAmount").asLong()).isEqualTo(60_000);
        assertThat(created.get("items").get(0).get("unitPrice").asLong()).isEqualTo(30_000);
        assertThat(created.has("pendingExpiresAt")).isTrue();
        assertThat(reserved(fx.menuItemId())).isEqualTo(2);
        assertThat(historyCount(orderId)).isEqualTo(1);

        JsonNode replay = place(token, fx.slotId(), key, 200);
        assertThat(replay.get("id").asLong()).isEqualTo(orderId);
        assertThat(reserved(fx.menuItemId())).as("gửi lại không giữ thêm suất").isEqualTo(2);
        long orderCount = jdbc.sql("SELECT COUNT(*) FROM orders WHERE pickup_slot_id = :s").param("s", fx.slotId())
                .query(Long.class).single();
        assertThat(orderCount).isEqualTo(1);

        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.items.length()").value(0));

        jdbc.sql("UPDATE dish SET price = 99000 WHERE id = :d").param("d", fx.dishId()).update();
        mvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(60_000))
                .andExpect(jsonPath("$.items[0].unitPrice").value(30_000));
    }

    @Test
    void missingIdempotencyKeyAndEmptyCartAreRejected() throws Exception {
        String token = registerCustomer(mvc, om);
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 5, 0, 20_000);

        JsonNode noKey = place(token, fx.slotId(), null, 400);
        assertThat(noKey.get("code").asText()).isEqualTo("VALIDATION_FAILED");

        JsonNode empty = place(token, fx.slotId(), UUID.randomUUID().toString(), 409);
        assertThat(empty.get("code").asText()).isEqualTo("CART_EMPTY");
    }

    @Test
    void cartHoldsOneVendorAndOneMenuOnly() throws Exception {
        String token = registerCustomer(mvc, om);
        MenuFixture a = createMenu(jdbc, LUNCH_VENDOR, 5, 0, 20_000);
        MenuFixture sameVendorOtherMenu = createMenu(jdbc, LUNCH_VENDOR, 5, 0, 20_000);
        MenuFixture b = createMenu(jdbc, OTHER_VENDOR, 5, 0, 20_000);

        addToCart(token, a.menuItemId(), 1, 200);
        mvc.perform(withJson(post("/api/cart/items"), token,
                        "{\"menuItemId\":" + b.menuItemId() + ",\"quantity\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CART_MULTI_VENDOR"));
        mvc.perform(withJson(post("/api/cart/items"), token,
                        "{\"menuItemId\":" + sameVendorOtherMenu.menuItemId() + ",\"quantity\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CART_MULTI_MENU"));

        mvc.perform(delete("/api/cart").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        addToCart(token, b.menuItemId(), 1, 200);
    }

    /** BR11, BR12: thiếu suất thì đơn thất bại, suất không bị giữ, giỏ còn nguyên; phần dành cho quầy không bán online. */
    @Test
    void notEnoughQuotaFailsWholeOrderAndKeepsCart() throws Exception {
        String token = registerCustomer(mvc, om);
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 5, 3, 20_000);     // online còn 2
        addToCart(token, fx.menuItemId(), 3, 200);                          // giỏ không giữ suất nên vẫn thêm được

        JsonNode err = place(token, fx.slotId(), UUID.randomUUID().toString(), 409);
        assertThat(err.get("code").asText()).isEqualTo("QUOTA_EXCEEDED");
        assertThat(reserved(fx.menuItemId())).isZero();
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].problem").value("QUOTA_EXCEEDED"));
    }

    /** BR14: hủy trả suất đúng một lần; BR15: không nhận món thì không tự cộng lại suất. */
    @Test
    void cancelReleasesQuotaExactlyOnceButNotCollectedDoesNot() throws Exception {
        String token = registerCustomer(mvc, om);
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 10, 0, 20_000);

        addToCart(token, fx.menuItemId(), 2, 200);
        long cancelled = place(token, fx.slotId(), UUID.randomUUID().toString(), 201).get("id").asLong();
        assertThat(reserved(fx.menuItemId())).isEqualTo(2);

        tx.executeWithoutResult(s -> {
            Order o = orders.findById(cancelled).orElseThrow();
            orderService.transition(o, OrderStatus.CANCELLED, Actor.system(), "test");
        });
        assertThat(reserved(fx.menuItemId())).isZero();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            Order o = orders.findById(cancelled).orElseThrow();
            orderService.transition(o, OrderStatus.CANCELLED, Actor.system(), "lần hai");
        })).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ORDER_TRANSITION));
        assertThat(reserved(fx.menuItemId())).as("không trả suất lần hai").isZero();
        assertThat(historyCount(cancelled)).isEqualTo(2);

        addToCart(token, fx.menuItemId(), 3, 200);
        long missed = place(token, fx.slotId(), UUID.randomUUID().toString(), 201).get("id").asLong();
        tx.executeWithoutResult(s -> {
            Order o = orders.findById(missed).orElseThrow();
            Actor staff = Actor.system();
            orderService.transition(o, OrderStatus.CONFIRMED, staff, null);
            orderService.transition(o, OrderStatus.PREPARING, staff, null);
            orderService.transition(o, OrderStatus.READY, staff, null);
            orderService.transition(o, OrderStatus.NOT_COLLECTED, staff, "khách không đến");
        });
        assertThat(reserved(fx.menuItemId())).as("món đã làm, không tự cộng suất").isEqualTo(3);
    }

    @Test
    void customersCannotReadEachOthersOrders() throws Exception {
        String owner = registerCustomer(mvc, om);
        String stranger = registerCustomer(mvc, om);
        MenuFixture fx = createMenu(jdbc, LUNCH_VENDOR, 5, 0, 20_000);
        addToCart(owner, fx.menuItemId(), 1, 200);
        long id = place(owner, fx.slotId(), UUID.randomUUID().toString(), 201).get("id").asLong();

        mvc.perform(get("/api/orders/" + id).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }
}
