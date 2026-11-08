package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.model.Order;
import vn.edu.phenikaa.canteen.model.OrderItem;
import vn.edu.phenikaa.canteen.repository.QuotaRepository;

/**
 * Giữ và trả suất (BR12, NFR02). Cả hai là MỘT câu UPDATE có điều kiện trên dòng hạn mức, không đọc rồi ghi,
 * nên hai request tranh suất cuối thì chỉ một bên thành công. CHECK ck_quota_no_oversell trong DB là lớp chặn cuối.
 * Luôn chạy trong transaction của người gọi: đơn lỗi thì suất đã giữ cũng được hoàn lại (BR11).
 */
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final QuotaRepository quotas;
    private final Clock clock;

    /**
     * Giữ suất cho nhiều món cùng lúc. Các món được xử lý theo thứ tự id tăng dần để hai đơn giữ cùng tập món
     * không khóa dòng theo thứ tự ngược nhau và gây deadlock.
     *
     * @param demand menuItemId -> số suất cần
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserveAll(Map<Long, Integer> demand) {
        for (Map.Entry<Long, Integer> e : new TreeMap<>(demand).entrySet()) {
            reserve(e.getKey(), e.getValue());
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Long menuItemId, int qty) {
        requirePositive(qty);
        if (quotas.tryReserve(menuItemId, qty, LocalDateTime.now(clock)) != 1) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Long menuItemId, int qty) {
        requirePositive(qty);
        if (quotas.tryRelease(menuItemId, qty, LocalDateTime.now(clock)) != 1) {
            // Trả nhiều hơn số đang giữ nghĩa là dữ liệu đã lệch. Dừng và hủy cả transaction thay vì im lặng làm sai.
            throw new IllegalStateException("Không thể trả " + qty + " suất cho menu_item " + menuItemId
                    + ": số suất đang giữ ít hơn.");
        }
    }

    /**
     * Trả toàn bộ suất của một đơn. Chỉ gọi từ {@code OrderService.transition} khi đơn vừa chuyển sang
     * CANCELLED hoặc EXPIRED, nên mỗi đơn được trả đúng một lần (BR14).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseForOrder(Order order) {
        Map<Long, Integer> byItem = new TreeMap<>();
        for (OrderItem item : order.getItems()) {
            byItem.merge(item.getMenuItemId(), item.getQuantity(), Integer::sum);
        }
        byItem.forEach(this::release);
    }

    private static void requirePositive(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("Số suất phải dương.");
        }
    }
}
