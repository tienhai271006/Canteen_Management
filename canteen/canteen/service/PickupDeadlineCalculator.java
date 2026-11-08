package vn.edu.phenikaa.canteen.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.config.OrderProperties;

/**
 * BR22: hạn nhận = mốc muộn hơn giữa cuối khung nhận và lúc báo Ready, cộng thời gian chờ (mặc định 15 phút).
 * Ví dụ (Bảng 21): khung kết thúc 12:00, Ready lúc 11:45 thì hạn 12:15; Ready lúc 12:10 thì hạn 12:25.
 * Hạn đã được quầy duyệt gia hạn (UC7.7) thay thế kết quả của hàm này, nên việc ưu tiên hạn đã thỏa thuận do người gọi quyết định.
 */
@Component
@RequiredArgsConstructor
public class PickupDeadlineCalculator {

    private final OrderProperties props;

    public LocalDateTime compute(LocalDate menuDate, LocalTime slotEnd, LocalDateTime readyAt) {
        LocalDateTime slotEndAt = menuDate.atTime(slotEnd);
        LocalDateTime base = readyAt.isAfter(slotEndAt) ? readyAt : slotEndAt;
        return base.plus(props.pickupWait());
    }
}
