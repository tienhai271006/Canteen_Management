package vn.edu.phenikaa.canteen.controller;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import vn.edu.phenikaa.canteen.dto.LateArrivalCreateRequest;
import vn.edu.phenikaa.canteen.dto.LateArrivalResponse;
import vn.edu.phenikaa.canteen.dto.OrderHistoryResponse;
import vn.edu.phenikaa.canteen.dto.OrderResponse;
import vn.edu.phenikaa.canteen.dto.ReasonRequest;
import vn.edu.phenikaa.canteen.model.OrderStatus;
import vn.edu.phenikaa.canteen.service.CustomerOrderService;
import vn.edu.phenikaa.canteen.service.LateArrivalService;

/**
 * UC6 phía khách. Cùng tiền tố /api/orders với OrderController (đặt đơn và xem một đơn); ở đây là danh sách,
 * tiến độ, hủy và các yêu cầu gửi quầy. Khách chỉ thấy và thao tác trên đơn của mình (BR03).
 */
@Tag(name = "Customer - My orders")
@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class MyOrderController {

    private final CustomerOrderService customerOrders;
    private final LateArrivalService lateArrivals;

    /** Lọc theo trạng thái bằng {@code ?status=PENDING,CONFIRMED}; bỏ trống là mọi trạng thái. */
    @GetMapping
    public PageResponse<OrderResponse> list(@RequestParam(required = false) Set<OrderStatus> status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return customerOrders.list(status, page, size);
    }

    @GetMapping("/{orderId}/history")
    public List<OrderHistoryResponse> history(@PathVariable Long orderId) {
        return customerOrders.history(orderId);
    }

    /** UC6.2: chỉ hủy được khi quầy chưa xác nhận. */
    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(@PathVariable Long orderId) {
        return customerOrders.cancelPending(orderId);
    }

    // ---- UC6.3 ----

    @PostMapping("/{orderId}/cancel-requests")
    public ResponseEntity<CancelRequestResponse> requestCancel(@PathVariable Long orderId,
                                                               @Valid @RequestBody ReasonRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerOrders.requestCancel(orderId, req.reason()));
    }

    @GetMapping("/{orderId}/cancel-requests")
    public List<CancelRequestResponse> cancelRequests(@PathVariable Long orderId) {
        return customerOrders.listCancelRequests(orderId);
    }

    @DeleteMapping("/{orderId}/cancel-requests/{requestId}")
    public CancelRequestResponse withdrawCancel(@PathVariable Long orderId, @PathVariable Long requestId) {
        return customerOrders.withdrawCancelRequest(orderId, requestId);
    }

    // ---- UC6.4 ----

    @PostMapping("/{orderId}/late-arrivals")
    public ResponseEntity<LateArrivalResponse> requestLateArrival(@PathVariable Long orderId,
                                                                  @Valid @RequestBody LateArrivalCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lateArrivals.request(orderId, req));
    }

    @GetMapping("/{orderId}/late-arrivals")
    public List<LateArrivalResponse> lateArrivals(@PathVariable Long orderId) {
        return lateArrivals.listForOrder(orderId);
    }

    @DeleteMapping("/{orderId}/late-arrivals/{requestId}")
    public LateArrivalResponse withdrawLateArrival(@PathVariable Long orderId, @PathVariable Long requestId) {
        return lateArrivals.withdraw(orderId, requestId);
    }
}
