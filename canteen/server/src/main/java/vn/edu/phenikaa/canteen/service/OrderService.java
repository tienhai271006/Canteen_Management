package vn.edu.phenikaa.canteen.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.edu.phenikaa.canteen.common.config.OrderProperties;
import vn.edu.phenikaa.canteen.common.exception.BusinessException;
import vn.edu.phenikaa.canteen.common.exception.ErrorCode;
import vn.edu.phenikaa.canteen.common.security.CurrentUser;
import vn.edu.phenikaa.canteen.dto.OrderResponse;
import vn.edu.phenikaa.canteen.dto.PlaceOrderRequest;
import vn.edu.phenikaa.canteen.model.Cart;
import vn.edu.phenikaa.canteen.model.CartItem;
import vn.edu.phenikaa.canteen.model.Dish;
import vn.edu.phenikaa.canteen.model.Menu;
import vn.edu.phenikaa.canteen.model.MenuItem;
import vn.edu.phenikaa.canteen.model.Order;
import vn.edu.phenikaa.canteen.model.OrderItem;
import vn.edu.phenikaa.canteen.model.OrderStatus;
import vn.edu.phenikaa.canteen.model.PaymentStatus;
import vn.edu.phenikaa.canteen.model.PickupSlot;
import vn.edu.phenikaa.canteen.repository.CartRepository;
import vn.edu.phenikaa.canteen.repository.OrderRepository;
import vn.edu.phenikaa.canteen.repository.PickupSlotRepository;
import vn.edu.phenikaa.canteen.service.PricingService.LineInput;
import vn.edu.phenikaa.canteen.service.PricingService.PricedLine;
import vn.edu.phenikaa.canteen.service.PricingService.Quote;

/**
 * UC5: đặt đơn từ giỏ. {@link #placeOrder} chạy trong MỘT transaction: kiểm tra quầy và giờ đóng nhận, giữ suất,
 * giữ ưu đãi, tạo đơn, ghi lịch sử, dọn giỏ. Bước nào lỗi thì cả transaction hủy, suất không bị giữ nhầm (BR11).
 * <p>
 * {@link #transition} là cửa duy nhất để đổi trạng thái đơn; Pha 7 (quầy, khách), Pha 9 (job hết hạn) và
 * Pha 10 (không nhận món) đều gọi vào đây thay vì tự set status.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    /** {@code replayed = true} khi khóa idempotency đã được xử lý trước đó và đây là kết quả cũ (BR36). */
    public record PlacedOrder(OrderResponse order, boolean replayed) {
    }

    private final OrderRepository orders;
    private final CartRepository carts;
    private final PickupSlotRepository slots;
    private final OrderabilityChecker orderability;
    private final OrderEligibilityChecker eligibility;
    private final PricingService pricing;
    private final QuotaService quotaService;
    private final OrderStateMachine stateMachine;
    private final OrderStatusHistoryService history;
    private final OrderCodeGenerator codes;
    private final ObjectProvider<FirstOrderDiscountProvider> discountProvider;
    private final OrderProperties props;
    private final Clock clock;

    /** Khóa do client sinh (UUID) và gửi ở header Idempotency-Key; thiếu hoặc sai dạng thì trả 400. */
    public static String requireIdempotencyKey(String key) {
        if (key == null || !IDEMPOTENCY_KEY.matcher(key.trim()).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Thiếu hoặc sai header Idempotency-Key (8 đến 64 ký tự chữ, số, '-' hoặc '_').");
        }
        return key.trim();
    }

    @Transactional
    public PlacedOrder placeOrder(PlaceOrderRequest req, String idempotencyKey) {
        Long userId = CurrentUser.id();
        String key = requireIdempotencyKey(idempotencyKey);

        // BR36: bấm hai lần hoặc client gửi lại thì trả đúng đơn cũ, không tạo đơn thứ hai và không giữ thêm suất.
        Optional<Order> existing = orders.findByCustomerIdAndIdempotencyKey(userId, key);
        if (existing.isPresent()) {
            return new PlacedOrder(detail(existing.get().getId()), true);
        }

        eligibility.requireCanOrder(userId);

        Cart cart = carts.findDetailByUserId(userId)
                .filter(c -> !c.getItems().isEmpty())
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_EMPTY));

        PickupSlot slot = slots.findDetailById(req.pickupSlotId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PICKUP_SLOT_INVALID));
        Menu menu = slot.getMenu();
        orderability.require(menu);

        List<LineInput> inputs = new java.util.ArrayList<>();
        Map<Long, Integer> demand = new TreeMap<>();
        for (CartItem line : cart.getItems()) {
            MenuItem mi = line.getMenuItem();
            if (!mi.getMenu().getId().equals(menu.getId())) {
                throw new BusinessException(ErrorCode.PICKUP_SLOT_INVALID,
                        "Khung giờ nhận không thuộc thực đơn của các món trong giỏ.");
            }
            orderability.require(mi);
            Dish dish = mi.getDish();
            inputs.add(new LineInput(mi.getId(), dish.getId(), dish.getName(), dish.getPrice(), line.getQuantity()));
            demand.merge(mi.getId(), line.getQuantity(), Integer::sum);
        }

        Quote quote = pricing.quote(userId, inputs);

        // Giữ suất (BR12): lỗi ở đây hoặc ở bước sau đều hủy cả transaction.
        quotaService.reserveAll(demand);

        LocalDateTime now = LocalDateTime.now(clock);
        Order order = new Order();
        order.setOrderCode(codes.next());
        order.setCustomerId(userId);
        order.setVendor(menu.getVendor());
        order.setPickupSlot(slot);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setSubtotalAmount(quote.subtotal());
        order.setDiscountAmount(quote.discount());
        order.setTotalAmount(quote.total());
        order.setEntitlementId(quote.entitlementId());
        order.setIdempotencyKey(key);
        order.setNote(blankToNull(req.note()));
        order.setPendingExpiresAt(now.plus(props.pendingTtl()));
        for (PricedLine l : quote.lines()) {
            OrderItem oi = new OrderItem();
            oi.setMenuItemId(l.menuItemId());
            oi.setDishId(l.dishId());
            oi.setDishName(l.dishName());
            oi.setUnitPrice(l.unitPrice());
            oi.setQuantity(l.quantity());
            oi.setLineTotal(l.lineTotal());
            order.addItem(oi);
        }
        // Flush ngay để vi phạm UNIQUE (customer_id, idempotency_key) lộ ra tại đây, trước khi giữ ưu đãi.
        orders.saveAndFlush(order);

        if (quote.entitlementId() != null) {
            FirstOrderDiscountProvider provider = discountProvider.getIfAvailable();
            if (provider == null) {
                throw new IllegalStateException("Có khoản giảm nhưng không có FirstOrderDiscountProvider.");
            }
            provider.hold(quote.entitlementId(), order.getId());
        }

        history.record(order, null, OrderStatus.PENDING, Actor.customer(userId), null);

        cart.empty();
        carts.save(cart);

        return new PlacedOrder(OrderResponse.from(order), false);
    }

    /**
     * Dùng khi hai request cùng khóa chạy song song và một bên thua ở ràng buộc UNIQUE:
     * bên thua đọc lại đơn mà bên thắng đã tạo. Gọi SAU khi transaction thua đã hủy.
     */
    @Transactional(readOnly = true)
    public Optional<PlacedOrder> findReplay(String idempotencyKey) {
        return orders.findByCustomerIdAndIdempotencyKey(CurrentUser.id(), idempotencyKey.trim())
                .map(o -> new PlacedOrder(detail(o.getId()), true));
    }

    /** Khách chỉ xem được đơn của mình; đơn của người khác trả 404 để không lộ đơn có tồn tại hay không (BR03). */
    @Transactional(readOnly = true)
    public OrderResponse getMine(Long orderId) {
        Order order = orders.findDetailById(orderId)
                .filter(o -> o.getCustomerId().equals(CurrentUser.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return OrderResponse.from(order);
    }

    /**
     * Cửa duy nhất để đổi trạng thái đơn: kiểm tra bảng chuyển, cập nhật mốc thời gian, ghi lịch sử (NFR03)
     * và trả suất khi hủy hoặc hết hạn (BR14). Người gọi phải tự kiểm tra QUYỀN (vai trò, quầy) trước.
     * Trả quyền ưu đãi khi hủy hợp lệ do Pha 8 nối vào.
     */
    @Transactional
    public void transition(Order order, OrderStatus to, Actor actor, String reason) {
        OrderStatus from = order.getStatus();
        stateMachine.require(from, to);

        LocalDateTime now = LocalDateTime.now(clock);
        order.setStatus(to);
        if (from == OrderStatus.PENDING) {
            order.setPendingExpiresAt(null);
        }
        switch (to) {
            case CONFIRMED -> order.setConfirmedAt(now);
            case READY -> order.setReadyAt(now);
            case COMPLETED -> order.setCompletedAt(now);
            case CANCELLED, EXPIRED, NOT_COLLECTED -> order.setClosedReason(blankToNull(reason));
            default -> {
            }
        }
        if (stateMachine.releasesQuota(to)) {
            quotaService.releaseForOrder(order);
        }
        history.record(order, from, to, actor, reason);
    }

    private OrderResponse detail(Long orderId) {
        return OrderResponse.from(orders.findDetailById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND)));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
