package vn.edu.phenikaa.canteen.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.dto.OrderResponse;
import vn.edu.phenikaa.canteen.dto.PlaceOrderRequest;
import vn.edu.phenikaa.canteen.service.OrderService.PlacedOrder;
import vn.edu.phenikaa.canteen.service.OrderService;

/** Danh sách đơn, hủy đơn nằm ở MyOrderController (Pha 7). Ở đây chỉ có đặt đơn và xem một đơn của mình. */
@Tag(name = "Customer - Orders")
@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 201 khi tạo đơn mới, 200 khi là yêu cầu gửi lại cùng Idempotency-Key (trả đúng đơn cũ).
     * Header được kiểm tra trong service (thay vì ràng buộc ở tham số) để lỗi vẫn ra đúng dạng ErrorResponse 400.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> place(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest req) {
        PlacedOrder placed;
        try {
            placed = orderService.placeOrder(req, idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            // Hai request cùng khóa chạy song song: bên thua vi phạm UNIQUE rồi bị hủy, trả lại đơn của bên thắng.
            placed = orderService.findReplay(idempotencyKey).orElseThrow(() -> e);
        }
        return ResponseEntity.status(placed.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(placed.order());
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable Long orderId) {
        return orderService.getMine(orderId);
    }
}
