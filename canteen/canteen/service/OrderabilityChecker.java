package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.model.Menu;
import vn.edu.phenikaa.canteen.model.MenuItem;

/**
 * Một nơi duy nhất trả lời "món này đặt được lúc này không" (BR02): thực đơn đã công bố, quầy đang hoạt động,
 * chưa quá giờ đóng nhận đơn, món chưa ẩn và chưa hết. Giỏ hàng dùng để chặn thêm món, placeOrder dùng để chặn đặt đơn.
 * Quầy tạm ngừng chỉ chặn đơn mới, đơn cũ vẫn xử lý bình thường (R2.4).
 * Cần menu.vendor và menuItem.dish đã được nạp.
 */
@Component
@RequiredArgsConstructor
public class OrderabilityChecker {

    private final Clock clock;

    /** @return null nếu đặt được, ngược lại là lý do đầu tiên gặp. */
    public ErrorCode problemOf(Menu menu) {
        if (!menu.isPublished()) {
            return ErrorCode.DISH_UNAVAILABLE;
        }
        if (!menu.getVendor().isActive()) {
            return ErrorCode.VENDOR_NOT_ACTIVE;
        }
        if (cutoffPassed(menu)) {
            return ErrorCode.ORDER_CUTOFF_PASSED;
        }
        return null;
    }

    public ErrorCode problemOf(MenuItem item) {
        ErrorCode menuProblem = problemOf(item.getMenu());
        if (menuProblem != null) {
            return menuProblem;
        }
        if (!item.isAvailable() || !item.getDish().isActive()) {
            return ErrorCode.DISH_UNAVAILABLE;
        }
        return null;
    }

    public void require(Menu menu) {
        ErrorCode problem = problemOf(menu);
        if (problem != null) {
            throw new BusinessException(problem);
        }
    }

    public void require(MenuItem item) {
        ErrorCode problem = problemOf(item);
        if (problem != null) {
            throw new BusinessException(problem);
        }
    }

    private boolean cutoffPassed(Menu menu) {
        LocalDate today = LocalDate.now(clock);
        if (menu.getMenuDate().isBefore(today)) {
            return true;
        }
        return menu.getMenuDate().isEqual(today) && !LocalTime.now(clock).isBefore(menu.getOrderCutoffTime());
    }
}
