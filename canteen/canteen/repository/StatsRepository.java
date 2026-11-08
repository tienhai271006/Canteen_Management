package vn.edu.phenikaa.canteen.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.dto.AdminOverviewResponse;
import vn.edu.phenikaa.canteen.model.MealSession;

/**
 * Truy vấn tổng hợp cho thống kê (UC9, UC10.7). Dùng SQL thuần vì là GROUP BY nhiều bảng; toàn bộ là SELECT chỉ đọc.
 * Mốc thời gian là NGÀY PHỤC VỤ (menu.menu_date), đi từ orders qua pickup_slot sang menu. Lọc theo
 * (vendor_id, menu_date) dùng chỉ mục uk_menu_vendor_date_session, nên không quét cả bảng đơn.
 */
@Repository
@RequiredArgsConstructor
public class StatsRepository {

    public record OrderCount(LocalDate date, String session, String status, long orders) {
    }

    public record RevenueSlice(LocalDate date, String session, long orders, long gross, long discount, long net,
                               long discounted) {
    }

    public record DishTotals(Long dishId, String name, long ordered, long sold, long cancelled, long notCollected,
                             long soldGross) {
    }

    public record KeyCount(String key, long count) {
    }

    private static final String FROM_ORDERS = " FROM orders o JOIN pickup_slot ps ON ps.id = o.pickup_slot_id "
            + "JOIN menu m ON m.id = ps.menu_id ";

    private final JdbcClient jdbc;

    private static String period(MealSession session) {
        return " m.menu_date BETWEEN :from AND :to" + (session == null ? "" : " AND m.meal_session = :session");
    }

    private JdbcClient.StatementSpec bind(String sql, Long vendorId, LocalDate from, LocalDate to, MealSession session) {
        JdbcClient.StatementSpec spec = jdbc.sql(sql).param("from", from).param("to", to);
        if (vendorId != null) {
            spec = spec.param("v", vendorId);
        }
        return session == null ? spec : spec.param("session", session.name());
    }

    // ---- UC9.1 ----

    public List<OrderCount> orderCounts(Long vendorId, LocalDate from, LocalDate to, MealSession session) {
        String sql = "SELECT m.menu_date AS d, m.meal_session AS s, o.status AS st, COUNT(*) AS n" + FROM_ORDERS
                + "WHERE o.vendor_id = :v AND" + period(session)
                + " GROUP BY m.menu_date, m.meal_session, o.status ORDER BY m.menu_date, m.meal_session, o.status";
        return bind(sql, vendorId, from, to, session)
                .query((rs, n) -> new OrderCount(rs.getObject("d", LocalDate.class), rs.getString("s"),
                        rs.getString("st"), rs.getLong("n")))
                .list();
    }

    // ---- UC9.2 (BR34: chỉ Completed và Paid) ----

    public List<RevenueSlice> revenue(Long vendorId, LocalDate from, LocalDate to, MealSession session) {
        String sql = "SELECT m.menu_date AS d, m.meal_session AS s, COUNT(*) AS n, SUM(o.subtotal_amount) AS gross, "
                + "SUM(o.discount_amount) AS disc, SUM(o.total_amount) AS net, "
                + "SUM(CASE WHEN o.discount_amount > 0 THEN 1 ELSE 0 END) AS discounted" + FROM_ORDERS
                + "WHERE o.vendor_id = :v AND o.status = 'COMPLETED' AND o.payment_status = 'PAID' AND" + period(session)
                + " GROUP BY m.menu_date, m.meal_session ORDER BY m.menu_date, m.meal_session";
        return bind(sql, vendorId, from, to, session)
                .query((rs, n) -> new RevenueSlice(rs.getObject("d", LocalDate.class), rs.getString("s"),
                        rs.getLong("n"), rs.getLong("gross"), rs.getLong("disc"), rs.getLong("net"),
                        rs.getLong("discounted")))
                .list();
    }

    // ---- UC9.3 ----

    public List<DishTotals> dishTotals(Long vendorId, LocalDate from, LocalDate to, MealSession session) {
        String sql = "SELECT oi.dish_id AS dish_id, MAX(oi.dish_name) AS name, SUM(oi.quantity) AS ordered, "
                + "SUM(CASE WHEN o.status = 'COMPLETED' AND o.payment_status = 'PAID' THEN oi.quantity ELSE 0 END) AS sold, "
                + "SUM(CASE WHEN o.status IN ('CANCELLED', 'EXPIRED') THEN oi.quantity ELSE 0 END) AS cancelled, "
                + "SUM(CASE WHEN o.status = 'NOT_COLLECTED' THEN oi.quantity ELSE 0 END) AS not_collected, "
                + "SUM(CASE WHEN o.status = 'COMPLETED' AND o.payment_status = 'PAID' THEN oi.line_total ELSE 0 END) AS sold_gross"
                + " FROM order_item oi JOIN orders o ON o.id = oi.order_id JOIN pickup_slot ps ON ps.id = o.pickup_slot_id "
                + "JOIN menu m ON m.id = ps.menu_id WHERE o.vendor_id = :v AND" + period(session)
                + " GROUP BY oi.dish_id ORDER BY sold DESC, ordered DESC, name";
        return bind(sql, vendorId, from, to, session)
                .query((rs, n) -> new DishTotals(rs.getLong("dish_id"), rs.getString("name"), rs.getLong("ordered"),
                        rs.getLong("sold"), rs.getLong("cancelled"), rs.getLong("not_collected"),
                        rs.getLong("sold_gross")))
                .list();
    }

    // ---- UC9.4 ----

    /** Số đơn và số suất NOT_COLLECTED trong kỳ: key "orders" và "portions". */
    public long[] notCollected(Long vendorId, LocalDate from, LocalDate to, MealSession session) {
        String sql = "SELECT COUNT(DISTINCT o.id) AS orders, COALESCE(SUM(oi.quantity), 0) AS portions"
                + " FROM order_item oi JOIN orders o ON o.id = oi.order_id JOIN pickup_slot ps ON ps.id = o.pickup_slot_id "
                + "JOIN menu m ON m.id = ps.menu_id WHERE o.vendor_id = :v AND o.status = 'NOT_COLLECTED' AND"
                + period(session);
        return bind(sql, vendorId, from, to, session)
                .query((rs, n) -> new long[] {rs.getLong("orders"), rs.getLong("portions")}).single();
    }

    /** Tổng suất xử lý theo kết quả (RESOLD, WASTED) của các đơn trong kỳ. */
    public List<KeyCount> wasteByOutcome(Long vendorId, LocalDate from, LocalDate to, MealSession session) {
        String sql = "SELECT w.outcome AS k, COALESCE(SUM(w.quantity), 0) AS n"
                + " FROM waste_record w JOIN orders o ON o.id = w.order_id JOIN pickup_slot ps ON ps.id = o.pickup_slot_id "
                + "JOIN menu m ON m.id = ps.menu_id WHERE o.vendor_id = :v AND" + period(session) + " GROUP BY w.outcome";
        return bind(sql, vendorId, from, to, session)
                .query((rs, n) -> new KeyCount(rs.getString("k"), rs.getLong("n"))).list();
    }

    public List<KeyCount> incidentsByStatus(Long vendorId, LocalDate from, LocalDate to, MealSession session) {
        String sql = "SELECT i.status AS k, COUNT(*) AS n FROM incident i JOIN orders o ON o.id = i.order_id "
                + "JOIN pickup_slot ps ON ps.id = o.pickup_slot_id JOIN menu m ON m.id = ps.menu_id "
                + "WHERE i.vendor_id = :v AND" + period(session) + " GROUP BY i.status";
        return bind(sql, vendorId, from, to, session)
                .query((rs, n) -> new KeyCount(rs.getString("k"), rs.getLong("n"))).list();
    }

    public long openFeedback(Long vendorId, LocalDate from, LocalDate to, MealSession session) {
        String sql = "SELECT COUNT(*) FROM feedback f JOIN incident i ON i.id = f.incident_id "
                + "JOIN orders o ON o.id = i.order_id JOIN pickup_slot ps ON ps.id = o.pickup_slot_id "
                + "JOIN menu m ON m.id = ps.menu_id WHERE f.status = 'OPEN' AND i.vendor_id = :v AND" + period(session);
        return bind(sql, vendorId, from, to, session).query(Long.class).single();
    }

    // ---- UC10.7 ----

    /** Mỗi quầy một dòng (kể cả quầy chưa có đơn), quầy thực thu cao nhất lên đầu. */
    public List<AdminOverviewResponse.VendorRow> vendorRows(LocalDate from, LocalDate to) {
        String sql = """
                SELECT v.id, v.name, a.name AS area_name, v.status,
                       COALESCE(t.orders, 0) AS orders, COALESCE(t.completed_paid, 0) AS completed_paid,
                       COALESCE(t.cancelled, 0) AS cancelled, COALESCE(t.not_collected, 0) AS not_collected,
                       COALESCE(t.gross, 0) AS gross, COALESCE(t.disc, 0) AS disc, COALESCE(t.net, 0) AS net
                  FROM vendor v JOIN area a ON a.id = v.area_id
                  LEFT JOIN (
                        SELECT o.vendor_id, COUNT(*) AS orders,
                               SUM(CASE WHEN o.status = 'COMPLETED' AND o.payment_status = 'PAID' THEN 1 ELSE 0 END) AS completed_paid,
                               SUM(CASE WHEN o.status IN ('CANCELLED', 'EXPIRED') THEN 1 ELSE 0 END) AS cancelled,
                               SUM(CASE WHEN o.status = 'NOT_COLLECTED' THEN 1 ELSE 0 END) AS not_collected,
                               SUM(CASE WHEN o.status = 'COMPLETED' AND o.payment_status = 'PAID' THEN o.subtotal_amount ELSE 0 END) AS gross,
                               SUM(CASE WHEN o.status = 'COMPLETED' AND o.payment_status = 'PAID' THEN o.discount_amount ELSE 0 END) AS disc,
                               SUM(CASE WHEN o.status = 'COMPLETED' AND o.payment_status = 'PAID' THEN o.total_amount ELSE 0 END) AS net
                          FROM orders o JOIN pickup_slot ps ON ps.id = o.pickup_slot_id JOIN menu m ON m.id = ps.menu_id
                         WHERE m.menu_date BETWEEN :from AND :to GROUP BY o.vendor_id) t ON t.vendor_id = v.id
                 ORDER BY net DESC, v.name""";
        return jdbc.sql(sql).param("from", from).param("to", to)
                .query((rs, n) -> new AdminOverviewResponse.VendorRow(rs.getLong("id"), rs.getString("name"),
                        rs.getString("area_name"),
                        vn.edu.phenikaa.canteen.model.VendorStatus.valueOf(rs.getString("status")),
                        rs.getLong("orders"), rs.getLong("completed_paid"), rs.getLong("cancelled"),
                        rs.getLong("not_collected"), rs.getLong("gross"), rs.getLong("disc"), rs.getLong("net")))
                .list();
    }

    public AdminOverviewResponse.Indicators indicators(LocalDateTime now) {
        String sql = """
                SELECT (SELECT COUNT(*) FROM vendor WHERE status = 'ACTIVE') AS active_vendors,
                       (SELECT COUNT(*) FROM vendor WHERE status = 'PENDING') AS pending_vendors,
                       (SELECT COUNT(*) FROM user_account WHERE role = 'CUSTOMER') AS customers,
                       (SELECT COUNT(*) FROM incident WHERE status = 'PENDING_REVIEW') AS pending_incidents,
                       (SELECT COUNT(*) FROM feedback WHERE status = 'OPEN') AS open_feedback,
                       (SELECT COUNT(*) FROM restriction WHERE status = 'ACTIVE' AND ends_at > :now) AS active_restrictions""";
        return jdbc.sql(sql).param("now", now)
                .query((rs, n) -> new AdminOverviewResponse.Indicators(rs.getLong("active_vendors"),
                        rs.getLong("pending_vendors"), rs.getLong("customers"), rs.getLong("pending_incidents"),
                        rs.getLong("open_feedback"), rs.getLong("active_restrictions")))
                .single();
    }
}
