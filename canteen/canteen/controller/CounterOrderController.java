package vn.edu.phenikaa.canteen.controller;

import java.util.Set;

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
import vn.edu.phenikaa.canteen.dto.CancelRequestResponse;
import vn.edu.phenikaa.canteen.dto.CompleteOrderRequest;
import vn.edu.phenikaa.canteen.dto.DecisionRequest;
import vn.edu.phenikaa.canteen.dto.LateArrivalDecisionRequest;
import vn.edu.phenikaa.canteen.dto.LateArrivalResponse;
import vn.edu.phenikaa.canteen.dto.OrderResponse;
import vn.edu.phenikaa.canteen.dto.ReasonRequest;
import vn.edu.phenikaa.canteen.model.CancelRequestStatus;
import vn.edu.phenikaa.canteen.model.LateArrivalStatus;
import vn.edu.phenikaa.canteen.model.OrderStatus;
import vn.edu.phenikaa.canteen.service.CancelApprovalService;
import vn.edu.phenikaa.canteen.service.CounterOrderService;
import vn.edu.phenikaa.canteen.service.LateArrivalService;

/**
 * UC7: màn hình quầy. SecurityConfig đã giới hạn /api/counter/** cho VENDOR_STAFF và VENDOR_MANAGER; việc người đó
 * có thuộc ĐÚNG quầy của đơn hay không do service kiểm tra bằng dữ liệu trong DB (NFR01, BR03), không tin id trên URL.
 */
@Tag(name = "Counter - Orders")
@RestController
@RequestMapping("/api/counter")
@RequiredArgsConstructor
public class CounterOrderController {

    private final CounterOrderService counter;
    private final CancelApprovalService cancelApproval;
    private final LateArrivalService lateArrivals;

    // ---- đơn ----

    /** Mặc định liệt kê đơn còn phải xử lý (Pending đến Ready), đơn cũ nhất trước. */
    @GetMapping("/orders")
    public PageResponse<OrderResponse> orders(@RequestParam Long vendorId,
                                              @RequestParam(required = false) Set<OrderStatus> status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return counter.list(vendorId, status, page, size);
    }

    @GetMapping("/orders/by-code")
    public OrderResponse byCode(@RequestParam Long vendorId, @RequestParam String code) {
        return counter.findByCode(vendorId, code);
    }

    @GetMapping("/orders/{orderId}")
    public OrderResponse get(@PathVariable Long orderId) {
        return counter.get(orderId);
    }

    @PostMapping("/orders/{orderId}/confirm")
    public OrderResponse confirm(@PathVariable Long orderId) {
        return counter.confirm(orderId);
    }

    @PostMapping("/orders/{orderId}/reject")
    public OrderResponse reject(@PathVariable Long orderId, @Valid @RequestBody ReasonRequest req) {
        return counter.reject(orderId, req.reason());
    }

    @PostMapping("/orders/{orderId}/prepare")
    public OrderResponse prepare(@PathVariable Long orderId) {
        return counter.startPreparing(orderId);
    }

    @PostMapping("/orders/{orderId}/ready")
    public OrderResponse ready(@PathVariable Long orderId) {
        return counter.markReady(orderId);
    }

    @PostMapping("/orders/{orderId}/complete")
    public OrderResponse complete(@PathVariable Long orderId, @Valid @RequestBody CompleteOrderRequest req) {
        return counter.complete(orderId, req);
    }

    // ---- UC7.6 yêu cầu hủy ----

    @GetMapping("/cancel-requests")
    public PageResponse<CancelRequestResponse> cancelRequests(@RequestParam Long vendorId,
                                                              @RequestParam(required = false) Set<CancelRequestStatus> status,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return cancelApproval.list(vendorId, status, page, size);
    }

    @PostMapping("/cancel-requests/{requestId}/approve")
    public CancelRequestResponse approveCancel(@PathVariable Long requestId, @Valid @RequestBody DecisionRequest req) {
        return cancelApproval.approve(requestId, req);
    }

    @PostMapping("/cancel-requests/{requestId}/reject")
    public CancelRequestResponse rejectCancel(@PathVariable Long requestId, @Valid @RequestBody DecisionRequest req) {
        return cancelApproval.reject(requestId, req);
    }

    // ---- UC7.7 đến muộn ----

    @GetMapping("/late-arrivals")
    public PageResponse<LateArrivalResponse> lateArrivals(@RequestParam Long vendorId,
                                                          @RequestParam(required = false) Set<LateArrivalStatus> status,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return lateArrivals.list(vendorId, status, page, size);
    }

    @PostMapping("/late-arrivals/{requestId}/approve")
    public LateArrivalResponse approveLate(@PathVariable Long requestId,
                                           @Valid @RequestBody LateArrivalDecisionRequest req) {
        return lateArrivals.approve(requestId, req);
    }

    @PostMapping("/late-arrivals/{requestId}/reject")
    public LateArrivalResponse rejectLate(@PathVariable Long requestId, @Valid @RequestBody DecisionRequest req) {
        return lateArrivals.reject(requestId, req);
    }
}
