package vn.edu.phenikaa.canteen.dto;

import java.time.LocalTime;

import vn.edu.phenikaa.canteen.model.PickupSlot;

/**
 * Khung nhận kèm số liệu hạn mức, cho quản lý quầy (UC3.5). Quan hệ:
 * {@code remaining = capacity - held - used}. {@code used} là suất của đơn đã hoàn tất hoặc khách không nhận (món đã làm,
 * không tự cộng lại, BR15); {@code held} là suất của các đơn còn đang xử lý. Khung chưa giới hạn thì capacity và remaining là null.
 */
public record SlotCapacityResponse(
        Long id,
        LocalTime startTime,
        LocalTime endTime,
        Integer capacity,
        int held,
        int used,
        Integer remaining,
        boolean full) {

    public static SlotCapacityResponse of(PickupSlot s, int used) {
        int u = Math.min(used, s.getReservedQty());
        return new SlotCapacityResponse(s.getId(), s.getStartTime(), s.getEndTime(), s.getCapacity(),
                s.getReservedQty() - u, u, s.remaining(), s.isFull());
    }
}
