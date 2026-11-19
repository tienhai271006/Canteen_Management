package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.dto.MenuSummaryResponse;
import vn.edu.phenikaa.canteen.dto.SlotRequest;
import vn.edu.phenikaa.canteen.dto.SlotCapacityResponse;
import vn.edu.phenikaa.canteen.model.Menu;
import vn.edu.phenikaa.canteen.model.PickupSlot;
import vn.edu.phenikaa.canteen.repository.PickupSlotRepository;

/** UC3: giờ đóng nhận đơn của ca và các khung giờ nhận. */
@Service
@RequiredArgsConstructor
public class SlotService {

    private final PickupSlotRepository slots;
    private final MenuGuard guard;
    private final MenuUsageChecker usage;
    private final SlotCapacityService capacity;
    private final Clock clock;

    @Transactional
    public MenuSummaryResponse updateCutoff(Long menuId, LocalTime cutoff) {
        Menu menu = guard.editable(menuId);
        menu.setOrderCutoffTime(cutoff);
        return MenuSummaryResponse.from(menu);
    }

    @Transactional(readOnly = true)
    public List<SlotCapacityResponse> list(Long menuId) {
        guard.managed(menuId);
        return capacity.view(menuId);
    }

    @Transactional
    public SlotCapacityResponse add(Long menuId, SlotRequest req) {
        Menu menu = guard.editable(menuId);
        requireValidRange(req);
        requireNoOverlap(menuId, req, null);
        PickupSlot slot = new PickupSlot();
        slot.setMenu(menu);
        slot.setStartTime(req.startTime());
        slot.setEndTime(req.endTime());
        PickupSlot saved = slots.saveAndFlush(slot);
        return SlotCapacityResponse.of(saved, 0);
    }

    /** Khung đã có đơn thì không đổi giờ được, vì khách đã chọn đúng giờ đó. */
    @Transactional
    public SlotCapacityResponse update(Long menuId, Long slotId, SlotRequest req) {
        guard.editable(menuId);
        PickupSlot slot = requireSlot(menuId, slotId);
        if (usage.isSlotUsed(slotId)) {
            throw new BusinessException(ErrorCode.SLOT_IN_USE);
        }
        requireValidRange(req);
        requireNoOverlap(menuId, req, slotId);
        slot.setStartTime(req.startTime());
        slot.setEndTime(req.endTime());
        return capacity.viewOne(slot);
    }

    /**
     * Đặt hoặc bỏ hạn mức của khung (UC3.5). Không cần khung chưa có đơn như đổi giờ: quản lý được tăng hạn mức khi đã
     * mở bán, còn giảm thì không thấp hơn số suất đã nhận ({@code reserved_qty <= capacity} kiểm trong câu UPDATE).
     */
    @Transactional
    public SlotCapacityResponse setCapacity(Long menuId, Long slotId, Integer cap) {
        guard.managed(menuId);
        PickupSlot slot = requireSlot(menuId, slotId);
        LocalDateTime now = LocalDateTime.now(clock);
        int changed = cap == null ? slots.clearCapacity(slotId, now) : slots.setCapacity(slotId, cap, now);
        if (changed != 1) {
            throw new BusinessException(ErrorCode.SLOT_CAPACITY_BELOW_COMMITTED);
        }
        // UPDATE native bỏ qua persistence context: nạp lại để số liệu trả về là mới.
        slots.flush();
        PickupSlot fresh = slots.findById(slot.getId()).orElseThrow();
        return capacity.viewOne(fresh);
    }

    @Transactional
    public void delete(Long menuId, Long slotId) {
        Menu menu = guard.editable(menuId);
        PickupSlot slot = requireSlot(menuId, slotId);
        if (usage.isSlotUsed(slotId)) {
            throw new BusinessException(ErrorCode.SLOT_IN_USE);
        }
        if (menu.isPublished() && slots.countByMenuId(menuId) <= 1) {
            throw new BusinessException(ErrorCode.MENU_INCOMPLETE);   // thực đơn đang công bố phải còn khung nhận
        }
        slots.delete(slot);
    }

    private PickupSlot requireSlot(Long menuId, Long slotId) {
        return slots.findByIdAndMenuId(slotId, menuId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy khung giờ nhận."));
    }

    private void requireValidRange(SlotRequest req) {
        if (!req.endTime().isAfter(req.startTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Giờ kết thúc phải sau giờ bắt đầu.");
        }
    }

    private void requireNoOverlap(Long menuId, SlotRequest req, Long ignoreSlotId) {
        boolean overlap = slots.findByMenuIdOrderByStartTime(menuId).stream()
                .filter(s -> !s.getId().equals(ignoreSlotId))
                .anyMatch(s -> s.overlaps(req.startTime(), req.endTime()));
        if (overlap) {
            throw new BusinessException(ErrorCode.SLOT_OVERLAP);
        }
    }
}
