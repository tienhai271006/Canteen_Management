package vn.edu.phenikaa.canteen.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.dto.PageResponse;
import vn.edu.phenikaa.canteen.dto.NoShowResponse;
import vn.edu.phenikaa.canteen.dto.NotCollectedRequest;
import vn.edu.phenikaa.canteen.dto.OrderResponse;
import vn.edu.phenikaa.canteen.dto.WasteHandlingRequest;
import vn.edu.phenikaa.canteen.dto.WasteOrderResponse;
import vn.edu.phenikaa.canteen.service.NoShowService;
import vn.edu.phenikaa.canteen.service.WasteHandlingService;

/** UC7.8 và UC3.7 tại quầy. Quyền theo quầy do service kiểm tra từ dữ liệu của đơn (NFR01, BR03). */
@Tag(name = "Counter - No-show and waste")
@RestController
@RequestMapping("/api/counter")
@RequiredArgsConstructor
public class CounterIncidentController {

    private final NoShowService noShow;
    private final WasteHandlingService waste;

    /** Đơn Ready đã quá hạn nhận, cần kiểm tra thực tế. */
    @GetMapping("/orders/overdue")
    public PageResponse<OrderResponse> overdue(@RequestParam Long vendorId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return noShow.listOverdue(vendorId, page, size);
    }

    @PostMapping("/orders/{orderId}/not-collected")
    public NoShowResponse notCollected(@PathVariable Long orderId, @Valid @RequestBody NotCollectedRequest req) {
        return noShow.markNotCollected(orderId, req);
    }

    /** Các đơn không nhận còn món chưa xử lý. */
    @GetMapping("/waste")
    public List<WasteOrderResponse> pendingWaste(@RequestParam Long vendorId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return waste.listPending(vendorId, page, size);
    }

    @GetMapping("/orders/{orderId}/waste")
    public WasteOrderResponse wasteOf(@PathVariable Long orderId) {
        return waste.get(orderId);
    }

    @PostMapping("/orders/{orderId}/waste")
    public WasteOrderResponse handleWaste(@PathVariable Long orderId, @Valid @RequestBody WasteHandlingRequest req) {
        return waste.handle(orderId, req);
    }
}
