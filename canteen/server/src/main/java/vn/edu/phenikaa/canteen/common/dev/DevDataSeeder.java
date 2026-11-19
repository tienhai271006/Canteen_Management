package vn.edu.phenikaa.canteen.common.dev;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Chỉ chạy ở profile dev, sau {@link DevAccountSeeder}. Thêm dữ liệu mẫu để MỌI tài khoản thử có thứ để xem:
 * lịch sử 6 ngày (hoàn tất, hủy, hết hạn, không nhận) cho thống kê và đơn của khách, đơn đang xử lý hôm nay cho bảng quầy,
 * yêu cầu hủy và báo đến muộn đang chờ, ghi nhận hao hụt, khoản bù của nền tảng và thông báo cho từng tài khoản.
 * Idempotent: nhận ra dữ liệu đã tạo qua khóa chống nhấn hai lần bắt đầu bằng {@code dev-seed-}.
 * Số suất đã giữ của món và của khung được tính lại từ đơn, nên các ràng buộc hạn mức vẫn đúng.
 */
@Component
@Order(2)
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final String[] PAST_STATUS = {"COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED",
            "CANCELLED", "NOT_COLLECTED", "EXPIRED"};
    private static final String[] VENDORS = {"Cơm Nhà Cô Lan", "Bún Phở Ba Sao"};
    private static final String STUDENT = "sv@phenikaa-uni.edu.vn";
    private static final String GUEST = "guest@gmail.com";

    private final JdbcClient jdbc;
    private final Clock clock;
    private int seq;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Integer done = jdbc.sql("SELECT COUNT(*) FROM orders WHERE idempotency_key LIKE 'dev-seed-%'")
                .query(Integer.class).single();
        if (done > 0 || id("SELECT id FROM user_account WHERE email = :e", STUDENT) == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        for (String v : VENDORS) {
            Long vendor = id("SELECT id FROM vendor WHERE name = :e AND status = 'ACTIVE'", v);
            if (vendor == null) {
                continue;
            }
            for (int d = 6; d >= 1; d--) {
                pastDay(vendor, today.minusDays(d), d);
            }
        }
        liveToday(today, now);
        recomputeReserved(today.minusDays(6), today);
        wasteAndMoney(today);
        notifications(now);
        log.info("Dev: đã thêm đơn mẫu ({} đơn), hao hụt, khoản bù và thông báo cho mọi tài khoản thử", seq);
    }

    // ---------------------------------------------------------------- lịch sử

    private void pastDay(Long vendor, LocalDate date, int d) {
        Long menu = ensureMenu(vendor, date);
        List<Long> dishes = menuDishes(menu);
        List<Long> slots = jdbc.sql("SELECT id FROM pickup_slot WHERE menu_id = :m ORDER BY start_time")
                .param("m", menu).query(Long.class).list();
        for (int i = 0; i < 4; i++) {
            String status = PAST_STATUS[(d * 3 + i) % PAST_STATUS.length];
            String customer = i % 2 == 0 ? STUDENT : GUEST;
            List<long[]> items = new ArrayList<>();                         // {dishId, qty}
            items.add(new long[] {dishes.get(i % dishes.size()), 1 + (d + i) % 2});
            if (i % 2 == 0 && dishes.size() > 1) {
                items.add(new long[] {dishes.get((i + 1) % dishes.size()), 1});
            }
            boolean discounted = "COMPLETED".equals(status) && customer.equals(STUDENT) && d % 2 == 0;
            LocalDateTime created = date.minusDays(1).atTime(18, 0).plusMinutes(7L * (i + d));
            order(customer, vendor, menu, slots.get(i % slots.size()), status, items, discounted, date, created);
        }
    }

    // ---------------------------------------------------------------- hôm nay (bảng quầy)

    private void liveToday(LocalDate today, LocalDateTime now) {
        Long coLan = id("SELECT id FROM vendor WHERE name = :e", VENDORS[0]);
        Long baSao = id("SELECT id FROM vendor WHERE name = :e", VENDORS[1]);
        if (coLan != null) {
            Long menu = ensureMenu(coLan, today);
            List<Long> slots = jdbc.sql("SELECT id FROM pickup_slot WHERE menu_id = :m ORDER BY start_time")
                    .param("m", menu).query(Long.class).list();
            List<Long> dishes = menuDishes(menu);
            String[] live = {"PENDING", "CONFIRMED", "PREPARING", "READY", "COMPLETED"};
            Long[] ids = new Long[live.length];
            for (int i = 0; i < live.length; i++) {
                List<long[]> items = new ArrayList<>(List.of(new long[] {dishes.get(i % dishes.size()), 1 + i % 2}));
                ids[i] = order(i % 2 == 0 ? STUDENT : GUEST, coLan, menu, slots.get(i % slots.size()), live[i], items,
                        false, today, now.minusMinutes(50 - 8L * i));
            }
            Long guest = id("SELECT id FROM user_account WHERE email = :e", GUEST);
            Long student = id("SELECT id FROM user_account WHERE email = :e", STUDENT);
            jdbc.sql("INSERT INTO cancel_request (order_id, requested_by, reason, status) VALUES (:o, :u, :r, 'PENDING')")
                    .param("o", ids[1]).param("u", guest).param("r", "Em có lịch học đột xuất, không kịp lấy cơm.").update();
            jdbc.sql("INSERT INTO late_arrival_request (order_id, requested_by, reason, proposed_arrival_at, status) "
                            + "VALUES (:o, :u, :r, :t, 'PENDING')")
                    .param("o", ids[2]).param("u", student).param("r", "Tan lớp muộn 15 phút.")
                    .param("t", now.plusMinutes(30)).update();
        }
        if (baSao != null) {
            Long menu = ensureMenu(baSao, today);
            Long slot = id("SELECT id FROM pickup_slot WHERE menu_id = :e ORDER BY start_time LIMIT 1", menu);
            Long dish = menuDishes(menu).get(0);
            order(GUEST, baSao, menu, slot, "CONFIRMED", List.of(new long[] {dish, 1}), false, today, now.minusMinutes(20));
        }
    }

    // ---------------------------------------------------------------- hao hụt, khoản bù, thông báo

    private void wasteAndMoney(LocalDate today) {
        Long manager = id("SELECT id FROM user_account WHERE email = :e", "manager@canteen.local");
        Long admin = id("SELECT id FROM user_account WHERE email = :e", "admin@canteen.local");
        Long coLan = id("SELECT id FROM vendor WHERE name = :e", VENDORS[0]);
        List<long[]> nc = jdbc.sql("""
                SELECT o.id, oi.dish_id, oi.quantity FROM orders o JOIN order_item oi ON oi.order_id = o.id
                 WHERE o.vendor_id = :v AND o.status = 'NOT_COLLECTED' AND o.idempotency_key LIKE 'dev-seed-%'
                 ORDER BY o.id, oi.id""").param("v", coLan)
                .query((rs, n) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3)}).list();
        for (int i = 0; i < nc.size() && i < 2; i++) {          // hai đơn đầu đã xử lý, các đơn còn lại để "chưa xử lý"
            jdbc.sql("INSERT INTO waste_record (order_id, dish_id, quantity, outcome, note, recorded_by) "
                            + "VALUES (:o, :d, :q, :out, :n, :u)")
                    .param("o", nc.get(i)[0]).param("d", nc.get(i)[1]).param("q", nc.get(i)[2])
                    .param("out", i == 0 ? "RESOLD" : "WASTED")
                    .param("n", i == 0 ? "Bán lại cho khách tại quầy" : "Hết giờ, đổ bỏ").param("u", manager).update();
        }
        for (int d : new int[] {5, 3}) {
            jdbc.sql("INSERT INTO platform_reimbursement (vendor_id, for_date, amount, note, recorded_by) "
                            + "VALUES (:v, :d, :a, :n, :u)")
                    .param("v", coLan).param("d", today.minusDays(d)).param("a", 15000L * d)
                    .param("n", "Dev: nền tảng bù ưu đãi").param("u", admin).update();
        }
    }

    private void notifications(LocalDateTime now) {
        note("admin@canteen.local", "RESTRICTION_REVIEW_NEEDED", "Có hạn chế cần xem lại",
                "Dev: một ghi nhận bị sửa, hạn chế liên quan cần được xem lại.", now.minusHours(3));
        note("manager@canteen.local", "CANCEL_REQUEST_RECEIVED", "Yêu cầu hủy mới",
                "Dev: khách xin hủy một đơn của hôm nay, vào Đơn hàng để xử lý.", now.minusMinutes(30));
        note("staff@canteen.local", "LATE_ARRIVAL_RECEIVED", "Khách báo đến muộn",
                "Dev: có đơn báo đến muộn 15 phút đang chờ quầy trả lời.", now.minusMinutes(25));
        note(STUDENT, "ORDER_READY", "Đơn của bạn đã sẵn sàng", "Dev: mời bạn đến quầy nhận món.", now.minusMinutes(10));
        note(GUEST, "ORDER_CONFIRMED", "Quầy đã xác nhận đơn", "Dev: đơn của bạn đã được xác nhận.", now.minusMinutes(40));
    }

    private void note(String email, String type, String title, String body, LocalDateTime at) {
        Long u = id("SELECT id FROM user_account WHERE email = :e", email);
        if (u == null) {
            return;
        }
        jdbc.sql("INSERT INTO notification (user_id, type, title, body, event_key, created_at) "
                        + "VALUES (:u, :t, :ti, :b, :k, :c)")
                .param("u", u).param("t", type).param("ti", title).param("b", body)
                .param("k", "dev-seed-" + email + "-" + type).param("c", at).update();
    }

    // ---------------------------------------------------------------- nền

    /** Tạo thực đơn trưa đã công bố cho ngày đó (kèm khung nhận, món, suất) nếu chưa có; trả về id. */
    private Long ensureMenu(Long vendor, LocalDate date) {
        Long menu = jdbc.sql("SELECT id FROM menu WHERE vendor_id = :v AND menu_date = :d AND meal_session = 'LUNCH'")
                .param("v", vendor).param("d", date).query(Long.class).optional().orElse(null);
        if (menu != null) {
            return menu;
        }
        jdbc.sql("INSERT INTO menu (vendor_id, menu_date, meal_session, order_cutoff_time, status) "
                + "VALUES (:v, :d, 'LUNCH', '10:30:00', 'PUBLISHED')").param("v", vendor).param("d", date).update();
        menu = id("SELECT id FROM menu WHERE vendor_id = :e AND menu_date = '" + date + "' AND meal_session = 'LUNCH'", vendor);
        jdbc.sql("INSERT INTO pickup_slot (menu_id, start_time, end_time) VALUES (:m, '11:00:00', '11:30:00'), "
                + "(:m, '11:30:00', '12:00:00'), (:m, '12:00:00', '12:30:00')").param("m", menu).update();
        jdbc.sql("INSERT INTO menu_item (menu_id, dish_id) SELECT :m, id FROM dish WHERE vendor_id = :v")
                .param("m", menu).param("v", vendor).update();
        jdbc.sql("INSERT INTO quota (menu_item_id, total_slots, reserved_slots, counter_slots) "
                + "SELECT id, 30, 0, 5 FROM menu_item WHERE menu_id = :m").param("m", menu).update();
        return menu;
    }

    /** Thêm một đơn kèm dòng món và lịch sử trạng thái; trả về id đơn. */
    private Long order(String email, Long vendor, Long menu, Long slot, String status, List<long[]> items,
                       boolean discounted, LocalDate serviceDate, LocalDateTime created) {
        Long customer = id("SELECT id FROM user_account WHERE email = :e", email);
        long subtotal = 0;
        List<Object[]> lines = new ArrayList<>();                            // {dishId, menuItemId, name, price, qty}
        for (long[] it : items) {
            var row = jdbc.sql("SELECT d.name, d.price, mi.id FROM dish d JOIN menu_item mi ON mi.dish_id = d.id "
                            + "WHERE d.id = :d AND mi.menu_id = :m").param("d", it[0]).param("m", menu)
                    .query((rs, n) -> new Object[] {rs.getString(1), rs.getLong(2), rs.getLong(3)}).single();
            subtotal += (Long) row[1] * it[1];
            lines.add(new Object[] {it[0], row[2], row[0], row[1], it[1]});
        }
        long discount = discounted ? Math.min(30000, subtotal / 2) : 0;
        boolean completed = "COMPLETED".equals(status);
        String code = "DEV" + serviceDate.toString().replace("-", "") + "-" + String.format("%03d", ++seq);
        boolean closed = completed || "NOT_COLLECTED".equals(status) || "CANCELLED".equals(status) || "EXPIRED".equals(status);
        LocalDateTime at = serviceDate.atTime(11, 15).plusMinutes(seq % 40);
        jdbc.sql("""
                INSERT INTO orders (order_code, customer_id, vendor_id, pickup_slot_id, status, payment_status,
                                    subtotal_amount, discount_amount, total_amount, idempotency_key, pending_expires_at,
                                    confirmed_at, ready_at, pickup_deadline, completed_at, closed_reason, created_at)
                VALUES (:code, :c, :v, :s, :st, :ps, :sub, :disc, :tot, :key, :pe, :cf, :rd, :pd, :cp, :cr, :cr8)""")
                .param("code", code).param("c", customer).param("v", vendor).param("s", slot).param("st", status)
                .param("ps", completed ? "PAID" : "UNPAID").param("sub", subtotal).param("disc", discount)
                .param("tot", subtotal - discount).param("key", "dev-seed-" + code)
                .param("pe", "PENDING".equals(status) ? LocalDateTime.now(clock).plusDays(1) : null)
                .param("cf", "PENDING".equals(status) || "EXPIRED".equals(status) ? null : created.plusMinutes(2))
                .param("rd", List.of("READY", "COMPLETED", "NOT_COLLECTED").contains(status) ? at : null)
                .param("pd", List.of("READY", "NOT_COLLECTED").contains(status) ? at.plusMinutes(30) : null)
                .param("cp", completed ? at.plusMinutes(5) : null)
                .param("cr", switch (status) {
                    case "CANCELLED" -> "Khách hủy đơn";
                    case "EXPIRED" -> "Quá hạn xác nhận";
                    case "NOT_COLLECTED" -> "Quá hạn nhận món";
                    default -> null;
                })
                .param("cr8", created).update();
        Long orderId = id("SELECT id FROM orders WHERE order_code = :e", code);
        for (Object[] l : lines) {
            jdbc.sql("INSERT INTO order_item (order_id, menu_item_id, dish_id, dish_name, unit_price, quantity, line_total) "
                            + "VALUES (:o, :mi, :d, :n, :p, :q, :lt)")
                    .param("o", orderId).param("mi", l[1]).param("d", l[0]).param("n", l[2]).param("p", l[3])
                    .param("q", l[4]).param("lt", (Long) l[3] * (Long) l[4]).update();
        }
        history(orderId, customer, status, created, closed);
        return orderId;
    }

    private void history(Long orderId, Long customer, String finalStatus, LocalDateTime t, boolean closed) {
        List<String> path = switch (finalStatus) {
            case "PENDING" -> List.of("PENDING");
            case "CONFIRMED" -> List.of("PENDING", "CONFIRMED");
            case "PREPARING" -> List.of("PENDING", "CONFIRMED", "PREPARING");
            case "READY" -> List.of("PENDING", "CONFIRMED", "PREPARING", "READY");
            case "COMPLETED" -> List.of("PENDING", "CONFIRMED", "PREPARING", "READY", "COMPLETED");
            case "NOT_COLLECTED" -> List.of("PENDING", "CONFIRMED", "PREPARING", "READY", "NOT_COLLECTED");
            case "CANCELLED" -> List.of("PENDING", "CONFIRMED", "CANCELLED");
            default -> List.of("PENDING", "EXPIRED");                          // EXPIRED
        };
        String from = null;
        int i = 0;
        for (String to : path) {
            boolean system = "EXPIRED".equals(to) || "NOT_COLLECTED".equals(to);
            boolean customerAct = i == 0 || "CANCELLED".equals(to);
            jdbc.sql("INSERT INTO order_status_history (order_id, from_status, to_status, actor_id, actor_type, reason, created_at) "
                            + "VALUES (:o, :f, :t, :a, :at, :r, :c)")
                    .param("o", orderId).param("f", from).param("t", to)
                    .param("a", system ? null : customerAct ? customer : null)
                    .param("at", system ? "SYSTEM" : customerAct ? "CUSTOMER" : "STAFF")
                    .param("r", "Dev seed").param("c", t.plusMinutes(3L * i)).update();
            from = to;
            i++;
        }
    }

    /** Đồng bộ số suất đã giữ của món và của khung với các đơn vừa thêm (trừ đơn hủy, hết hạn), giống cách V11 làm. */
    private void recomputeReserved(LocalDate from, LocalDate to) {
        jdbc.sql("""
                UPDATE quota q SET q.reserved_slots = COALESCE((SELECT SUM(oi.quantity) FROM order_item oi
                    JOIN orders o ON o.id = oi.order_id
                   WHERE oi.menu_item_id = q.menu_item_id AND o.status NOT IN ('CANCELLED', 'EXPIRED')), 0)
                 WHERE q.menu_item_id IN (SELECT mi.id FROM menu_item mi JOIN menu m ON m.id = mi.menu_id
                                           WHERE m.menu_date BETWEEN :f AND :t)""")
                .param("f", from).param("t", to).update();
        jdbc.sql("""
                UPDATE pickup_slot s SET s.reserved_qty = COALESCE((SELECT SUM(oi.quantity) FROM order_item oi
                    JOIN orders o ON o.id = oi.order_id
                   WHERE o.pickup_slot_id = s.id AND o.status NOT IN ('CANCELLED', 'EXPIRED')), 0)
                 WHERE s.menu_id IN (SELECT m.id FROM menu m WHERE m.menu_date BETWEEN :f AND :t)""")
                .param("f", from).param("t", to).update();
    }

    /** Các món có trong thực đơn, theo thứ tự thêm; đơn mẫu chỉ chọn trong đây để luôn có dòng menu_item hợp lệ. */
    private List<Long> menuDishes(Long menu) {
        return jdbc.sql("SELECT dish_id FROM menu_item WHERE menu_id = :m ORDER BY id").param("m", menu)
                .query(Long.class).list();
    }

    private Long id(String sql, Object arg) {
        return jdbc.sql(sql).param("e", arg).query(Long.class).optional().orElse(null);
    }
}
