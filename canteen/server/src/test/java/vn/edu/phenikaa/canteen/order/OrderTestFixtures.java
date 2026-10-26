package vn.edu.phenikaa.canteen.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import vn.edu.phenikaa.canteen.common.config.TimeConfig;

/** Dựng dữ liệu cho test đặt đơn. Mỗi lần gọi {@link #createMenu} dùng một ngày riêng nên các test không dẫm nhau. */
final class OrderTestFixtures {

    private static final AtomicInteger NEXT_DAY = new AtomicInteger(5);

    private OrderTestFixtures() {
    }

    /** Một thực đơn đã công bố với một món, một khung nhận và hạn mức. */
    record MenuFixture(long vendorId, long menuId, long slotId, long menuItemId, long dishId) {
    }

    static String registerCustomer(MockMvc mvc, ObjectMapper om) throws Exception {
        String email = "sv." + UUID.randomUUID().toString().substring(0, 8) + "@phenikaa-uni.edu.vn";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Khách thử","email":"%s","password":"Matkhau123"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("accessToken").asText();
    }

    static MenuFixture createMenu(JdbcClient jdbc, String vendorName, int totalSlots, int counterSlots, long price) {
        long vendorId = jdbc.sql("SELECT id FROM vendor WHERE name = :n").param("n", vendorName)
                .query(Long.class).single();
        LocalDate date = LocalDate.now(TimeConfig.ZONE).plusDays(NEXT_DAY.getAndIncrement());

        long menuId = insert(jdbc, "INSERT INTO menu (vendor_id, menu_date, meal_session, order_cutoff_time, status) "
                + "VALUES (:vendorId, :date, 'LUNCH', '10:30:00', 'PUBLISHED')",
                Map.of("vendorId", vendorId, "date", date));
        long dishId = insert(jdbc, "INSERT INTO dish (vendor_id, name, category, price) "
                + "VALUES (:vendorId, :name, 'MAIN', :price)",
                Map.of("vendorId", vendorId, "name", "Món thử " + UUID.randomUUID(), "price", price));
        long menuItemId = insert(jdbc, "INSERT INTO menu_item (menu_id, dish_id) VALUES (:menuId, :dishId)",
                Map.of("menuId", menuId, "dishId", dishId));
        jdbc.sql("INSERT INTO quota (menu_item_id, total_slots, reserved_slots, counter_slots) VALUES (:i, :t, 0, :c)")
                .param("i", menuItemId).param("t", totalSlots).param("c", counterSlots).update();
        long slotId = insert(jdbc, "INSERT INTO pickup_slot (menu_id, start_time, end_time) "
                + "VALUES (:menuId, '11:00:00', '11:30:00')", Map.of("menuId", menuId));
        return new MenuFixture(vendorId, menuId, slotId, menuItemId, dishId);
    }

    private static long insert(JdbcClient jdbc, String sql, Map<String, Object> params) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql(sql).params(params).update(keys);
        return keys.getKey().longValue();
    }

    static MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder req, String token, String json) {
        return req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json);
    }
}
