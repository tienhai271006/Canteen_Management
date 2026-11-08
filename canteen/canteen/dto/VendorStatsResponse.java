package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import vn.edu.phenikaa.canteen.model.IncidentStatus;
import vn.edu.phenikaa.canteen.model.MealSession;
import vn.edu.phenikaa.canteen.model.OrderStatus;

/**
 * Thống kê một quầy (UC9). Mọi số liệu tính theo NGÀY PHỤC VỤ của đơn (ngày của thực đơn), không theo ngày tạo đơn,
 * để một đơn đặt hôm trước cho bữa trưa hôm sau được tính vào đúng bữa đó. Tiền tính bằng đồng.
 */
public record VendorStatsResponse(
        Long vendorId,
        LocalDate from,
        LocalDate to,
        MealSession session,
        Orders orders,
        Revenue revenue,
        List<DishRow> dishes,
        Waste waste) {

    /** UC9.1. {@code byStatus} luôn đủ mọi trạng thái (không có thì bằng 0). */
    public record Orders(long total, Map<OrderStatus, Long> byStatus, List<OrderRow> breakdown) {
    }

    public record OrderRow(LocalDate date, MealSession session, OrderStatus status, long orders) {
    }

    /**
     * UC9.2, BR34: chỉ đơn Completed VÀ Paid. {@code grossAmount} là giá trị trước giảm, {@code discountAmount} là chi phí
     * ưu đãi, {@code netRevenue} là tiền thực thu (= gross - discount). Đơn không nhận chưa thanh toán không tạo doanh thu.
     */
    public record Revenue(long completedPaidOrders, long grossAmount, long discountAmount, long netRevenue,
                          long discountedOrders, List<RevenueRow> byDay) {
    }

    public record RevenueRow(LocalDate date, MealSession session, long orders, long grossAmount, long discountAmount,
                             long netRevenue) {
    }

    /**
     * UC9.3. {@code ordered} = mọi suất khách đặt = sold + cancelled + notCollected + inProgress.
     * {@code cancelled} gồm đơn Cancelled và Expired (không được phục vụ). {@code inProgress} là Pending đến Ready.
     */
    public record DishRow(Long dishId, String dishName, long ordered, long sold, long cancelled, long notCollected,
                          long inProgress, long soldGrossAmount) {
    }

    /**
     * UC9.4. {@code unprocessed} = notCollectedPortions - resold - wasted (món đã làm chưa được quầy xử lý).
     * {@code incidentsByStatus} luôn đủ mọi trạng thái. {@code openFeedback}: phản hồi đang chờ của các sự việc trong kỳ.
     */
    public record Waste(long notCollectedOrders, long notCollectedPortions, long resold, long wasted, long unprocessed,
                        Map<IncidentStatus, Long> incidentsByStatus, long openFeedback) {
    }
}
