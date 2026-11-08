package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.dto.AdminOverviewResponse;
import vn.edu.phenikaa.canteen.dto.VendorStatsResponse;
import vn.edu.phenikaa.canteen.model.IncidentStatus;
import vn.edu.phenikaa.canteen.model.MealSession;
import vn.edu.phenikaa.canteen.model.OrderStatus;
import vn.edu.phenikaa.canteen.repository.StatsRepository;
import vn.edu.phenikaa.canteen.repository.StatsRepository.KeyCount;

/**
 * UC9 (thống kê quầy) và UC10.7 (tổng hợp toàn hệ thống). Chỉ đọc.
 * <ul>
 *   <li>Doanh thu thực thu chỉ tính đơn Completed và Paid, theo số tiền sau giảm; đơn không nhận chưa thanh toán không
 *       tạo doanh thu (BR34). Giá trị trước giảm và chi phí ưu đãi được tách riêng.</li>
 *   <li>Suất đặt, bán thực tế, bị hủy, không nhận được tách riêng và cộng lại đúng bằng tổng đặt (UC9.3).</li>
 *   <li>Quản lý chỉ xem quầy mình được phân công (NFR01, BR03); quản trị viên xem mọi quầy.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    static final int MAX_RANGE_DAYS = 366;
    static final int DEFAULT_RANGE_DAYS = 7;

    private final StatsRepository stats;
    private final IncidentAccess access;
    private final Clock clock;

    @Transactional(readOnly = true)
    public VendorStatsResponse vendorStats(Long vendorId, LocalDate from, LocalDate to, MealSession session) {
        access.requireAdminOrVendorManager(vendorId);
        LocalDate[] range = resolve(from, to);
        LocalDate f = range[0];
        LocalDate t = range[1];

        // UC9.1
        Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) {
            byStatus.put(s, 0L);
        }
        List<VendorStatsResponse.OrderRow> breakdown = stats.orderCounts(vendorId, f, t, session).stream()
                .map(r -> new VendorStatsResponse.OrderRow(r.date(), MealSession.valueOf(r.session()),
                        OrderStatus.valueOf(r.status()), r.orders()))
                .toList();
        breakdown.forEach(r -> byStatus.merge(r.status(), r.orders(), Long::sum));
        long totalOrders = byStatus.values().stream().mapToLong(Long::longValue).sum();

        // UC9.2
        List<StatsRepository.RevenueSlice> slices = stats.revenue(vendorId, f, t, session);
        List<VendorStatsResponse.RevenueRow> days = slices.stream()
                .map(r -> new VendorStatsResponse.RevenueRow(r.date(), MealSession.valueOf(r.session()), r.orders(),
                        r.gross(), r.discount(), r.net()))
                .toList();
        long discounted = slices.stream().mapToLong(StatsRepository.RevenueSlice::discounted).sum();
        var revenue = new VendorStatsResponse.Revenue(
                days.stream().mapToLong(VendorStatsResponse.RevenueRow::orders).sum(),
                days.stream().mapToLong(VendorStatsResponse.RevenueRow::grossAmount).sum(),
                days.stream().mapToLong(VendorStatsResponse.RevenueRow::discountAmount).sum(),
                days.stream().mapToLong(VendorStatsResponse.RevenueRow::netRevenue).sum(),
                discounted, days);

        // UC9.3
        List<VendorStatsResponse.DishRow> dishes = stats.dishTotals(vendorId, f, t, session).stream()
                .map(d -> new VendorStatsResponse.DishRow(d.dishId(), d.name(), d.ordered(), d.sold(), d.cancelled(),
                        d.notCollected(), d.ordered() - d.sold() - d.cancelled() - d.notCollected(), d.soldGross()))
                .toList();

        // UC9.4
        long[] nc = stats.notCollected(vendorId, f, t, session);
        long resold = 0;
        long wasted = 0;
        for (KeyCount k : stats.wasteByOutcome(vendorId, f, t, session)) {
            if ("RESOLD".equals(k.key())) {
                resold = k.count();
            } else if ("WASTED".equals(k.key())) {
                wasted = k.count();
            }
        }
        Map<IncidentStatus, Long> incidents = new EnumMap<>(IncidentStatus.class);
        for (IncidentStatus s : IncidentStatus.values()) {
            incidents.put(s, 0L);
        }
        stats.incidentsByStatus(vendorId, f, t, session)
                .forEach(k -> incidents.put(IncidentStatus.valueOf(k.key()), k.count()));
        var waste = new VendorStatsResponse.Waste(nc[0], nc[1], resold, wasted, nc[1] - resold - wasted, incidents,
                stats.openFeedback(vendorId, f, t, session));

        return new VendorStatsResponse(vendorId, f, t, session,
                new VendorStatsResponse.Orders(totalOrders, byStatus, breakdown), revenue, dishes, waste);
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse adminOverview(LocalDate from, LocalDate to) {
        access.requireAdmin();
        LocalDate[] range = resolve(from, to);
        List<AdminOverviewResponse.VendorRow> rows = stats.vendorRows(range[0], range[1]);
        var totals = new AdminOverviewResponse.Totals(
                rows.stream().mapToLong(AdminOverviewResponse.VendorRow::orders).sum(),
                rows.stream().mapToLong(AdminOverviewResponse.VendorRow::completedPaid).sum(),
                rows.stream().mapToLong(AdminOverviewResponse.VendorRow::cancelledOrExpired).sum(),
                rows.stream().mapToLong(AdminOverviewResponse.VendorRow::notCollected).sum(),
                rows.stream().mapToLong(AdminOverviewResponse.VendorRow::grossAmount).sum(),
                rows.stream().mapToLong(AdminOverviewResponse.VendorRow::discountAmount).sum(),
                rows.stream().mapToLong(AdminOverviewResponse.VendorRow::netRevenue).sum());
        return new AdminOverviewResponse(range[0], range[1], totals, rows,
                stats.indicators(LocalDateTime.now(clock)));
    }

    /** Mặc định 7 ngày gần nhất tính đến hôm nay. Từ phải không sau đến, và khoảng không quá 366 ngày. */
    LocalDate[] resolve(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Ngày bắt đầu phải không sau ngày kết thúc.");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Khoảng thống kê tối đa " + MAX_RANGE_DAYS + " ngày.");
        }
        return new LocalDate[] {start, end};
    }
}
