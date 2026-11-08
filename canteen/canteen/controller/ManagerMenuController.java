package vn.edu.phenikaa.canteen.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.dto.AddMenuItemRequest;
import vn.edu.phenikaa.canteen.dto.AdjustQuotaRequest;
import vn.edu.phenikaa.canteen.dto.CreateMenuRequest;
import vn.edu.phenikaa.canteen.dto.ItemAvailabilityRequest;
import vn.edu.phenikaa.canteen.dto.MenuDetailResponse;
import vn.edu.phenikaa.canteen.dto.MenuItemResponse;
import vn.edu.phenikaa.canteen.dto.MenuSummaryResponse;
import vn.edu.phenikaa.canteen.dto.QuotaAdjustmentResponse;
import vn.edu.phenikaa.canteen.dto.SlotRequest;
import vn.edu.phenikaa.canteen.dto.SlotResponse;
import vn.edu.phenikaa.canteen.dto.UpdateCutoffRequest;
import vn.edu.phenikaa.canteen.service.MenuService;
import vn.edu.phenikaa.canteen.service.QuotaAdminService;
import vn.edu.phenikaa.canteen.service.SlotService;

/** Mọi thao tác lấy vendorId từ bản ghi thực đơn trong DB (MenuGuard), không tin id quầy trên URL (BR03). */
@Tag(name = "Manager - Menu")
@RestController
@RequestMapping("/api/manager/menus")
@RequiredArgsConstructor
public class ManagerMenuController {

    private final MenuService menuService;
    private final SlotService slotService;
    private final QuotaAdminService quotaService;

    @GetMapping
    public List<MenuSummaryResponse> list(@RequestParam Long vendorId,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return menuService.list(vendorId, from, to);
    }

    @PostMapping
    public ResponseEntity<MenuSummaryResponse> create(@Valid @RequestBody CreateMenuRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.create(req));
    }

    @GetMapping("/{menuId}")
    public MenuDetailResponse get(@PathVariable Long menuId) {
        return menuService.get(menuId);
    }

    @PostMapping("/{menuId}/publish")
    public MenuSummaryResponse publish(@PathVariable Long menuId) {
        return menuService.publish(menuId);
    }

    @PostMapping("/{menuId}/unpublish")
    public MenuSummaryResponse unpublish(@PathVariable Long menuId) {
        return menuService.unpublish(menuId);
    }

    @PutMapping("/{menuId}/cutoff")
    public MenuSummaryResponse updateCutoff(@PathVariable Long menuId, @Valid @RequestBody UpdateCutoffRequest req) {
        return slotService.updateCutoff(menuId, req.orderCutoffTime());
    }

    // ---- Món trong thực đơn và hạn mức ----

    @PostMapping("/{menuId}/items")
    public ResponseEntity<MenuItemResponse> addItem(@PathVariable Long menuId,
                                                    @Valid @RequestBody AddMenuItemRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.addItem(menuId, req));
    }

    @PatchMapping("/{menuId}/items/{itemId}/availability")
    public MenuItemResponse setAvailability(@PathVariable Long menuId, @PathVariable Long itemId,
                                            @Valid @RequestBody ItemAvailabilityRequest req) {
        return menuService.setAvailability(menuId, itemId, req.available());
    }

    @DeleteMapping("/{menuId}/items/{itemId}")
    public ResponseEntity<Void> removeItem(@PathVariable Long menuId, @PathVariable Long itemId) {
        menuService.removeItem(menuId, itemId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{menuId}/items/{itemId}/quota")
    public MenuItemResponse adjustQuota(@PathVariable Long menuId, @PathVariable Long itemId,
                                        @Valid @RequestBody AdjustQuotaRequest req) {
        return quotaService.adjust(menuId, itemId, req);
    }

    @GetMapping("/{menuId}/items/{itemId}/quota/history")
    public List<QuotaAdjustmentResponse> quotaHistory(@PathVariable Long menuId, @PathVariable Long itemId) {
        return quotaService.history(menuId, itemId);
    }

    // ---- Khung giờ nhận ----

    @GetMapping("/{menuId}/slots")
    public List<SlotResponse> slots(@PathVariable Long menuId) {
        return slotService.list(menuId);
    }

    @PostMapping("/{menuId}/slots")
    public ResponseEntity<SlotResponse> addSlot(@PathVariable Long menuId, @Valid @RequestBody SlotRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(slotService.add(menuId, req));
    }

    @PutMapping("/{menuId}/slots/{slotId}")
    public SlotResponse updateSlot(@PathVariable Long menuId, @PathVariable Long slotId,
                                   @Valid @RequestBody SlotRequest req) {
        return slotService.update(menuId, slotId, req);
    }

    @DeleteMapping("/{menuId}/slots/{slotId}")
    public ResponseEntity<Void> deleteSlot(@PathVariable Long menuId, @PathVariable Long slotId) {
        slotService.delete(menuId, slotId);
        return ResponseEntity.noContent().build();
    }
}
