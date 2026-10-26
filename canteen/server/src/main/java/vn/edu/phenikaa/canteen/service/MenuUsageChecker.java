package vn.edu.phenikaa.canteen.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Kiểm tra món và khung giờ đã phát sinh đơn chưa. Dùng JDBC trực tiếp để Pha 5 không phụ thuộc
 * entity của Pha 6 (bảng orders và order_item đã có từ migration V4).
 */
@Component
@RequiredArgsConstructor
public class MenuUsageChecker {

    private final JdbcClient jdbc;

    public boolean isItemOrdered(Long menuItemId) {
        return count("SELECT COUNT(*) FROM order_item WHERE menu_item_id = :id", menuItemId) > 0;
    }

    public boolean isSlotUsed(Long slotId) {
        return count("SELECT COUNT(*) FROM orders WHERE pickup_slot_id = :id", slotId) > 0;
    }

    /** Giỏ hàng không giữ suất (BR11) nên gỡ món khỏi giỏ của khách là an toàn. */
    public void removeFromCarts(Long menuItemId) {
        jdbc.sql("DELETE FROM cart_item WHERE menu_item_id = :id").param("id", menuItemId).update();
    }

    private long count(String sql, Long id) {
        Long n = jdbc.sql(sql).param("id", id).query(Long.class).single();
        return n == null ? 0 : n;
    }
}
