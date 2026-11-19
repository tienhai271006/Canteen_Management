package vn.edu.phenikaa.canteen.dto;

import java.time.LocalDate;
import java.util.List;

import vn.edu.phenikaa.canteen.model.VendorStatus;

/** UC10.7: tổng hợp hoạt động các quầy và chỉ tiêu chung của nền tảng trong kỳ. */
public record AdminOverviewResponse(LocalDate from, LocalDate to, Totals totals, List<VendorRow> vendors,
                                    Indicators indicators) {

    public record Totals(long orders, long completedPaid, long cancelledOrExpired, long notCollected,
                         long grossAmount, long discountAmount, long netRevenue) {
    }

    public record VendorRow(Long vendorId, String name, String areaName, VendorStatus status, long orders,
                            long completedPaid, long cancelledOrExpired, long notCollected, long grossAmount,
                            long discountAmount, long netRevenue) {
    }

    /** Chỉ tiêu tại thời điểm xem (không phụ thuộc kỳ). */
    public record Indicators(long activeVendors, long pendingVendors, long customers, long pendingIncidents,
                             long openFeedback, long activeRestrictions) {
    }
}
