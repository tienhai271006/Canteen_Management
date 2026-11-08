package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.security.CurrentUser;
import vn.edu.phenikaa.canteen.dto.AdjustQuotaRequest;
import vn.edu.phenikaa.canteen.dto.MenuItemResponse;
import vn.edu.phenikaa.canteen.dto.QuotaAdjustmentResponse;
import vn.edu.phenikaa.canteen.model.MenuItem;
import vn.edu.phenikaa.canteen.model.Quota;
import vn.edu.phenikaa.canteen.model.QuotaAdjustment;
import vn.edu.phenikaa.canteen.repository.MenuItemRepository;
import vn.edu.phenikaa.canteen.repository.QuotaAdjustmentRepository;
import vn.edu.phenikaa.canteen.repository.QuotaRepository;

/**
 * R3.6, BR33: chỉnh tổng suất và phần dành cho khách tại quầy.
 * Quy tắc: tổng mới không được nhỏ hơn (suất đã giữ + phần dành cho quầy mới), tức không giảm dưới số đã cam kết.
 * Khóa dòng hạn mức bằng SELECT ... FOR UPDATE nên khi quầy đang chỉnh mà có khách giữ suất cùng lúc,
 * hai việc xếp hàng với nhau và phép so sánh với số đã giữ luôn đúng.
 */
@Service
@RequiredArgsConstructor
public class QuotaAdminService {

    private final MenuGuard guard;
    private final MenuItemRepository items;
    private final QuotaRepository quotas;
    private final QuotaAdjustmentRepository adjustments;
    private final Clock clock;

    @Transactional
    public MenuItemResponse adjust(Long menuId, Long itemId, AdjustQuotaRequest req) {
        guard.editable(menuId);
        MenuItem item = requireItem(menuId, itemId);
        Quota quota = quotas.findByMenuItemIdForUpdate(itemId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy hạn mức của món."));

        int newTotal = req.totalSlots();
        int newCounter = req.counterSlots();
        if (newCounter > newTotal) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Phần dành cho khách tại quầy không được vượt tổng số suất.");
        }
        int committed = quota.getReservedSlots();
        if (newTotal - newCounter < committed) {
            throw new BusinessException(ErrorCode.QUOTA_BELOW_COMMITTED,
                    "Đã có " + committed + " suất được đặt trước, tổng suất trừ phần dành cho quầy không được nhỏ hơn số này.");
        }

        int deltaTotal = newTotal - quota.getTotalSlots();
        int deltaCounter = newCounter - quota.getCounterSlots();
        if (deltaTotal == 0 && deltaCounter == 0) {
            return MenuItemResponse.from(item, quota);      // không đổi gì thì không ghi lịch sử
        }
        quota.setTotalSlots(newTotal);
        quota.setCounterSlots(newCounter);

        QuotaAdjustment log = new QuotaAdjustment();
        log.setQuotaId(quota.getId());
        log.setDeltaTotal(deltaTotal);
        log.setDeltaCounter(deltaCounter);
        log.setReason(req.reason().trim());
        log.setActorId(CurrentUser.id());
        log.setCreatedAt(LocalDateTime.now(clock));
        adjustments.save(log);
        return MenuItemResponse.from(item, quota);
    }

    @Transactional(readOnly = true)
    public List<QuotaAdjustmentResponse> history(Long menuId, Long itemId) {
        guard.managed(menuId);
        requireItem(menuId, itemId);
        Quota quota = quotas.findByMenuItemId(itemId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy hạn mức của món."));
        return adjustments.findByQuotaIdOrderByIdDesc(quota.getId()).stream()
                .map(QuotaAdjustmentResponse::from).toList();
    }

    private MenuItem requireItem(Long menuId, Long itemId) {
        return items.findByIdAndMenuId(itemId, menuId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy món trong thực đơn."));
    }
}
