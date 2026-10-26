package vn.edu.phenikaa.canteen.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static vn.edu.phenikaa.canteen.order.OrderTestFixtures.createMenu;
import static vn.edu.phenikaa.canteen.order.OrderTestFixtures.registerCustomer;
import static vn.edu.phenikaa.canteen.order.OrderTestFixtures.withJson;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.edu.phenikaa.canteen.order.OrderTestFixtures.MenuFixture;

/**
 * NFR02, BR12: không bán vượt suất dù nhiều request cùng lúc. Cần Docker (Testcontainers MySQL).
 * Các request chạy thật song song qua MockMvc, mỗi khách một token và một khóa idempotency riêng.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class OrderConcurrencyTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcClient jdbc;
    @MockBean JavaMailSender mailSender;

    private record Result(int status, String code) {
    }

    /** Chuẩn bị mỗi khách một giỏ có {@code qty} suất, rồi cho tất cả đặt đơn cùng một lúc. */
    private List<Result> raceToOrder(MenuFixture fx, int customers, int qty) throws Exception {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < customers; i++) {
            String token = registerCustomer(mvc, om);
            mvc.perform(withJson(post("/api/cart/items"), token,
                    "{\"menuItemId\":" + fx.menuItemId() + ",\"quantity\":" + qty + "}"));
            tokens.add(token);
        }

        ExecutorService pool = Executors.newFixedThreadPool(customers);
        CyclicBarrier start = new CyclicBarrier(customers);
        try {
            List<Future<Result>> futures = new ArrayList<>();
            for (String token : tokens) {
                Callable<Result> task = () -> {
                    start.await();
                    var res = mvc.perform(withJson(post("/api/orders"), token, "{\"pickupSlotId\":" + fx.slotId() + "}")
                            .header("Idempotency-Key", UUID.randomUUID().toString())).andReturn().getResponse();
                    String body = res.getContentAsString();
                    JsonNode json = body.isBlank() ? null : om.readTree(body);
                    String code = json != null && json.has("code") ? json.get("code").asText() : null;
                    return new Result(res.getStatus(), code);
                };
                futures.add(pool.submit(task));
            }
            List<Result> results = new ArrayList<>();
            for (Future<Result> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private int reserved(MenuFixture fx) {
        return jdbc.sql("SELECT reserved_slots FROM quota WHERE menu_item_id = :i").param("i", fx.menuItemId())
                .query(Integer.class).single();
    }

    private long orderCount(MenuFixture fx) {
        return jdbc.sql("SELECT COUNT(*) FROM orders WHERE pickup_slot_id = :s").param("s", fx.slotId())
                .query(Long.class).single();
    }

    /** Tiêu chí của Pha 6: hai request tranh suất cuối thì chỉ một request thành công. */
    @Test
    void twoRequestsRaceForTheLastSlotOnlyOneWins() throws Exception {
        MenuFixture fx = createMenu(jdbc, "Cơm Nhà Cô Lan", 1, 0, 30_000);

        List<Result> results = raceToOrder(fx, 2, 1);

        assertThat(results).filteredOn(r -> r.status() == 201).hasSize(1);
        assertThat(results).filteredOn(r -> r.status() == 409 && "QUOTA_EXCEEDED".equals(r.code())).hasSize(1);
        assertThat(reserved(fx)).isEqualTo(1);
        assertThat(orderCount(fx)).isEqualTo(1);
    }

    /** Nhiều người hơn số suất: số đơn thành công đúng bằng số suất online, không hơn. */
    @Test
    void manyCustomersNeverOversell() throws Exception {
        MenuFixture fx = createMenu(jdbc, "Cơm Nhà Cô Lan", 5, 2, 30_000);      // online chỉ còn 3

        List<Result> results = raceToOrder(fx, 8, 1);

        assertThat(results).filteredOn(r -> r.status() == 201).hasSize(3);
        assertThat(results).filteredOn(r -> r.status() != 201)
                .allSatisfy(r -> assertThat(r.code()).isEqualTo("QUOTA_EXCEEDED"));
        assertThat(reserved(fx)).isEqualTo(3);
        assertThat(orderCount(fx)).isEqualTo(3);
    }

    /** BR36: hai request cùng khóa chạy song song chỉ tạo một đơn và chỉ giữ suất một lần. */
    @Test
    void sameIdempotencyKeyInParallelCreatesOneOrder() throws Exception {
        MenuFixture fx = createMenu(jdbc, "Cơm Nhà Cô Lan", 10, 0, 30_000);
        String token = registerCustomer(mvc, om);
        mvc.perform(withJson(post("/api/cart/items"), token,
                "{\"menuItemId\":" + fx.menuItemId() + ",\"quantity\":2}"));
        String key = UUID.randomUUID().toString();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(withJson(post("/api/orders"), token, "{\"pickupSlotId\":" + fx.slotId() + "}")
                            .header("Idempotency-Key", key)).andReturn().getResponse().getStatus();
                }));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
            assertThat(statuses).as("một request tạo đơn, request còn lại nhận lại đơn đó hoặc xung đột")
                    .contains(201).doesNotContain(500);
        } finally {
            pool.shutdownNow();
        }
        assertThat(orderCount(fx)).isEqualTo(1);
        assertThat(reserved(fx)).isEqualTo(2);
    }
}
