package vn.edu.phenikaa.canteen.dto;

import java.time.LocalTime;

import vn.edu.phenikaa.canteen.model.PickupSlot;

/**
 * Khung nhận như khách thấy. {@code full} cho biết hết chỗ (UC4, UC5.2); {@code remaining} là số suất còn nhận được,
 * null nếu quầy chưa giới hạn khung này. Tổng hạn mức và số đã giữ chỉ quản lý quầy xem, xem {@link SlotCapacityResponse}.
 */
public record SlotResponse(Long id, LocalTime startTime, LocalTime endTime, boolean full, Integer remaining) {
    public static SlotResponse from(PickupSlot s) {
        return new SlotResponse(s.getId(), s.getStartTime(), s.getEndTime(), s.isFull(), s.remaining());
    }
}
